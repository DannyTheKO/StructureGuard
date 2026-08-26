package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import com.structureguard.config.ProtectionRule;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class RulesCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public RulesCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg rules"; }
    @Override public boolean execute(CommandSender sender, String[] args){
        if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; }
        Map<String,ProtectionRule> rules=plugin.getConfigManager().getProtectionRules(); sender.sendMessage("§6§l=== Protection Rules ==="); if(rules.isEmpty()){sender.sendMessage("§7No rules. Use /sg enable <pattern>"); return true;}
        for(ProtectionRule rule:rules.values()){ String status=rule.enabled?"§a●":"§c○"; sender.sendMessage(status+" §e"+rule.pattern+" §7padding §f"+rule.padding); if(!rule.flags.isEmpty()){ StringBuilder sb=new StringBuilder(); int c=0; for(Map.Entry<String,String> f:rule.flags.entrySet()){if(c++>0) sb.append(", "); if(c>3){sb.append("...");break;} sb.append(f.getKey()).append("=").append(f.getValue());} sender.sendMessage("  §7Flags: §f"+sb);} }
        sender.sendMessage("§7Legend: §a● enabled §c○ disabled — region = BB + padding");
        return true;
    }
}
