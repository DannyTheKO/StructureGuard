package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import org.bukkit.command.CommandSender;

public class DebugCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public DebugCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg debug"; }
    @Override public boolean execute(CommandSender sender, String[] args){ if(!sender.hasPermission(permission())){sender.sendMessage("§cNo permission.");return true;} boolean cur=plugin.getConfigManager().isDebugMode(); plugin.getConfigManager().setDebugMode(!cur); sender.sendMessage("§7Debug: "+(!cur?"§aOn":"§cOff")); return true; }
}
