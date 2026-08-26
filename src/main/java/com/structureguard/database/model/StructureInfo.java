package com.structureguard.database.model;

public class StructureInfo {
    public final String world;
    public final String type;
    public final int minX;
    public final int minZ;
    public final int maxX;
    public final int maxZ;
    public final int minY;
    public final int maxY;
    public final int chunkX;
    public final int chunkZ;
    public final boolean hasRegion;
    public final String regionId;

    public StructureInfo(String world, String type, int minX, int minZ, int maxX, int maxZ, int minY, int maxY, int chunkX, int chunkZ, boolean hasRegion, String regionId) {
        this.world = world; this.type = type;
        this.minX = minX; this.minZ = minZ; this.maxX = maxX; this.maxZ = maxZ;
        this.minY = minY; this.maxY = maxY;
        this.chunkX = chunkX; this.chunkZ = chunkZ;
        this.hasRegion = hasRegion; this.regionId = regionId;
    }

    public int getCenterX() { return (minX + maxX) / 2; }
    public int getCenterZ() { return (minZ + maxZ) / 2; }
    public int getCenterY() { return (minY + maxY) / 2; }

    public boolean isLargerThan(StructureInfo other) {
        return minX < other.minX || maxX > other.maxX || minZ < other.minZ || maxZ > other.maxZ || minY < other.minY || maxY > other.maxY;
    }

    public String generateRegionId() {
        String safeName = type.replace(":", "_").replace("/", "_");
        return "sg_" + safeName + "_" + chunkX + "_" + chunkZ;
    }
}
