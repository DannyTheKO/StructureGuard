package com.structureguard.structure;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.database.model.StructureInfo;
import com.structureguard.structure.model.ScanState;
import com.structureguard.structure.model.StructureResult;
import com.structureguard.structure.nms.NmsReflectionCache;
import com.structureguard.util.ChunkUtil;
import com.structureguard.util.PatternMatcher;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class StructureFinder {
    private final StructureGuardPlugin plugin;
    private final NmsReflectionCache nms;
    private volatile boolean scanInProgress = false;

    public StructureFinder(StructureGuardPlugin plugin) {
        this.plugin = plugin;
        this.nms = new NmsReflectionCache(plugin);
    }

    public boolean isSearchInProgress() { return scanInProgress; }
    public void cancelSearch() { scanInProgress = false; }
    public boolean isUsingChunkBasedDetection() { return false; }
    public String getDetectionPathInfo() {
        return "1.21 Chunk.getAllStarts + BoundingBox | Registry: " + (nms.getCachedStructureRegistry() != null ? "OK" : "null");
    }

    public List<String> dumpChunkMethods(World world) {
        List<String> out = new ArrayList<>();
        try {
            if (!nms.isInitialized()) nms.init(world);
            Object chunk = nms.getGetChunkMethod().invoke(nms.getCachedServerLevel(), 0, 0);
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
            if (!nms.isInitialized() || nms.getCachedWorld() != world) nms.init(world);
            Object chunk = nms.getGetChunkMethod().invoke(nms.getCachedServerLevel(), chunkX, chunkZ);
            if (chunk == null) { out.add("chunk null"); return out; }
            Map<?,?> map = (Map<?,?>) nms.getChunkGetAllStartsMethod().invoke(chunk);
            out.add("Found " + map.size() + " starts");
            for (Map.Entry<?,?> e : map.entrySet()) {
                String name = nms.getStructureName(e.getKey());
                int[] bb = nms.extractBB(e.getValue());
                out.add("  " + name + " BB=" + java.util.Arrays.toString(bb) + " chunkPos=" + getChunkPos(e.getValue()));
            }
        } catch (Exception e) { out.add("Error: " + e.getMessage()); e.printStackTrace(); }
        return out;
    }

    private String getChunkPos(Object start) {
        try {
            Object cp = nms.getStructureStartGetChunkPosMethod().invoke(start);
            if (cp == null) return "null";
            int x = nms.getChunkPosX(cp);
            int z = nms.getChunkPosZ(cp);
            return x + "," + z;
        } catch (Exception e) { return "?"; }
    }

    public List<String> getAllStructureTypes() {
        List<String> out = new ArrayList<>();
        try {
            World world = Bukkit.getWorlds().get(0);
            if (!nms.isInitialized()) nms.init(world);
            Method keySetMethod = nms.getCachedStructureRegistry().getClass().getMethod("keySet");
            Set<?> keys = (Set<?>) keySetMethod.invoke(nms.getCachedStructureRegistry());
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
        List<int[]> allChunks = ChunkUtil.buildChunkList(radiusBlocks);
        Set<Long> alreadyScanned = plugin.getDatabase().getScannedChunks(world.getName());
        List<int[]> toScan = new ArrayList<>();
        int skipped = 0;
        for (int[] c : allChunks) {
            long packed = ChunkUtil.pack(c[0], c[1]);
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

    private void processScanQueue(ScanState state) {
        if (!nms.init(state.world)) {
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
                        int fpct = pct; int found = state.results.size(); String tr = ChunkUtil.formatTime(rem);
                        Bukkit.getScheduler().runTask(plugin, () -> { if (state.sender!=null) state.sender.sendMessage("§7Scan: " + fpct + "% (" + found + " found, ~" + tr + " remaining)"); });
                    }
                    try { Thread.sleep(1); } catch (InterruptedException e) { break; }
                }
                boolean cancelled = !scanInProgress;
                if (!cancelled) {
                    List<StructureInfo> batch = new ArrayList<>();
                    for (StructureResult r : state.results) batch.add(new StructureInfo(state.world.getName(), r.structureType, r.minX, r.minZ, r.maxX, r.maxZ, r.minY, r.maxY, r.chunkX, r.chunkZ, false, null));
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
            Object chunk = nms.getGetChunkMethod().invoke(nms.getCachedServerLevel(), chunkX, chunkZ);
            if (chunk == null) return out;
            Map<?,?> starts = (Map<?,?>) nms.getChunkGetAllStartsMethod().invoke(chunk);
            if (starts == null || starts.isEmpty()) return out;
            for (Map.Entry<?,?> e : starts.entrySet()) {
                Object structure = e.getKey();
                Object start = e.getValue();
                if (start == null) continue;
                String name = nms.getStructureName(structure);
                if (name == null) continue;
                Object cp = nms.getStructureStartGetChunkPosMethod().invoke(start);
                int originX = nms.getChunkPosX(cp);
                int originZ = nms.getChunkPosZ(cp);
                if (originX != chunkX || originZ != chunkZ) continue;
                int[] bb = nms.extractBB(start);
                if (bb == null) continue;
                out.add(new StructureResult(name, bb[0], bb[1], bb[2], bb[3], bb[4], bb[5], originX, originZ));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private void finishScan(ScanState state, boolean cancelled) {
        scanInProgress = false;
        long elapsed = System.currentTimeMillis() - state.startTime;
        String t = ChunkUtil.formatTime(elapsed);
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

    public boolean initForChunkListener(World world) {
        if (nms.isInitialized() && nms.getCachedWorld() == world) return true;
        if (Bukkit.isPrimaryThread()) return nms.init(world);
        CompletableFuture<Boolean> f = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> f.complete(nms.init(world)));
        try { return f.get(5, TimeUnit.SECONDS); } catch (Exception e) { return false; }
    }
    public boolean isReady() { return nms.isInitialized(); }

    public List<StructureResult> getStructuresInChunk(World world, int chunkX, int chunkZ) {
        if (!nms.isInitialized() || nms.getCachedWorld() != world) if (!nms.init(world)) return Collections.emptyList();
        return getStructuresInChunkAsync(chunkX, chunkZ);
    }

    public List<StructureResult> getStructuresSpanningChunk(World world, int chunkX, int chunkZ) {
        if (!nms.isInitialized() || nms.getCachedWorld() != world) if (!nms.init(world)) return Collections.emptyList();
        List<StructureResult> out = new ArrayList<>();
        try {
            Object chunk = nms.getGetChunkMethod().invoke(nms.getCachedServerLevel(), chunkX, chunkZ);
            if (chunk == null) return out;
            Map<?,?> starts = (Map<?,?>) nms.getChunkGetAllStartsMethod().invoke(chunk);
            for (Map.Entry<?,?> e : starts.entrySet()) {
                Object structure = e.getKey(); Object start = e.getValue();
                if (start==null) continue;
                String name = nms.getStructureName(structure);
                if (name==null) continue;
                int[] bb = nms.extractBB(start);
                if (bb==null) continue;
                Object cp = nms.getStructureStartGetChunkPosMethod().invoke(start);
                int ox = nms.getChunkPosX(cp); int oz = nms.getChunkPosZ(cp);
                out.add(new StructureResult(name, bb[0], bb[1], bb[2], bb[3], bb[4], bb[5], ox, oz));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public CompletableFuture<List<StructureResult>> getStructuresInChunkFuture(World world, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> getStructuresInChunk(world, chunkX, chunkZ));
    }

    public boolean hasStructureInChunk(World world, int chunkX, int chunkZ, String pattern) {
        for (StructureResult r : getStructuresInChunk(world, chunkX, chunkZ)) if (PatternMatcher.matches(r.structureType, pattern)) return true;
        return false;
    }

    public boolean matchesStructurePattern(String type, String pattern) { return PatternMatcher.matches(type, pattern); }
}
