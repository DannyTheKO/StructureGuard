package com.structureguard.structure.nms;

import com.structureguard.StructureGuardPlugin;
import org.bukkit.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class NmsReflectionCache {
    private final StructureGuardPlugin plugin;

    private boolean initialized = false;
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

    public NmsReflectionCache(StructureGuardPlugin plugin) { this.plugin = plugin; }

    public boolean isInitialized() { return initialized; }
    public World getCachedWorld() { return cachedWorld; }
    public Object getCachedStructureRegistry() { return cachedStructureRegistry; }
    public Method getChunkGetAllStartsMethod() { return chunkGetAllStartsMethod; }
    public Method getStructureStartGetBoundingBoxMethod() { return structureStartGetBoundingBoxMethod; }
    public Method getStructureStartGetChunkPosMethod() { return structureStartGetChunkPosMethod; }
    public Method getGetChunkMethod() { return getChunkMethod; }
    public Method getGetKeyMethod() { return getKeyMethod; }
    public Object getCachedServerLevel() { return cachedServerLevel; }
    public Method getBbMinXMethod() { return bbMinXMethod; }
    public Method getBbMinYMethod() { return bbMinYMethod; }
    public Method getBbMinZMethod() { return bbMinZMethod; }
    public Method getBbMaxXMethod() { return bbMaxXMethod; }
    public Method getBbMaxYMethod() { return bbMaxYMethod; }
    public Method getBbMaxZMethod() { return bbMaxZMethod; }
    public Method getChunkPosGetXMethod() { return chunkPosGetXMethod; }
    public Method getChunkPosGetZMethod() { return chunkPosGetZMethod; }
    public Class<?> getBoundingBoxClass() { return boundingBoxClass; }
    public Class<?> getChunkPosClass() { return chunkPosClass; }

    public boolean init(World world) {
        if (initialized && cachedWorld == world) return true;
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
            if (chunkGetAllStartsMethod == null) throw new NoSuchMethodException("Chunk.getAllStarts not found");
            Class<?> structureStartClass = null;
            Map<?,?> sample = (Map<?,?>) chunkGetAllStartsMethod.invoke(testChunk);
            if (!sample.isEmpty()) {
                Object val = sample.values().iterator().next();
                structureStartClass = val.getClass();
            } else {
                for (Method m : testChunk.getClass().getMethods()) {
                    if (m.getReturnType().getName().contains("StructureStart")) { structureStartClass = m.getReturnType(); break; }
                }
                if (structureStartClass == null) structureStartClass = Class.forName("net.minecraft.world.level.levelgen.structure.StructureStart");
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
            cachedWorld = world;
            initialized = true;
            plugin.getConfigManager().debug("Reflection cache initialized [1.21 BB mode]");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize reflection cache (1.21): " + e.getMessage());
            e.printStackTrace();
            initialized = false;
            return false;
        }
    }

    private Method findMethodNoArgs(Class<?> clazz, String... names) {
        for (String n : names) { try { return clazz.getMethod(n); } catch (NoSuchMethodException ignored) {} }
        for (Method m : clazz.getMethods()) { if (m.getParameterCount() == 0) for (String n : names) if (m.getName().equals(n)) return m; }
        return null;
    }

    private Method findMethodByParams(Class<?> clazz, String name, Class<?>... params) {
        try { return clazz.getMethod(name, params); } catch (NoSuchMethodException e) {
            for (Method m : clazz.getMethods()) if (m.getName().equals(name) && Arrays.equals(m.getParameterTypes(), params)) return m;
            return null;
        }
    }

    public int[] extractBB(Object start) {
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

    public int getChunkPosX(Object cp) throws Exception {
        if (chunkPosGetXMethod != null) return (int) chunkPosGetXMethod.invoke(cp);
        return cp.getClass().getField("x").getInt(cp);
    }

    public int getChunkPosZ(Object cp) throws Exception {
        if (chunkPosGetZMethod != null) return (int) chunkPosGetZMethod.invoke(cp);
        return cp.getClass().getField("z").getInt(cp);
    }

    public String getStructureName(Object structure) {
        try {
            Object rl = getKeyMethod.invoke(cachedStructureRegistry, structure);
            if (rl != null) return rl.toString();
        } catch (Exception ignored) {}
        return structure.toString();
    }
}
