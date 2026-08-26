package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import org.bukkit.command.CommandSender;

public class RemoveMemberCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public RemoveMemberCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg removemember <pattern> <player|g:group>"; }
    @Override public boolean execute(CommandSender sender, String[] args){ if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; } if(args.length<3){sender.sendMessage("§cUsage: " + usage()); return true;} int u=plugin.getRegionManager().removeMember(args[1].toLowerCase(), args[2]); if(u>0) sender.sendMessage("§a✓ Removed member from "+u+" regions."); else sender.sendMessage("§cNo matching regions."); return true; }
}
