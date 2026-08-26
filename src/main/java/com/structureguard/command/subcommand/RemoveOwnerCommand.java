package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import org.bukkit.command.CommandSender;

public class RemoveOwnerCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public RemoveOwnerCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg removeowner <pattern> <player|g:group>"; }
    @Override public boolean execute(CommandSender sender, String[] args){ if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; } if(args.length<3){sender.sendMessage("§cUsage: " + usage()); return true;} int u=plugin.getRegionManager().removeOwner(args[1].toLowerCase(), args[2]); if(u>0) sender.sendMessage("§a✓ Removed owner from "+u+" regions."); else sender.sendMessage("§cNo matching regions."); return true; }
}
