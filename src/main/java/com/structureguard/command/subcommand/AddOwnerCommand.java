package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import org.bukkit.command.CommandSender;

public class AddOwnerCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public AddOwnerCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg addowner <pattern> <player|g:group>"; }
    @Override public boolean execute(CommandSender sender, String[] args){ if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; } if(args.length<3){sender.sendMessage("§cUsage: " + usage()); return true;} int u=plugin.getRegionManager().addOwner(args[1].toLowerCase(), args[2]); if(u>0) sender.sendMessage("§a✓ Added owner to "+u+" regions."); else sender.sendMessage("§cNo regions matching."); return true; }
}
