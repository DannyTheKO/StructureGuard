package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import com.structureguard.config.ProtectionRule;
import com.structureguard.database.model.StructureInfo;
import com.structureguard.structure.model.StructureResult;
import com.structureguard.util.PatternMatcher;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;

public class ProtectCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public ProtectCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg protect <pattern> [padding]"; }
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length<2) { sender.sendMessage("§cUsage: " + usage()); sender.sendMessage("§7Example: /sg protect minecraft:village 5"); return true; }
        String pattern=args[1].toLowerCase(); if(!pattern.contains(":") && !pattern.equals("*")){ pattern="*:*"+pattern+"*"; sender.sendMessage("§7Using wildcard pattern: §e"+pattern); }
        int padding=plugin.getConfigManager().getDefaultPadding(); if(args.length>=3) try{padding=Integer.parseInt(args[2]);}catch(NumberFormatException e){sender.sendMessage("§cInvalid padding.");return true;}
        ProtectionRule rule=plugin.getConfigManager().getProtectionRule(pattern); boolean isNew=(rule==null); if(rule==null){rule=new ProtectionRule(); rule.pattern=pattern; rule.flags=new HashMap<>(plugin.getConfigManager().getDefaultFlags());}
        rule.enabled=true; rule.padding=padding; plugin.getConfigManager().addProtectionRule(rule);
        if(isNew) sender.sendMessage("§a✓ Added protection rule: §e"+pattern); else sender.sendMessage("§a✓ Updated protection rule: §e"+pattern);
        sender.sendMessage("§7Padding: "+padding+" | BB Y = minY-padding to maxY+padding");
        final String finalPattern=pattern; final ProtectionRule finalRule=rule; int created=protectExistingStructures(finalPattern, finalRule);
        if(sender instanceof Player && plugin.getRegionManager()!=null && plugin.getRegionManager().isWorldGuardAvailable()){
            Player player=(Player)sender; int chunkX=player.getLocation().getBlockX()>>4, chunkZ=player.getLocation().getBlockZ()>>4;
            for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++){ List<StructureResult> nearby=plugin.getStructureFinder().getStructuresInChunk(player.getWorld(), chunkX+dx, chunkZ+dz); for(StructureResult s:nearby) if(PatternMatcher.matches(s.structureType, finalPattern)){
                plugin.getDatabase().addStructure(player.getWorld().getName(), s.structureType, s.minX, s.minZ, s.maxX, s.maxZ, s.minY, s.maxY, s.chunkX, s.chunkZ);
                StructureInfo info=new StructureInfo(player.getWorld().getName(), s.structureType, s.minX, s.minZ, s.maxX, s.maxZ, s.minY, s.maxY, s.chunkX, s.chunkZ, false, null);
                String regionId=plugin.getRegionManager().createRegionWithFlags(info, finalRule.padding, finalRule.flags);
                if(regionId!=null){ plugin.getDatabase().setRegionId(player.getWorld().getName(), s.structureType, s.chunkX, s.chunkZ, regionId); created++; sender.sendMessage("§a✓ Protected nearby: §e"+s.structureType+" §7["+s.minX+","+s.minZ+" -> "+s.maxX+","+s.maxZ+"]");}
            } }
        }
        if(created>0) sender.sendMessage("§a✓ Created §e"+created+"§a region(s) total."); else sender.sendMessage("§7Structures will be auto-protected when chunks load.");
        return true;
    }
    private int protectExistingStructures(String pattern, ProtectionRule rule){ int created=0; List<StructureInfo> structures=plugin.getDatabase().getUnprotectedStructures(pattern); for(StructureInfo info:structures) if(PatternMatcher.matches(info.type, pattern)){ String regionId=plugin.getRegionManager().createRegionWithFlags(info, rule.padding, rule.flags); if(regionId!=null) created++; } return created; }
}
