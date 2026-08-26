package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public ReloadCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg reload"; }
    @Override public boolean execute(CommandSender sender, String[] args){ if(!sender.hasPermission(permission())){sender.sendMessage("§cNo permission.");return true;} plugin.reload(); sender.sendMessage("§a✓ Reloaded. Old DB was wiped on startup (BB schema)."); return true; }
}
