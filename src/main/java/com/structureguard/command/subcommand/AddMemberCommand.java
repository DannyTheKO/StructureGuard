package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import org.bukkit.command.CommandSender;

public class AddMemberCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public AddMemberCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg addmember <pattern> <player|g:group>"; }
    @Override public boolean execute(CommandSender sender, String[] args){ if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; } if(args.length<3){sender.sendMessage("§cUsage: " + usage()); return true;} int u=plugin.getRegionManager().addMember(args[1].toLowerCase(), args[2]); if(u>0) sender.sendMessage("§a✓ Added member to "+u+" regions."); else sender.sendMessage("§cNo regions matching."); return true; }
}
