package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import org.bukkit.command.CommandSender;

public class UnprotectCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public UnprotectCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg unprotect <pattern> [--clear]"; }
    @Override public boolean execute(CommandSender sender, String[] args){
        if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length<2){ sender.sendMessage("§cUsage: " + usage()); return true; }
        String pattern=args[1].toLowerCase(); boolean clear=false; for(int i=2;i<args.length;i++) if(args[i].equalsIgnoreCase("--clear")||args[i].equalsIgnoreCase("-c")) clear=true;
        if(!pattern.contains(":") && !pattern.equals("*")) pattern="*:*"+pattern+"*";
        boolean removed=plugin.getConfigManager().removeProtectionRule(pattern); if(!removed){ sender.sendMessage("§cNo rule found for: "+pattern); return true; }
        sender.sendMessage("§a✓ Removed protection rule: §e"+pattern); if(clear){ int c=plugin.getRegionManager().clearRegions(pattern); sender.sendMessage("§7Cleared §e"+c+"§7 regions."); } else sender.sendMessage("§7Existing regions NOT removed. Use /sg clearregions "+pattern);
        return true;
    }
}
