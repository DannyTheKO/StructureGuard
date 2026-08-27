package com.structureguard.region;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.structureguard.StructureGuardPlugin;
import com.structureguard.config.ProtectionRule;
import com.structureguard.database.model.StructureInfo;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.*;

public class RegionManager {
    private final StructureGuardPlugin plugin;
    private boolean worldGuardAvailable = false;

    public RegionManager(StructureGuardPlugin plugin) {
        this.plugin = plugin;
        checkWorldGuard();
    }

    private void checkWorldGuard() {
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            worldGuardAvailable = true;
            plugin.getLogger().info("WorldGuard integration enabled");
        } catch (ClassNotFoundException e) {
            worldGuardAvailable = false;
        }
    }

    public boolean isWorldGuardAvailable() { return worldGuardAvailable; }
    private int worldMinY(World w) { try { return w.getMinHeight(); } catch (Exception e) { return -64; } }
    private int worldMaxY(World w) { try { return w.getMaxHeight() + w.getMinHeight() - 1; } catch (Exception e) { return 320; } }

    public String createRegion(StructureInfo info, int padding) {
        return createRegionWithFlags(info, padding, plugin.getConfigManager().getDefaultFlags());
    }

    @Deprecated public String createRegion(StructureInfo info, int radius, int yMin, int yMax) { return createRegion(info, radius); }

    public String createRegionWithFlags(StructureInfo info, int padding, Map<String, String> flags) {
        if (!worldGuardAvailable) return null;
        try {
            World bukkitWorld = Bukkit.getWorld(info.world);
            if (bukkitWorld == null) { plugin.getLogger().warning("World not found: " + info.world); return null; }
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            com.sk89q.worldguard.protection.managers.RegionManager regionManager = container.get(weWorld);
            if (regionManager == null) { plugin.getLogger().warning("No region manager for world: " + info.world); return null; }
            String regionId = info.generateRegionId();
            if (regionManager.hasRegion(regionId)) { plugin.getConfigManager().debug("Region already exists: " + regionId); return regionId; }
            int minY = Math.max(worldMinY(bukkitWorld), info.minY - padding);
            int maxY = Math.min(worldMaxY(bukkitWorld), info.maxY + padding);
            BlockVector3 min = BlockVector3.at(info.minX - padding, minY, info.minZ - padding);
            BlockVector3 max = BlockVector3.at(info.maxX + padding, maxY, info.maxZ + padding);
            ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);
            RegionFlagService.applyFlags(region, flags);
            regionManager.addRegion(region);
            plugin.getDatabase().setRegionId(info.world, info.type, info.chunkX, info.chunkZ, regionId);
            plugin.getConfigManager().debug("Created BB region: " + regionId + " [" + min + " -> " + max + "]");
            return regionId;
        } catch (Exception e) { plugin.getLogger().warning("Failed to create region: " + e.getMessage()); return null; }
    }

    @Deprecated public String createRegionWithFlags(StructureInfo info, int radius, int yMin, int yMax, Map<String, String> flags) { return createRegionWithFlags(info, radius, flags); }

    public boolean updateRegionBounds(StructureInfo info, int padding) {
        if (!worldGuardAvailable) return false;
        try {
            World bukkitWorld = Bukkit.getWorld(info.world);
            if (bukkitWorld == null) return false;
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);
            com.sk89q.worldguard.protection.managers.RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
            if (regionManager == null) return false;
            ProtectedRegion existing = regionManager.getRegion(info.generateRegionId());
            if (existing == null) return false;
            if (!(existing instanceof ProtectedCuboidRegion)) return false;
            int minY = Math.max(worldMinY(bukkitWorld), info.minY - padding);
            int maxY = Math.min(worldMaxY(bukkitWorld), info.maxY + padding);
            BlockVector3 newMin = BlockVector3.at(info.minX - padding, minY, info.minZ - padding);
            BlockVector3 newMax = BlockVector3.at(info.maxX + padding, maxY, info.maxZ + padding);
            BlockVector3 oldMin = existing.getMinimumPoint();
            BlockVector3 oldMax = existing.getMaximumPoint();
            boolean needsExpand = newMin.getBlockX() < oldMin.getBlockX() || newMin.getBlockY() < oldMin.getBlockY() || newMin.getBlockZ() < oldMin.getBlockZ()
                               || newMax.getBlockX() > oldMax.getBlockX() || newMax.getBlockY() > oldMax.getBlockY() || newMax.getBlockZ() > oldMax.getBlockZ();
            if (!needsExpand) return false;
            BlockVector3 finalMin = BlockVector3.at(Math.min(oldMin.getBlockX(), newMin.getBlockX()), Math.min(oldMin.getBlockY(), newMin.getBlockY()), Math.min(oldMin.getBlockZ(), newMin.getBlockZ()));
            BlockVector3 finalMax = BlockVector3.at(Math.max(oldMax.getBlockX(), newMax.getBlockX()), Math.max(oldMax.getBlockY(), newMax.getBlockY()), Math.max(oldMax.getBlockZ(), newMax.getBlockZ()));
            ProtectedCuboidRegion replacement = new ProtectedCuboidRegion(existing.getId(), finalMin, finalMax);
            replacement.setFlags(existing.getFlags());
            replacement.setOwners(existing.getOwners());
            replacement.setMembers(existing.getMembers());
            replacement.setPriority(existing.getPriority());
            regionManager.removeRegion(existing.getId());
            regionManager.addRegion(replacement);
            plugin.getConfigManager().debug("Expanded region " + existing.getId() + " to [" + finalMin + " -> " + finalMax + "]");
            return true;
        } catch (Exception e) { plugin.getLogger().warning("Failed to update region bounds: " + e.getMessage()); return false; }
    }

    public boolean removeRegion(String worldName, String regionId) {
        if (!worldGuardAvailable) return false;
        try {
            World bukkitWorld = Bukkit.getWorld(worldName);
            if (bukkitWorld == null) return false;
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);
            com.sk89q.worldguard.protection.managers.RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
            if (regionManager == null || !regionManager.hasRegion(regionId)) return false;
            regionManager.removeRegion(regionId);
            return true;
        } catch (Exception e) { plugin.getLogger().warning("Failed to remove region: " + e.getMessage()); return false; }
    }

    public int clearRegions(String pattern) {
        if (!worldGuardAvailable) return 0;
        int removed = 0;
        List<StructureInfo> structures = plugin.getDatabase().getProtectedStructures(pattern);
        for (StructureInfo info : structures) if (info.regionId != null && removeRegion(info.world, info.regionId)) removed++;
        plugin.getDatabase().clearRegions(pattern);
        return removed;
    }

    public int clearAllStructureGuardRegions() {
        if (!worldGuardAvailable) return 0;
        int removed = 0;
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            for (World bukkitWorld : Bukkit.getWorlds()) {
                com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);
                com.sk89q.worldguard.protection.managers.RegionManager regionManager = container.get(weWorld);
                if (regionManager == null) continue;
                List<String> toRemove = new ArrayList<>();
                for (String regionId : regionManager.getRegions().keySet()) if (regionId.startsWith("sg_")) toRemove.add(regionId);
                for (String regionId : toRemove) { regionManager.removeRegion(regionId); removed++; plugin.getConfigManager().debug("Removed region: " + regionId + " from " + bukkitWorld.getName()); }
            }
            plugin.getLogger().info("Cleared " + removed + " StructureGuard regions from all worlds");
        } catch (Exception e) { plugin.getLogger().warning("Failed to clear all regions: " + e.getMessage()); }
        return removed;
    }

    public int clearAllStructureGuardRegionsInWorld(String worldName) {
        if (!worldGuardAvailable) return 0;
        int removed = 0;
        try {
            World bukkitWorld = Bukkit.getWorld(worldName);
            if (bukkitWorld == null) { plugin.getLogger().warning("World not found: " + worldName); return 0; }
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);
            com.sk89q.worldguard.protection.managers.RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
            if (regionManager == null) return 0;
            List<String> toRemove = new ArrayList<>();
            for (String regionId : regionManager.getRegions().keySet()) if (regionId.startsWith("sg_")) toRemove.add(regionId);
            for (String regionId : toRemove) { regionManager.removeRegion(regionId); removed++; }
            plugin.getLogger().info("Cleared " + removed + " StructureGuard regions from " + worldName);
        } catch (Exception e) { plugin.getLogger().warning("Failed to clear regions in world: " + e.getMessage()); }
        return removed;
    }

    public int clearRegionsInWorld(String pattern, String worldName) {
        if (!worldGuardAvailable) return 0;
        int removed = 0;
        List<StructureInfo> structures = plugin.getDatabase().getProtectedStructures(pattern);
        for (StructureInfo info : structures) if (info.regionId != null && info.world.equals(worldName)) if (removeRegion(info.world, info.regionId)) removed++;
        plugin.getDatabase().clearRegions(pattern);
        return removed;
    }

    public int setFlag(String pattern, String flagName, String value) {
        if (!worldGuardAvailable) return 0;
        int updated = 0;
        List<StructureInfo> structures = plugin.getDatabase().getProtectedStructures(pattern);
        for (StructureInfo info : structures) if (info.regionId != null) if (setRegionFlag(info.world, info.regionId, flagName, value)) updated++;
        return updated;
    }

    public boolean setRegionFlag(String worldName, String regionId, String flagName, String value) {
        if (!worldGuardAvailable) return false;
        try {
            World bukkitWorld = Bukkit.getWorld(worldName);
            if (bukkitWorld == null) return false;
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);
            com.sk89q.worldguard.protection.managers.RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
            if (regionManager == null) return false;
            ProtectedRegion region = regionManager.getRegion(regionId);
            if (region == null) return false;
            return RegionFlagService.setFlag(region, flagName, value);
        } catch (Exception e) { plugin.getLogger().warning("Failed to set flag: " + e.getMessage()); return false; }
    }

    public Map<String, String> getRegionFlags(String worldName, String regionId) {
        Map<String, String> flags = new LinkedHashMap<>();
        if (!worldGuardAvailable) return flags;
        try {
            World bukkitWorld = Bukkit.getWorld(worldName);
            if (bukkitWorld == null) return flags;
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);
            com.sk89q.worldguard.protection.managers.RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
            if (regionManager == null) return flags;
            ProtectedRegion region = regionManager.getRegion(regionId);
            if (region == null) return flags;
            for (Map.Entry<Flag<?>, Object> entry : region.getFlags().entrySet()) flags.put(entry.getKey().getName(), String.valueOf(entry.getValue()));
        } catch (Exception e) { plugin.getLogger().warning("Failed to get flags: " + e.getMessage()); }
        return flags;
    }

    public ProtectedRegion getRegion(String worldName, String regionId) {
        if (!worldGuardAvailable) return null;
        try {
            World bukkitWorld = Bukkit.getWorld(worldName);
            if (bukkitWorld == null) return null;
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);
            com.sk89q.worldguard.protection.managers.RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
            if (regionManager == null) return null;
            return regionManager.getRegion(regionId);
        } catch (Exception e) { return null; }
    }

    public int addOwner(String pattern, String target) {
        if (!worldGuardAvailable) return 0;
        int updated = 0;
        List<StructureInfo> structures = plugin.getDatabase().getProtectedStructures(pattern);
        for (StructureInfo info : structures) if (info.regionId != null) if (addOwnerToRegion(info.world, info.regionId, target)) updated++;
        return updated;
    }

    public int removeOwner(String pattern, String target) {
        if (!worldGuardAvailable) return 0;
        int updated = 0;
        List<StructureInfo> structures = plugin.getDatabase().getProtectedStructures(pattern);
        for (StructureInfo info : structures) if (info.regionId != null) if (removeOwnerFromRegion(info.world, info.regionId, target)) updated++;
        return updated;
    }

    public int addMember(String pattern, String target) {
        if (!worldGuardAvailable) return 0;
        int updated = 0;
        List<StructureInfo> structures = plugin.getDatabase().getProtectedStructures(pattern);
        for (StructureInfo info : structures) if (info.regionId != null) if (addMemberToRegion(info.world, info.regionId, target)) updated++;
        return updated;
    }

    public int removeMember(String pattern, String target) {
        if (!worldGuardAvailable) return 0;
        int updated = 0;
        List<StructureInfo> structures = plugin.getDatabase().getProtectedStructures(pattern);
        for (StructureInfo info : structures) if (info.regionId != null) if (removeMemberFromRegion(info.world, info.regionId, target)) updated++;
        return updated;
    }

    private boolean addOwnerToRegion(String worldName, String regionId, String target) {
        try {
            ProtectedRegion region = getRegion(worldName, regionId);
            if (region == null) return false;
            if (target.startsWith("g:")) region.getOwners().addGroup(target.substring(2));
            else { try { java.util.UUID uuid = Bukkit.getOfflinePlayer(target).getUniqueId(); region.getOwners().addPlayer(uuid); } catch (Exception e) { region.getOwners().addPlayer(target); } }
            return true;
        } catch (Exception e) { plugin.getLogger().warning("Failed to add owner: " + e.getMessage()); return false; }
    }

    private boolean removeOwnerFromRegion(String worldName, String regionId, String target) {
        try {
            ProtectedRegion region = getRegion(worldName, regionId);
            if (region == null) return false;
            if (target.startsWith("g:")) region.getOwners().removeGroup(target.substring(2));
            else { try { java.util.UUID uuid = Bukkit.getOfflinePlayer(target).getUniqueId(); region.getOwners().removePlayer(uuid); } catch (Exception e) { region.getOwners().removePlayer(target); } }
            return true;
        } catch (Exception e) { plugin.getLogger().warning("Failed to remove owner: " + e.getMessage()); return false; }
    }

    private boolean addMemberToRegion(String worldName, String regionId, String target) {
        try {
            ProtectedRegion region = getRegion(worldName, regionId);
            if (region == null) return false;
            if (target.startsWith("g:")) region.getMembers().addGroup(target.substring(2));
            else { try { java.util.UUID uuid = Bukkit.getOfflinePlayer(target).getUniqueId(); region.getMembers().addPlayer(uuid); } catch (Exception e) { region.getMembers().addPlayer(target); } }
            return true;
        } catch (Exception e) { plugin.getLogger().warning("Failed to add member: " + e.getMessage()); return false; }
    }

    private boolean removeMemberFromRegion(String worldName, String regionId, String target) {
        try {
            ProtectedRegion region = getRegion(worldName, regionId);
            if (region == null) return false;
            if (target.startsWith("g:")) region.getMembers().removeGroup(target.substring(2));
            else { try { java.util.UUID uuid = Bukkit.getOfflinePlayer(target).getUniqueId(); region.getMembers().removePlayer(uuid); } catch (Exception e) { region.getMembers().removePlayer(target); } }
            return true;
        } catch (Exception e) { plugin.getLogger().warning("Failed to remove member: " + e.getMessage()); return false; }
    }

    @SuppressWarnings("unchecked")
    public void syncFromConfig() {
        if (!worldGuardAvailable) { plugin.getLogger().warning("WorldGuard not available - cannot sync regions"); return; }
        plugin.getLogger().info("Syncing region settings from config...");
        int updatedCount = 0; int errorCount = 0;
        List<StructureInfo> structures = plugin.getDatabase().getProtectedStructures("");
        for (StructureInfo info : structures) {
            if (info.regionId == null) continue;
            try {
                World bukkitWorld = Bukkit.getWorld(info.world);
                if (bukkitWorld == null) continue;
                com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);
                com.sk89q.worldguard.protection.managers.RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
                if (regionManager == null) continue;
                ProtectedRegion region = regionManager.getRegion(info.regionId);
                if (region == null) continue;
                ProtectionRule rule = plugin.getConfigManager().getProtectionRule(info.type);
                Map<String, String> flagsToApply;
                if (rule != null && rule.flags != null && !rule.flags.isEmpty()) flagsToApply = rule.flags;
                else flagsToApply = plugin.getConfigManager().getDefaultFlags();
                for (Map.Entry<String, String> entry : flagsToApply.entrySet()) RegionFlagService.setFlag(region, entry.getKey(), entry.getValue());
                updatedCount++;
            } catch (Exception e) { errorCount++; }
        }
        plugin.getLogger().info("Synced " + updatedCount + " regions from config" + (errorCount > 0 ? " (" + errorCount + " errors)" : ""));
        protectUnprotectedStructures();
    }

    public int protectUnprotectedStructures() {
        if (!worldGuardAvailable) return 0;
        int createdCount = 0;
        List<StructureInfo> unprotected = plugin.getDatabase().getUnprotectedStructures("");
        for (StructureInfo info : unprotected) {
            ProtectionRule rule = plugin.getConfigManager().getProtectionRule(info.type);
            if (rule == null || !rule.enabled) continue;
            if (!plugin.getConfigManager().isWorldAllowed(info.world)) continue;
            try {
                String regionId = createRegionWithFlags(info, rule.padding, rule.flags);
                if (regionId != null) { createdCount++; plugin.getLogger().info("Auto-protected " + info.type + " [" + info.minX + "," + info.minZ + " -> " + info.maxX + "," + info.maxZ + "] -> " + regionId); }
            } catch (Exception e) { plugin.getConfigManager().debug("Failed to protect " + info.type + ": " + e.getMessage()); }
        }
        if (createdCount > 0) plugin.getLogger().info("Created " + createdCount + " new regions for previously discovered structures");
        return createdCount;
    }

    public List<String> getAvailableFlags() {
        List<String> flags = new ArrayList<>();
        if (!worldGuardAvailable) return flags;
        try {
            com.sk89q.worldguard.protection.flags.registry.FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            for (Flag<?> flag : registry.getAll()) flags.add(flag.getName());
            Collections.sort(flags);
        } catch (Exception e) {
            flags.addAll(Arrays.asList("build", "block-break", "block-place", "use", "interact", "pvp", "mob-spawning", "mob-damage", "creeper-explosion", "tnt", "fire-spread", "entry", "exit", "greeting", "farewell"));
        }
        return flags;
    }
}
