package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import com.structureguard.config.ProtectionRule;
import org.bukkit.command.CommandSender;

public class DisableCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public DisableCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg disable <pattern>"; }
    @Override public boolean execute(CommandSender sender, String[] args){
        if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length<2){ sender.sendMessage("§cUsage: " + usage()); return true; }
        String pattern=args[1].toLowerCase(); ProtectionRule rule=plugin.getConfigManager().getProtectionRule(pattern); if(rule==null){sender.sendMessage("§cNo rule found for: "+pattern); return true;}
        rule.enabled=false; plugin.getConfigManager().addProtectionRule(rule); sender.sendMessage("§a✓ Disabled: §e"+pattern); return true;
    }
}
