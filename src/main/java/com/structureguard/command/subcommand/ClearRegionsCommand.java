package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import org.bukkit.command.CommandSender;

public class ClearRegionsCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public ClearRegionsCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg clearregions <pattern> [world]"; }
    @Override public boolean execute(CommandSender sender, String[] args){
        if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length<2){ sender.sendMessage("§cUsage: " + usage()); return true; }
        String pattern=args[1]; String worldName=args.length>=3?args[2]:null; int removed; if(pattern.equals("*")){ if(worldName!=null) removed=plugin.getRegionManager().clearAllStructureGuardRegionsInWorld(worldName); else { removed=plugin.getRegionManager().clearAllStructureGuardRegions(); plugin.getDatabase().reset(); }
            sender.sendMessage("§a✓ Removed "+removed+" regions"); } else { if(worldName!=null) removed=plugin.getRegionManager().clearRegionsInWorld(pattern.toLowerCase(), worldName); else removed=plugin.getRegionManager().clearRegions(pattern.toLowerCase()); sender.sendMessage("§a✓ Removed "+removed+" regions matching '"+pattern+"'"); }
        return true;
    }
}
