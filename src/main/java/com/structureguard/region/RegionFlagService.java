package com.structureguard.region;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;

import java.util.Map;

public final class RegionFlagService {
    private RegionFlagService() {}

    @SuppressWarnings("unchecked")
    public static void applyFlags(ProtectedRegion region, Map<String, String> flags) {
        if (flags == null) return;
        for (Map.Entry<String, String> entry : flags.entrySet()) {
            try {
                FlagRegistry flagRegistry = WorldGuard.getInstance().getFlagRegistry();
                Flag<?> flag = flagRegistry.get(entry.getKey());
                if (flag == null) flag = Flags.fuzzyMatchFlag(flagRegistry, entry.getKey());
                if (flag == null) continue;
                if (flag instanceof StateFlag) {
                    StateFlag sf = (StateFlag) flag;
                    if ("allow".equalsIgnoreCase(entry.getValue())) region.setFlag(sf, StateFlag.State.ALLOW);
                    else if ("deny".equalsIgnoreCase(entry.getValue())) region.setFlag(sf, StateFlag.State.DENY);
                } else {
                    FlagContext context = FlagContext.create().setSender(com.sk89q.worldguard.bukkit.WorldGuardPlugin.inst().wrapCommandSender(Bukkit.getConsoleSender())).setInput(entry.getValue()).build();
                    Object parsedValue = flag.parseInput(context);
                    region.setFlag((Flag<Object>) flag, parsedValue);
                }
            } catch (Exception ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean setFlag(ProtectedRegion region, String flagName, String value) {
        try {
            FlagRegistry flagRegistry = WorldGuard.getInstance().getFlagRegistry();
            Flag<?> flag = flagRegistry.get(flagName);
            if (flag == null) flag = Flags.fuzzyMatchFlag(flagRegistry, flagName);
            if (flag == null) return false;
            if (value.equalsIgnoreCase("allow") && flag instanceof StateFlag) region.setFlag((StateFlag) flag, StateFlag.State.ALLOW);
            else if (value.equalsIgnoreCase("deny") && flag instanceof StateFlag) region.setFlag((StateFlag) flag, StateFlag.State.DENY);
            else if (value.equalsIgnoreCase("none") || value.equalsIgnoreCase("clear")) region.setFlag(flag, null);
            else {
                FlagContext context = FlagContext.create().setSender(com.sk89q.worldguard.bukkit.WorldGuardPlugin.inst().wrapCommandSender(Bukkit.getConsoleSender())).setInput(value).build();
                Object parsedValue = flag.parseInput(context);
                region.setFlag((Flag<Object>) flag, parsedValue);
            }
            return true;
        } catch (Exception e) { return false; }
    }
}
