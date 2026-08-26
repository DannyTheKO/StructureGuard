package com.structureguard.structure.model;

import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ScanState {
    public final World world; public final List<int[]> chunks; public final CommandSender sender;
    public final CompletableFuture<List<StructureResult>> future;
    public final List<StructureResult> results = new ArrayList<>();
    public final Set<String> foundKeys = new HashSet<>();
    public final Map<String,Integer> typeCounts = new HashMap<>();
    public final int totalChunks; public final long startTime; public final int maxRadius;
    public int currentIndex = 0;
    public ScanState(World world, List<int[]> chunks, CommandSender sender, CompletableFuture<List<StructureResult>> future, int totalChunks, long startTime, int maxRadius) {
        this.world = world; this.chunks = chunks; this.sender = sender; this.future = future; this.totalChunks = totalChunks; this.startTime = startTime; this.maxRadius = maxRadius;
    }
}
