package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import com.structureguard.config.ProtectionRule;
import org.bukkit.command.CommandSender;

import java.util.HashMap;

public class EnableCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public EnableCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg enable <pattern> [padding]"; }
    @Override public boolean execute(CommandSender sender, String[] args){
        if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length<2){ sender.sendMessage("§cUsage: " + usage()); return true; }
        String pattern=args[1].toLowerCase(); int padding=plugin.getConfigManager().getDefaultPadding(); if(args.length>=3) try{padding=Integer.parseInt(args[2]);}catch(Exception e){sender.sendMessage("§cInvalid padding.");return true;}
        ProtectionRule rule=plugin.getConfigManager().getProtectionRule(pattern); if(rule==null){rule=new ProtectionRule(); rule.pattern=pattern; rule.flags=new HashMap<>(plugin.getConfigManager().getDefaultFlags());}
        rule.enabled=true; rule.padding=padding; plugin.getConfigManager().addProtectionRule(rule); sender.sendMessage("§a✓ Enabled protection rule: §e"+pattern+" §7Padding: "+padding); return true;
    }
}
