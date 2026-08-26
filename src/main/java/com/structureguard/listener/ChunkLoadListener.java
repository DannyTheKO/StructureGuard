package com.structureguard.listener;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.config.ProtectionRule;
import com.structureguard.database.model.StructureInfo;
import com.structureguard.structure.model.StructureResult;
import com.structureguard.util.ChunkUtil;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ChunkLoadListener implements Listener {
    private final StructureGuardPlugin plugin;
    private final Map<String, Set<Long>> scannedChunksCache = new ConcurrentHashMap<>();
    private volatile boolean cacheLoaded = false;
    private static final int MAX_CONCURRENT_TASKS = 10;
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);
    private final ConcurrentLinkedQueue<ChunkTask> pendingChunks = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ChunkWriteTask> pendingDbWrites = new ConcurrentLinkedQueue<>();
    private static final int DB_BATCH_SIZE = 50;
    private final AtomicLong processedChunkCount = new AtomicLong(0);
    private final AtomicLong protectedStructureCount = new AtomicLong(0);

    private static class ChunkTask {
        final World world; final int chunkX, chunkZ; final String worldName; final long chunkKey;
        ChunkTask(World world, int chunkX, int chunkZ, String worldName, long chunkKey) { this.world = world; this.chunkX = chunkX; this.chunkZ = chunkZ; this.worldName = worldName; this.chunkKey = chunkKey; }
    }
    private static class ChunkWriteTask { final String worldName; final int chunkX, chunkZ; ChunkWriteTask(String worldName, int chunkX, int chunkZ) { this.worldName = worldName; this.chunkX = chunkX; this.chunkZ = chunkZ; } }

    public ChunkLoadListener(StructureGuardPlugin plugin) { this.plugin = plugin; loadCacheSync(); }

    private void loadCacheSync() {
        for (World world : plugin.getServer().getWorlds()) {
            String worldName = world.getName();
            Set<Long> chunks = plugin.getDatabase().getScannedChunks(worldName);
            Set<Long> cacheSet = ConcurrentHashMap.newKeySet();
            cacheSet.addAll(chunks);
            scannedChunksCache.put(worldName, cacheSet);
            plugin.getLogger().info("Loaded " + chunks.size() + " scanned chunks for " + worldName);
            plugin.getStructureFinder().initForChunkListener(world);
        }
        cacheLoaded = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk() && !plugin.getConfigManager().shouldProcessExistingChunks()) return;
        if (!plugin.getConfigManager().hasEnabledProtectionRules()) return;
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();
        String worldName = world.getName();
        if (plugin.getConfigManager().isWorldDisabled(worldName)) return;
        long chunkKey = ChunkUtil.pack(chunk.getX(), chunk.getZ());
        if (isChunkScannedCached(worldName, chunkKey)) return;
        ChunkTask task = new ChunkTask(world, chunk.getX(), chunk.getZ(), worldName, chunkKey);
        if (activeTaskCount.get() < MAX_CONCURRENT_TASKS) startAsyncTask(task);
        else pendingChunks.offer(task);
    }

    private boolean isChunkScannedCached(String worldName, long chunkKey) {
        Set<Long> worldCache = scannedChunksCache.get(worldName);
        if (worldCache == null) { worldCache = ConcurrentHashMap.newKeySet(); scannedChunksCache.put(worldName, worldCache); return false; }
        return worldCache.contains(chunkKey);
    }

    private void markChunkScannedCached(String worldName, int chunkX, int chunkZ, long chunkKey) {
        Set<Long> worldCache = scannedChunksCache.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet());
        worldCache.add(chunkKey);
        pendingDbWrites.offer(new ChunkWriteTask(worldName, chunkX, chunkZ));
        if (pendingDbWrites.size() >= DB_BATCH_SIZE) flushDbWrites();
    }

    private void flushDbWrites() {
        Map<String, List<int[]>> byWorld = new HashMap<>();
        ChunkWriteTask task; int count = 0;
        while ((task = pendingDbWrites.poll()) != null && count < DB_BATCH_SIZE * 2) { byWorld.computeIfAbsent(task.worldName, k -> new ArrayList<>()).add(new int[]{task.chunkX, task.chunkZ}); count++; }
        if (!byWorld.isEmpty()) plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> { for (Map.Entry<String, List<int[]>> entry : byWorld.entrySet()) plugin.getDatabase().markChunksScanned(entry.getKey(), entry.getValue()); });
    }

    private void startAsyncTask(ChunkTask task) {
        activeTaskCount.incrementAndGet();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try { processChunkStructures(task.world, task.chunkX, task.chunkZ); processedChunkCount.incrementAndGet(); markChunkScannedCached(task.worldName, task.chunkX, task.chunkZ, task.chunkKey); }
            catch (Exception e) { plugin.getConfigManager().debug("Error processing chunk " + task.chunkX + "," + task.chunkZ + ": " + e.getMessage()); }
            finally { activeTaskCount.decrementAndGet(); processNextQueued(); }
        });
    }

    private void processNextQueued() { if (activeTaskCount.get() < MAX_CONCURRENT_TASKS) { ChunkTask next = pendingChunks.poll(); if (next != null) startAsyncTask(next); } }

    private void processChunkStructures(World world, int chunkX, int chunkZ) {
        try {
            List<StructureResult> structures = plugin.getStructureFinder().getStructuresInChunk(world, chunkX, chunkZ);
            plugin.getConfigManager().debug("processChunkStructures: chunk " + chunkX + "," + chunkZ + " found " + structures.size() + " structures");
            if (structures.isEmpty()) return;
            for (StructureResult structure : structures) {
                plugin.getConfigManager().debug("  Checking structure: " + structure.structureType);
                ProtectionRule rule = plugin.getConfigManager().getProtectionRule(structure.structureType);
                if (rule == null || !rule.enabled) { plugin.getConfigManager().debug("    No matching rule or not enabled"); continue; }
                StructureInfo existing = plugin.getDatabase().getStructureByChunk(world.getName(), structure.structureType, structure.chunkX, structure.chunkZ);
                if (existing != null) {
                    boolean grown = structure.minX < existing.minX || structure.maxX > existing.maxX || structure.minZ < existing.minZ || structure.maxZ > existing.maxZ || structure.minY < existing.minY || structure.maxY > existing.maxY;
                    if (grown) {
                        int newMinX = Math.min(existing.minX, structure.minX);
                        int newMaxX = Math.max(existing.maxX, structure.maxX);
                        int newMinZ = Math.min(existing.minZ, structure.minZ);
                        int newMaxZ = Math.max(existing.maxZ, structure.maxZ);
                        int newMinY = Math.min(existing.minY, structure.minY);
                        int newMaxY = Math.max(existing.maxY, structure.maxY);
                        plugin.getDatabase().updateBounds(world.getName(), structure.structureType, structure.chunkX, structure.chunkZ, newMinX, newMinZ, newMaxX, newMaxZ, newMinY, newMaxY);
                        StructureInfo updated = new StructureInfo(world.getName(), structure.structureType, newMinX, newMinZ, newMaxX, newMaxZ, newMinY, newMaxY, structure.chunkX, structure.chunkZ, existing.hasRegion, existing.regionId);
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            boolean expanded = plugin.getRegionManager().updateRegionBounds(updated, rule.padding);
                            if (expanded) plugin.getLogger().info("Expanded " + structure.structureType + " [" + existing.minX + "," + existing.minZ + " -> " + existing.maxX + "," + existing.maxZ + "] => [" + newMinX + "," + newMinZ + " -> " + newMaxX + "," + newMaxZ + "]");
                        });
                    } else {
                        plugin.getConfigManager().debug("    Already in DB with same or larger BB");
                    }
                    continue;
                }
                final StructureResult finalStructure = structure; final ProtectionRule finalRule = rule;
                plugin.getServer().getScheduler().runTask(plugin, () -> createProtection(world, finalStructure, finalRule));
            }
        } catch (Exception e) { plugin.getConfigManager().debug("Error in processChunkStructures: " + e.getMessage()); }
    }

    private void createProtection(World world, StructureResult structure, ProtectionRule rule) {
        try {
            plugin.getDatabase().addStructure(world.getName(), structure.structureType, structure.minX, structure.minZ, structure.maxX, structure.maxZ, structure.minY, structure.maxY, structure.chunkX, structure.chunkZ);
            StructureInfo dbInfo = new StructureInfo(world.getName(), structure.structureType, structure.minX, structure.minZ, structure.maxX, structure.maxZ, structure.minY, structure.maxY, structure.chunkX, structure.chunkZ, false, null);
            String regionId = plugin.getRegionManager().createRegionWithFlags(dbInfo, rule.padding, rule.flags);
            if (regionId != null) { protectedStructureCount.incrementAndGet(); plugin.getLogger().info("Auto-protected " + structure.structureType + " [" + structure.minX + "," + structure.minZ + " -> " + structure.maxX + "," + structure.maxZ + "] Y" + structure.minY + "->" + structure.maxY + " -> " + regionId); }
        } catch (Exception e) { plugin.getLogger().warning("Failed to create protection for " + structure.structureType + ": " + e.getMessage()); e.printStackTrace(); }
    }

    public long getProcessedChunkCount() { return processedChunkCount.get(); }
    public long getProtectedStructureCount() { return protectedStructureCount.get(); }
    public long getPendingCount() { return pendingChunks.size(); }
    public int getActiveTaskCount() { return activeTaskCount.get(); }
    public int getCachedChunkCount(String worldName) { Set<Long> cache = scannedChunksCache.get(worldName); return cache != null ? cache.size() : 0; }
    public void clearCache() { flushDbWrites(); scannedChunksCache.clear(); loadCacheSync(); plugin.getConfigManager().debug("Cleared and reloaded all chunk caches"); }
    public void clearWorldCache(String worldName) { Set<Long> cache = scannedChunksCache.get(worldName); if (cache != null) cache.clear(); flushDbWrites(); plugin.getConfigManager().debug("Cleared chunk cache for world reset: " + worldName); }
    public void shutdown() { Map<String, List<int[]>> byWorld = new HashMap<>(); ChunkWriteTask task; while ((task = pendingDbWrites.poll()) != null) byWorld.computeIfAbsent(task.worldName, k -> new ArrayList<>()).add(new int[]{task.chunkX, task.chunkZ}); for (Map.Entry<String, List<int[]>> entry : byWorld.entrySet()) plugin.getDatabase().markChunksScanned(entry.getKey(), entry.getValue()); }
    public void resetStats() { processedChunkCount.set(0); protectedStructureCount.set(0); }
}
