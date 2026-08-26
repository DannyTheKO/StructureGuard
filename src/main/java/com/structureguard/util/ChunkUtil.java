package com.structureguard.util;

import java.util.ArrayList;
import java.util.List;

public final class ChunkUtil {
    private ChunkUtil() {}

    public static long pack(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z) << 32);
    }

    public static List<int[]> buildChunkList(int radiusBlocks) {
        List<int[]> out = new ArrayList<>();
        int radiusChunks = (radiusBlocks / 16) + 1;
        for (int cx = -radiusChunks; cx <= radiusChunks; cx++)
            for (int cz = -radiusChunks; cz <= radiusChunks; cz++) {
                int bx = cx * 16 + 8;
                int bz = cz * 16 + 8;
                if (Math.sqrt((double) bx * bx + (double) bz * bz) <= radiusBlocks + 16) out.add(new int[]{cx, cz});
            }
        return out;
    }

    public static String formatTime(long ms) {
        long s = ms / 1000;
        if (s < 60) return s + "s";
        return (s / 60) + "m " + (s % 60) + "s";
    }
}
