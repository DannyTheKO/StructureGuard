package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import com.structureguard.config.ProtectionRule;
import org.bukkit.command.CommandSender;

import java.util.Arrays;

public class FlagCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public FlagCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg flag <pattern> <flag> <value>"; }
    @Override public boolean execute(CommandSender sender, String[] args){
        if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length<4){ sender.sendMessage("§cUsage: " + usage()); return true; }
        String pattern=args[1].toLowerCase(), flagName=args[2].toLowerCase(), value=String.join(" ", Arrays.copyOfRange(args,3,args.length)); if(value.startsWith("\"")&&value.endsWith("\"")&&value.length()>1) value=value.substring(1,value.length()-1);
        int updatedRules=0, updatedRegions=0; ProtectionRule rule=plugin.getConfigManager().getProtectionRule(pattern); if(rule!=null){ if(value.equalsIgnoreCase("none")||value.equalsIgnoreCase("remove")) rule.flags.remove(flagName); else rule.flags.put(flagName,value); plugin.getConfigManager().addProtectionRule(rule); updatedRules=1;}
        if(plugin.getRegionManager().isWorldGuardAvailable()) updatedRegions=plugin.getRegionManager().setFlag(pattern, flagName, value);
        if(updatedRules>0||updatedRegions>0){ sender.sendMessage("§a✓ Set §e"+flagName+" = "+value); if(updatedRules>0) sender.sendMessage("§7Updated rule: §e"+pattern); if(updatedRegions>0) sender.sendMessage("§7Updated §e"+updatedRegions+"§7 regions"); } else sender.sendMessage("§cNo matching rule or regions for: "+pattern);
        return true;
    }
}
