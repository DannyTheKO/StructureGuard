package com.structureguard.structure.model;

public class StructureResult {
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
