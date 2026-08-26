package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import org.bukkit.command.CommandSender;

public class ResetWorldCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public ResetWorldCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg resetworld <world> confirm"; }
    @Override public boolean execute(CommandSender sender, String[] args){
        if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length<2){ sender.sendMessage("§cUsage: " + usage()); return true; }
        String worldName=args[1]; if(args.length<3 || !args[2].equalsIgnoreCase("confirm")){ sender.sendMessage("§e⚠ This will remove ALL data for "+worldName); sender.sendMessage("§cType: /sg resetworld "+worldName+" confirm"); return true; }
        sender.sendMessage("§7Resetting world: "+worldName+"..."); int regionsRemoved=plugin.getRegionManager().clearAllStructureGuardRegionsInWorld(worldName); int structuresRemoved=plugin.getDatabase().clearWorld(worldName); int chunksCleared=plugin.getDatabase().clearScannedChunks(worldName); plugin.getChunkLoadListener().clearWorldCache(worldName); sender.sendMessage("§a✓ Reset: regions "+regionsRemoved+" structures "+structuresRemoved+" chunks "+chunksCleared); return true;
    }
}
