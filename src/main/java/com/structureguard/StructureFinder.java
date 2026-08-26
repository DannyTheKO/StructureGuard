package com.structureguard;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class StructureFinder {
    
    private final StructureGuardPlugin plugin;
    private volatile boolean scanInProgress = false;
    
    private boolean reflectionCacheInitialized = false;
    private Method getHandleMethod;
    private Method registryAccessMethod;
    private Method lookupOrThrowMethod;
    private Method getKeyMethod;
    private Constructor<?> blockPosConstructor;
    private Class<?> blockPosClass;
    private Object structureRegistryKey;
    
    private Method getChunkMethod;
    private Method chunkGetAllStartsMethod;
    private Method structureStartGetBoundingBoxMethod;
    private Method structureStartGetChunkPosMethod;
    
    private Method bbMinXMethod, bbMinYMethod, bbMinZMethod, bbMaxXMethod, bbMaxYMethod, bbMaxZMethod;
    private Method chunkPosGetXMethod, chunkPosGetZMethod;
    private Class<?> boundingBoxClass;
    private Class<?> chunkPosClass;
    
    private Object cachedServerLevel;
    private Object cachedStructureRegistry;
    private World cachedWorld;
    
    public StructureFinder(StructureGuardPlugin plugin) {
        this.plugin = plugin;
    }
    
    private boolean initReflectionCache(World world) {
        if (reflectionCacheInitialized && cachedWorld == world) {
            return true;
        }
        try {
            Object craftWorld = world;
            getHandleMethod = craftWorld.getClass().getMethod("getHandle");
            cachedServerLevel = getHandleMethod.invoke(craftWorld);

            blockPosClass = Class.forName("net.minecraft.core.BlockPos");
            blockPosConstructor = blockPosClass.getConstructor(int.class, int.class, int.class);

            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.Registries");
            structureRegistryKey = registriesClass.getField("STRUCTURE").get(null);

            Class<?> serverLevelClass = cachedServerLevel.getClass();
            registryAccessMethod = serverLevelClass.getMethod("registryAccess");
            Object registryAccess = registryAccessMethod.invoke(cachedServerLevel);

            Class<?> resourceKeyClass = Class.forName("net.minecraft.resources.ResourceKey");
            try {
                lookupOrThrowMethod = registryAccess.getClass().getMethod("lookupOrThrow", resourceKeyClass);
            } catch (NoSuchMethodException e) {
                lookupOrThrowMethod = registryAccess.getClass().getMethod("registryOrThrow", resourceKeyClass);
            }
            cachedStructureRegistry = lookupOrThrowMethod.invoke(registryAccess, structureRegistryKey);
            getKeyMethod = cachedStructureRegistry.getClass().getMethod("getKey", Object.class);

            getChunkMethod = findMethodByParams(serverLevelClass, "getChunk", int.class, int.class);

            Object testChunk = getChunkMethod.invoke(cachedServerLevel, 0, 0);
            if (testChunk == null) throw new IllegalStateException("getChunk(0,0) returned null");

            chunkGetAllStartsMethod = findMethodNoArgs(testChunk.getClass(), "getAllStarts", "getStructureStarts");
            if (chunkGetAllStartsMethod == null) {
                throw new NoSuchMethodException("Chunk.getAllStarts not found");
            }

            Class<?> structureStartClass = null;
            Map<?,?> sample = (Map<?,?>) chunkGetAllStartsMethod.invoke(testChunk);
            if (!sample.isEmpty()) {
                Object val = sample.values().iterator().next();
                structureStartClass = val.getClass();
            } else {
                for (Method m : testChunk.getClass().getMethods()) {
                    if (m.getReturnType().getName().contains("StructureStart")) {
                        structureStartClass = m.getReturnType();
                        break;
                    }
                }
                if (structureStartClass == null) {
                    structureStartClass = Class.forName("net.minecraft.world.level.levelgen.structure.StructureStart");
                }
            }

            structureStartGetBoundingBoxMethod = findMethodNoArgs(structureStartClass, "getBoundingBox");
            structureStartGetChunkPosMethod = findMethodNoArgs(structureStartClass, "getChunkPos");

            boundingBoxClass = Class.forName("net.minecraft.world.level.levelgen.structure.BoundingBox");
            chunkPosClass = Class.forName("net.minecraft.world.level.ChunkPos");

            bbMinXMethod = findMethodNoArgs(boundingBoxClass, "minX", "getMinX");
            bbMinYMethod = findMethodNoArgs(boundingBoxClass, "minY", "getMinY");
            bbMinZMethod = findMethodNoArgs(boundingBoxClass, "minZ", "getMinZ");
            bbMaxXMethod = findMethodNoArgs(boundingBoxClass, "maxX", "getMaxX");
            bbMaxYMethod = findMethodNoArgs(boundingBoxClass, "maxY", "getMaxY");
            bbMaxZMethod = findMethodNoArgs(boundingBoxClass, "maxZ", "getMaxZ");

            chunkPosGetXMethod = findMethodNoArgs(chunkPosClass, "getX", "x");
            chunkPosGetZMethod = findMethodNoArgs(chunkPosClass, "getZ", "z");
            if (chunkPosGetXMethod == null) {
                chunkPosGetXMethod = chunkPosClass.getField("x").getDeclaringClass() != null ? null : null;
            }

            cachedWorld = world;
            reflectionCacheInitialized = true;
            plugin.getConfigManager().debug("Reflection cache initialized [1.21 BB mode]");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize reflection cache (1.21): " + e.getMessage());
            e.printStackTrace();
            reflectionCacheInitialized = false;
            return false;
        }
    }

    private Method findMethodNoArgs(Class<?> clazz, String... names) {
        for (String n : names) {
            try { return clazz.getMethod(n); } catch (NoSuchMethodException ignored) {}
        }
        for (Method m : clazz.getMethods()) {
            if (m.getParameterCount() == 0) {
                for (String n : names) if (m.getName().equals(n)) return m;
            }
        }
        return null;
    }

    private Method findMethodByParams(Class<?> clazz, String name, Class<?>... params) {
        try { return clazz.getMethod(name, params); } catch (NoSuchMethodException e) {
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(name) && Arrays.equals(m.getParameterTypes(), params)) return m;
            }
            return null;
        }
    }

    public boolean isSearchInProgress() { return scanInProgress; }
    public void cancelSearch() { scanInProgress = false; }
    public boolean isUsingChunkBasedDetection() { return false; }
    public String getDetectionPathInfo() {
        return "1.21 Chunk.getAllStarts + BoundingBox | Registry: " + (cachedStructureRegistry != null ? "OK" : "null");
    }

    public List<String> dumpChunkMethods(World world) {
        List<String> out = new ArrayList<>();
        try {
            if (!reflectionCacheInitialized) initReflectionCache(world);
            Object chunk = getChunkMethod.invoke(cachedServerLevel, 0, 0);
            out.add("Chunk: " + chunk.getClass().getName());
            for (Method m : chunk.getClass().getMethods()) {
                if (m.getParameterCount()==0 && Map.class.isAssignableFrom(m.getReturnType())) {
                    out.add("  " + m.getName() + " -> " + m.getGenericReturnType().getTypeName());
                }
            }
        } catch (Exception e) { out.add("Error: " + e.getMessage()); }
        return out;
    }

    public List<String> probeChunkVerbose(World world, int chunkX, int chunkZ) {
        List<String> out = new ArrayList<>();
        out.add("Probing chunk " + chunkX + "," + chunkZ);
        try {
            if (!reflectionCacheInitialized || cachedWorld != world) initReflectionCache(world);
            Object chunk = getChunkMethod.invoke(cachedServerLevel, chunkX, chunkZ);
            if (chunk == null) { out.add("chunk null"); return out; }
            Map<?,?> map = (Map<?,?>) chunkGetAllStartsMethod.invoke(chunk);
            out.add("Found " + map.size() + " starts");
            for (Map.Entry<?,?> e : map.entrySet()) {
                String name = getStructureNameCached(e.getKey());
                int[] bb = extractBB(e.getValue());
                out.add("  " + name + " BB=" + Arrays.toString(bb) + " chunkPos=" + getChunkPos(e.getValue()));
            }
        } catch (Exception e) { out.add("Error: " + e.getMessage()); e.printStackTrace(); }
        return out;
    }

    private String getChunkPos(Object start) {
        try {
            Object cp = structureStartGetChunkPosMethod.invoke(start);
            if (cp == null) return "null";
            int x = getChunkPosX(cp);
            int z = getChunkPosZ(cp);
            return x + "," + z;
        } catch (Exception e) { return "?"; }
    }

    private int getChunkPosX(Object cp) throws Exception {
        if (chunkPosGetXMethod != null) return (int) chunkPosGetXMethod.invoke(cp);
        return cp.getClass().getField("x").getInt(cp);
    }
    private int getChunkPosZ(Object cp) throws Exception {
        if (chunkPosGetZMethod != null) return (int) chunkPosGetZMethod.invoke(cp);
        return cp.getClass().getField("z").getInt(cp);
    }

    private int[] extractBB(Object start) {
        try {
            Object bb = structureStartGetBoundingBoxMethod.invoke(start);
            if (bb == null) return null;
            int minX = invokeBB(bb, bbMinXMethod, "minX");
            int minY = invokeBB(bb, bbMinYMethod, "minY");
            int minZ = invokeBB(bb, bbMinZMethod, "minZ");
            int maxX = invokeBB(bb, bbMaxXMethod, "maxX");
            int maxY = invokeBB(bb, bbMaxYMethod, "maxY");
            int maxZ = invokeBB(bb, bbMaxZMethod, "maxZ");
            if (minX == Integer.MIN_VALUE || maxX == Integer.MIN_VALUE) return null;
            if (minX > maxX || minZ > maxZ) return null;
            return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
        } catch (Exception e) { return null; }
    }

    private int invokeBB(Object bb, Method m, String field) throws Exception {
        if (m != null) return (int) m.invoke(bb);
        return bb.getClass().getField(field).getInt(bb);
    }

    public List<String> getAllStructureTypes() {
        List<String> out = new ArrayList<>();
        try {
            World world = Bukkit.getWorlds().get(0);
            if (!reflectionCacheInitialized) initReflectionCache(world);
            Method keySetMethod = cachedStructureRegistry.getClass().getMethod("keySet");
            Set<?> keys = (Set<?>) keySetMethod.invoke(cachedStructureRegistry);
            for (Object k : keys) out.add(k.toString());
            Collections.sort(out);
        } catch (Exception e) { plugin.getLogger().warning("Failed to get structure types: " + e.getMessage()); }
        return out;
    }

    public CompletableFuture<List<StructureResult>> scan(World world, int radiusBlocks, CommandSender sender) {
        CompletableFuture<List<StructureResult>> future = new CompletableFuture<>();
        if (scanInProgress) {
            if (sender != null) sender.sendMessage("§cA scan is already in progress.");
            future.complete(new ArrayList<>());
            return future;
        }
        scanInProgress = true;
        List<int[]> allChunks = buildChunkList(radiusBlocks);
        Set<Long> alreadyScanned = plugin.getDatabase().getScannedChunks(world.getName());
        List<int[]> toScan = new ArrayList<>();
        int skipped = 0;
        for (int[] c : allChunks) {
            long packed = ((long) c[0] & 0xFFFFFFFFL) | (((long) c[1]) << 32);
            if (!alreadyScanned.contains(packed)) toScan.add(c); else skipped++;
        }
        if (toScan.isEmpty()) {
            scanInProgress = false;
            if (sender != null) sender.sendMessage("§aAll " + allChunks.size() + " chunks already scanned! Use /sg scan reset to clear.");
            future.complete(new ArrayList<>());
            return future;
        }
        int total = toScan.size();
        int perBatch = plugin.getConfigManager().getScanChunksPerTick();
        if (sender != null) {
            sender.sendMessage("§6Starting BB scan...");
            if (skipped>0) sender.sendMessage("§7Skipping " + skipped + " already-scanned chunks");
            sender.sendMessage("§7Scanning " + total + " chunks in " + world.getName() + " (" + perBatch + "/batch)");
        }
        ScanState state = new ScanState(world, toScan, sender, future, total, System.currentTimeMillis(), radiusBlocks);
        processScanQueue(state);
        return future;
    }

    public static class StructureResult {
        public final String structureType;
        public final int minX, minY, minZ, maxX, maxY, maxZ;
        public final int chunkX, chunkZ;
        public StructureResult(String structureType, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int chunkX, int chunkZ) {
            this.structureType = structureType;
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
            this.chunkX = chunkX; this.chunkZ = chunkZ;
        }
        public int getCenterX() { return (minX + maxX)/2; }
        public int getCenterZ() { return (minZ + maxZ)/2; }
        public int getCenterY() { return (minY + maxY)/2; }
        public boolean intersectsRadius(int radius) {
            int cx = getCenterX(); int cz = getCenterZ();
            double dist = Math.sqrt((double)cx*cx + (double)cz*cz);
            if (dist <= radius) return true;
            int clampedX = Math.max(minX, Math.min(0, maxX));
            int clampedZ = Math.max(minZ, Math.min(0, maxZ));
            double edgeDist = Math.sqrt((double)clampedX*clampedX + (double)clampedZ*clampedZ);
            return edgeDist <= radius;
        }
    }

    private static class ScanState {
        final World world; final List<int[]> chunks; final CommandSender sender;
        final CompletableFuture<List<StructureResult>> future;
        final List<StructureResult> results = new ArrayList<>();
        final Set<String> foundKeys = new HashSet<>();
        final Map<String,Integer> typeCounts = new HashMap<>();
        final int totalChunks; final long startTime; final int maxRadius;
        int currentIndex = 0;
        ScanState(World world, List<int[]> chunks, CommandSender sender, CompletableFuture<List<StructureResult>> future, int totalChunks, long startTime, int maxRadius) {
            this.world = world; this.chunks = chunks; this.sender = sender; this.future = future; this.totalChunks = totalChunks; this.startTime = startTime; this.maxRadius = maxRadius;
        }
    }

    private List<int[]> buildChunkList(int radiusBlocks) {
        List<int[]> out = new ArrayList<>();
        int radiusChunks = (radiusBlocks / 16) + 1;
        for (int cx = -radiusChunks; cx <= radiusChunks; cx++)
            for (int cz = -radiusChunks; cz <= radiusChunks; cz++) {
                int bx = cx*16+8; int bz = cz*16+8;
                if (Math.sqrt((double)bx*bx + (double)bz*bz) <= radiusBlocks+16) out.add(new int[]{cx,cz});
            }
        return out;
    }

    private void processScanQueue(ScanState state) {
        if (!initReflectionCache(state.world)) {
            if (state.sender != null) state.sender.sendMessage("§cFailed to init scanner.");
            scanInProgress = false; state.future.complete(new ArrayList<>()); return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int lastPercent = -1;
                while (state.currentIndex < state.chunks.size() && scanInProgress) {
                    int perBatch = plugin.getConfigManager().getScanChunksPerTick();
                    int batchEnd = Math.min(state.currentIndex + perBatch, state.chunks.size());
                    for (int i = state.currentIndex; i < batchEnd && scanInProgress; i++) {
                        int[] cc = state.chunks.get(i);
                        List<StructureResult> list = getStructuresInChunkAsync(cc[0], cc[1]);
                        for (StructureResult r : list) {
                            if (!r.intersectsRadius(state.maxRadius)) continue;
                            String key = r.structureType + ":" + r.chunkX + ":" + r.chunkZ;
                            if (!state.foundKeys.contains(key)) {
                                state.foundKeys.add(key);
                                state.results.add(r);
                                state.typeCounts.merge(r.structureType,1,Integer::sum);
                            }
                        }
                    }
                    state.currentIndex = batchEnd;
                    int pct = (state.currentIndex*100)/state.totalChunks;
                    if (pct != lastPercent && pct % 10 == 0) {
                        lastPercent = pct;
                        long elapsed = System.currentTimeMillis() - state.startTime;
                        long est = (elapsed * state.totalChunks)/Math.max(1, state.currentIndex);
                        long rem = est - elapsed;
                        int fpct = pct; int found = state.results.size(); String tr = formatTime(rem);
                        Bukkit.getScheduler().runTask(plugin, () -> { if (state.sender!=null) state.sender.sendMessage("§7Scan: " + fpct + "% (" + found + " found, ~" + tr + " remaining)"); });
                    }
                    try { Thread.sleep(1); } catch (InterruptedException e) { break; }
                }
                boolean cancelled = !scanInProgress;
                if (!cancelled) {
                    List<StructureDatabase.StructureInfo> batch = new ArrayList<>();
                    for (StructureResult r : state.results) batch.add(new StructureDatabase.StructureInfo(state.world.getName(), r.structureType, r.minX, r.minZ, r.maxX, r.maxZ, r.minY, r.maxY, r.chunkX, r.chunkZ, false, null));
                    int inserted = plugin.getDatabase().addStructuresBatch(state.world.getName(), batch);
                    plugin.getLogger().info("Scan wrote " + inserted + " structures");
                    plugin.getDatabase().markChunksScanned(state.world.getName(), state.chunks);
                    Bukkit.getScheduler().runTask(plugin, () -> finishScan(state,false));
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> finishScan(state,true));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Async scan error: " + e.getMessage()); e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> { if (state.sender!=null) state.sender.sendMessage("§cScan error: " + e.getMessage()); scanInProgress=false; state.future.complete(state.results); });
            }
        });
    }

    private List<StructureResult> getStructuresInChunkAsync(int chunkX, int chunkZ) {
        List<StructureResult> out = new ArrayList<>();
        try {
            Object chunk = getChunkMethod.invoke(cachedServerLevel, chunkX, chunkZ);
            if (chunk == null) return out;
            Map<?,?> starts = (Map<?,?>) chunkGetAllStartsMethod.invoke(chunk);
            if (starts == null || starts.isEmpty()) return out;
            for (Map.Entry<?,?> e : starts.entrySet()) {
                Object structure = e.getKey();
                Object start = e.getValue();
                if (start == null) continue;
                String name = getStructureNameCached(structure);
                if (name == null) continue;
                Object cp = structureStartGetChunkPosMethod.invoke(start);
                int originX = getChunkPosX(cp);
                int originZ = getChunkPosZ(cp);
                if (originX != chunkX || originZ != chunkZ) continue;
                int[] bb = extractBB(start);
                if (bb == null) continue;
                out.add(new StructureResult(name, bb[0], bb[1], bb[2], bb[3], bb[4], bb[5], originX, originZ));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private String getStructureNameCached(Object structure) {
        try {
            Object rl = getKeyMethod.invoke(cachedStructureRegistry, structure);
            if (rl != null) return rl.toString();
        } catch (Exception ignored) {}
        return structure.toString();
    }

    private void finishScan(ScanState state, boolean cancelled) {
        scanInProgress = false;
        long elapsed = System.currentTimeMillis() - state.startTime;
        String t = formatTime(elapsed);
        if (state.sender != null) {
            if (cancelled) state.sender.sendMessage("§eCancelled. Found " + state.results.size() + " before cancel.");
            else if (state.results.isEmpty()) state.sender.sendMessage("§cScan complete. No structures found.");
            else {
                state.sender.sendMessage("§a✓ Scan complete! Found " + state.results.size() + " in " + t);
                List<Map.Entry<String,Integer>> sorted = new ArrayList<>(state.typeCounts.entrySet());
                sorted.sort((a,b)->b.getValue().compareTo(a.getValue()));
                int shown=0;
                for (Map.Entry<String,Integer> e : sorted) {
                    if (shown>=10) { state.sender.sendMessage("§7  ... and " + (sorted.size()-10) + " more types"); break; }
                    state.sender.sendMessage("§7  - " + e.getKey() + ": §e" + e.getValue()); shown++;
                }
            }
        }
        plugin.getLogger().info("Scan " + (cancelled?"cancelled":"complete") + ": " + state.results.size() + " in " + t);
        state.future.complete(state.results);
    }

    private String formatTime(long ms) {
        long s = ms/1000;
        if (s<60) return s+"s";
        return (s/60)+"m "+(s%60)+"s";
    }

    public boolean initForChunkListener(World world) {
        if (reflectionCacheInitialized && cachedWorld == world) return true;
        if (Bukkit.isPrimaryThread()) return initReflectionCache(world);
        CompletableFuture<Boolean> f = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> f.complete(initReflectionCache(world)));
        try { return f.get(5, TimeUnit.SECONDS); } catch (Exception e) { return false; }
    }
    public boolean isReady() { return reflectionCacheInitialized; }

    public List<StructureResult> getStructuresInChunk(World world, int chunkX, int chunkZ) {
        if (!reflectionCacheInitialized || cachedWorld != world) if (!initReflectionCache(world)) return Collections.emptyList();
        return getStructuresInChunkAsync(chunkX, chunkZ);
    }

    public List<StructureResult> getStructuresSpanningChunk(World world, int chunkX, int chunkZ) {
        if (!reflectionCacheInitialized || cachedWorld != world) if (!initReflectionCache(world)) return Collections.emptyList();
        List<StructureResult> out = new ArrayList<>();
        try {
            Object chunk = getChunkMethod.invoke(cachedServerLevel, chunkX, chunkZ);
            if (chunk == null) return out;
            Map<?,?> starts = (Map<?,?>) chunkGetAllStartsMethod.invoke(chunk);
            for (Map.Entry<?,?> e : starts.entrySet()) {
                Object structure = e.getKey(); Object start = e.getValue();
                if (start==null) continue;
                String name = getStructureNameCached(structure);
                if (name==null) continue;
                int[] bb = extractBB(start);
                if (bb==null) continue;
                Object cp = structureStartGetChunkPosMethod.invoke(start);
                int ox = getChunkPosX(cp); int oz = getChunkPosZ(cp);
                out.add(new StructureResult(name, bb[0], bb[1], bb[2], bb[3], bb[4], bb[5], ox, oz));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public CompletableFuture<List<StructureResult>> getStructuresInChunkFuture(World world, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> getStructuresInChunk(world, chunkX, chunkZ));
    }

    public boolean hasStructureInChunk(World world, int chunkX, int chunkZ, String pattern) {
        for (StructureResult r : getStructuresInChunk(world, chunkX, chunkZ)) if (matchesPattern(r.structureType, pattern)) return true;
        return false;
    }

    public boolean matchesStructurePattern(String type, String pattern) { return matchesPattern(type, pattern); }
    private boolean matchesPattern(String type, String pattern) {
        if (pattern.equals(type)) return true;
        if (pattern.contains("*")) return type.matches(pattern.replace(".","\\.").replace("*",".*"));
        return false;
    }
}
