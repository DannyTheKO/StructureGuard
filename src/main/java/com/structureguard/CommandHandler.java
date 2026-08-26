package com.structureguard;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final StructureGuardPlugin plugin;
    public CommandHandler(StructureGuardPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { showHelp(sender); return true; }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "find": return cmdFind(sender,args);
            case "listall": return cmdListAll(sender,args);
            case "info": return cmdInfo(sender,args);
            case "protect": return cmdProtect(sender,args);
            case "unprotect": return cmdUnprotect(sender,args);
            case "enable": return cmdEnable(sender,args);
            case "disable": return cmdDisable(sender,args);
            case "rules": return cmdRules(sender,args);
            case "flag": return cmdFlag(sender,args);
            case "clearregions": return cmdClearRegions(sender,args);
            case "resetworld": return cmdResetWorld(sender,args);
            case "addowner": return cmdAddOwner(sender,args);
            case "removeowner": return cmdRemoveOwner(sender,args);
            case "addmember": return cmdAddMember(sender,args);
            case "removemember": return cmdRemoveMember(sender,args);
            case "list": return cmdList(sender,args);
            case "status": return cmdStatus(sender,args);
            case "reload": return cmdReload(sender,args);
            case "debug": return cmdDebug(sender,args);
            case "probe": return cmdProbe(sender,args);
            case "methods": return cmdMethods(sender,args);
            default: sender.sendMessage("§cUnknown command. Use /sg for help."); return true;
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6§l=== StructureGuard (1.21 BB) ===");
        sender.sendMessage("§e/sg listall §7- List all structure types");
        sender.sendMessage("§e/sg find <structure> §7- Locate nearest structure");
        sender.sendMessage("§e/sg info §7- Show BB at your location");
        sender.sendMessage("§e/sg protect <pattern> [padding] §7- Add rule (BB + padding)");
        sender.sendMessage("§e/sg unprotect <pattern> [--clear] §7- Remove rule");
        sender.sendMessage("§e/sg enable/disable <pattern> [padding] §7- Toggle rule");
        sender.sendMessage("§e/sg rules §7- Show rules");
        sender.sendMessage("§e/sg flag <pattern> <flag> <value>");
        sender.sendMessage("§e/sg clearregions <pattern> [world]");
        sender.sendMessage("§e/sg resetworld <world> confirm");
        sender.sendMessage("§e/sg list <pattern> [page] §7- List DB BB entries");
        sender.sendMessage("§e/sg status §7- System status");
        sender.sendMessage("§e/sg reload §7- Reload");
    }

    private boolean cmdFind(CommandSender sender, String[] args) {
        if (!sender.hasPermission("structureguard.find")) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length < 2) { sender.sendMessage("§cUsage: /sg find <structure-name>"); return true; }
        if (!(sender instanceof Player)) { sender.sendMessage("§cPlayers only."); return true; }
        Player player = (Player) sender;
        String structureType = args[1].toLowerCase();
        String pattern = structureType.contains(":") ? structureType : "*:*" + structureType + "*";
        player.sendMessage("§7Searching for " + structureType + " in nearby chunks...");
        int chunkX = player.getLocation().getBlockX() >> 4;
        int chunkZ = player.getLocation().getBlockZ() >> 4;
        int searchRadius = 4;
        StructureFinder.StructureResult nearest = null; double nearestDist = Double.MAX_VALUE;
        for (int dx=-searchRadius; dx<=searchRadius; dx++) for (int dz=-searchRadius; dz<=searchRadius; dz++) {
            List<StructureFinder.StructureResult> results = plugin.getStructureFinder().getStructuresInChunk(player.getWorld(), chunkX+dx, chunkZ+dz);
            for (StructureFinder.StructureResult s : results) if (matchesPattern(s.structureType, pattern)) {
                double dist = Math.sqrt(Math.pow(s.getCenterX()-player.getLocation().getBlockX(),2)+Math.pow(s.getCenterZ()-player.getLocation().getBlockZ(),2));
                if (dist<nearestDist) { nearestDist=dist; nearest=s; }
            }
        }
        Location dbFound = findNearestFromDatabase(player, pattern);
        if (dbFound != null) {
            double dbDist = player.getLocation().distance(dbFound);
            if (nearest==null || dbDist < nearestDist) {
                player.sendMessage("§a✓ Found (from database)"); player.sendMessage("§7Location: §f"+dbFound.getBlockX()+", "+dbFound.getBlockZ()); player.sendMessage("§7Distance: §f"+String.format("%.0f",dbDist)+" blocks");
                if (player.hasPermission("structureguard.teleport")) { TextComponent tp=new TextComponent("§e[Click to teleport]"); tp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/minecraft:tp @s "+dbFound.getBlockX()+" "+(dbFound.getBlockY()+5)+" "+dbFound.getBlockZ())); tp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click to teleport").create())); player.spigot().sendMessage(tp); }
                return true;
            }
        }
        if (nearest != null) {
            player.sendMessage("§a✓ Found "+nearest.structureType); player.sendMessage("§7BB: §f["+nearest.minX+","+nearest.minY+","+nearest.minZ+" -> "+nearest.maxX+","+nearest.maxY+","+nearest.maxZ+"]"); player.sendMessage("§7Center: §f"+nearest.getCenterX()+", "+nearest.getCenterZ()+" §7Chunk: §f"+nearest.chunkX+", "+nearest.chunkZ); player.sendMessage("§7Distance: §f"+String.format("%.0f",nearestDist)+" blocks");
            plugin.getDatabase().addStructure(player.getWorld().getName(), nearest.structureType, nearest.minX, nearest.minZ, nearest.maxX, nearest.maxZ, nearest.minY, nearest.maxY, nearest.chunkX, nearest.chunkZ);
            if (player.hasPermission("structureguard.teleport")) { TextComponent tp=new TextComponent("§e[Click to teleport]"); tp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/minecraft:tp @s "+nearest.getCenterX()+" 100 "+nearest.getCenterZ())); tp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click to teleport").create())); player.spigot().sendMessage(tp); }
        } else { player.sendMessage("§cNo "+structureType+" found within "+(searchRadius*16)+" blocks."); }
        return true;
    }

    private Location findNearestFromDatabase(Player player, String pattern) {
        List<StructureDatabase.StructureInfo> list = plugin.getDatabase().getStructuresOfType(player.getWorld().getName(), pattern);
        if (list.isEmpty()) return null;
        int px = player.getLocation().getBlockX(), pz = player.getLocation().getBlockZ();
        StructureDatabase.StructureInfo best=null; double bestDist=Double.MAX_VALUE;
        for (StructureDatabase.StructureInfo s : list) { double d=Math.sqrt(Math.pow(s.getCenterX()-px,2)+Math.pow(s.getCenterZ()-pz,2)); if (d<bestDist){bestDist=d; best=s;} }
        if (best!=null && bestDist<=10000) return new Location(player.getWorld(), best.getCenterX(), best.getCenterY(), best.getCenterZ());
        return null;
    }

    private boolean cmdListAll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("structureguard.listall")) { sender.sendMessage("§cNo permission."); return true; }
        sender.sendMessage("§7Loading structure registry...");
        List<String> types = plugin.getStructureFinder().getAllStructureTypes();
        if (types.isEmpty()) { sender.sendMessage("§cNo structure types found."); return true; }
        int page=1; if(args.length>=2) try{page=Integer.parseInt(args[1]);}catch(Exception ignored){}
        int perPage=15; int totalPages=(int)Math.ceil(types.size()/(double)perPage); page=Math.max(1, Math.min(page,totalPages)); int start=(page-1)*perPage; int end=Math.min(start+perPage, types.size());
        sender.sendMessage("§6§l=== Structure Types ["+page+"/"+totalPages+"] ===");
        for(int i=start;i<end;i++){ String type=types.get(i); String ns=type.contains(":")?type.substring(0,type.indexOf(":")):"minecraft"; String color=ns.equals("minecraft")?"§e":"§d"; ConfigManager.ProtectionRule rule=plugin.getConfigManager().getProtectionRule(type); String status=(rule!=null&&rule.enabled)?" §a✓":""; if(sender instanceof Player){ TextComponent line=new TextComponent(color+type+status); line.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sg enable "+type)); line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§7Click to enable\n§e/sg enable "+type).create())); ((Player)sender).spigot().sendMessage(line);} else sender.sendMessage(color+type+status); }
        sender.sendMessage("§7Total: §f"+types.size()+" §7| §a✓§7 = auto-protected");
        if(totalPages>1 && sender instanceof Player){ TextComponent nav=new TextComponent(""); if(page>1){TextComponent prev=new TextComponent("§a[« Prev] "); prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sg listall "+(page-1))); nav.addExtra(prev);} nav.addExtra(new TextComponent("§7Page "+page+"/"+totalPages+" ")); if(page<totalPages){TextComponent next=new TextComponent("§a[Next »]"); next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sg listall "+(page+1))); nav.addExtra(next);} ((Player)sender).spigot().sendMessage(nav); }
        return true;
    }

    private boolean cmdInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("§cPlayers only."); return true; }
        Player player=(Player)sender; Location loc=player.getLocation(); int chunkX=loc.getBlockX()>>4, chunkZ=loc.getBlockZ()>>4;
        player.sendMessage("§6§l=== Structure Info (BB) ==="); player.sendMessage("§7Location: §f"+loc.getBlockX()+", "+loc.getBlockY()+", "+loc.getBlockZ()); player.sendMessage("§7Chunk: §f"+chunkX+", "+chunkZ);
        Map<String, StructureFinder.StructureResult> uniq=new LinkedHashMap<>();
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++){ List<StructureFinder.StructureResult> list=plugin.getStructureFinder().getStructuresSpanningChunk(player.getWorld(), chunkX+dx, chunkZ+dz); for(StructureFinder.StructureResult s:list){ String key=s.structureType+"@"+s.chunkX+","+s.chunkZ; if(!uniq.containsKey(key)) uniq.put(key,s);} }
        if(!uniq.isEmpty()){ player.sendMessage("§7Structures in this area:"); for(StructureFinder.StructureResult s:uniq.values()){ boolean isProtected=plugin.getDatabase().isStructureProtected(player.getWorld().getName(), s.structureType, s.chunkX, s.chunkZ); String status=isProtected?" §a(protected)":" §7(unprotected)"; double dist=Math.sqrt(Math.pow(s.getCenterX()-loc.getBlockX(),2)+Math.pow(s.getCenterZ()-loc.getBlockZ(),2)); String distStr=" §8("+String.format("%.0f",dist)+"b to center)"; String bb=" §8["+s.minX+","+s.minZ+" -> "+s.maxX+","+s.maxZ+" Y"+s.minY+"->"+s.maxY+"]"; player.sendMessage("  §e"+s.structureType+status+distStr+bb); if(!isProtected && findMatchingRule(s.structureType)!=null){ TextComponent link=new TextComponent("    §a[Click to Protect]"); link.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sg protect "+s.structureType)); link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§aProtect this structure").create())); player.spigot().sendMessage(link);} } } else player.sendMessage("§7No structures detected in this area.");
        StructureDatabase.StructureInfo nearest=plugin.getDatabase().getNearestStructure(player.getWorld().getName(), loc.getBlockX(), loc.getBlockZ(), 500);
        if(nearest!=null){ double dist=Math.sqrt(Math.pow(nearest.getCenterX()-loc.getX(),2)+Math.pow(nearest.getCenterZ()-loc.getZ(),2)); player.sendMessage("§7Nearest in DB: §e"+nearest.type+" §7("+String.format("%.0f",dist)+"b) ["+nearest.minX+","+nearest.minZ+" -> "+nearest.maxX+","+nearest.maxZ+"]"); if(nearest.hasRegion) player.sendMessage("§7Region: §f"+nearest.regionId); }
        return true;
    }

    private boolean cmdProtect(CommandSender sender, String[] args) {
        if (!sender.hasPermission("structureguard.admin")) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length<2) { sender.sendMessage("§cUsage: /sg protect <pattern> [padding]"); sender.sendMessage("§7Example: /sg protect minecraft:village 5"); return true; }
        String pattern=args[1].toLowerCase(); if(!pattern.contains(":") && !pattern.equals("*")){ pattern="*:*"+pattern+"*"; sender.sendMessage("§7Using wildcard pattern: §e"+pattern); }
        int padding=plugin.getConfigManager().getDefaultPadding(); if(args.length>=3) try{padding=Integer.parseInt(args[2]);}catch(NumberFormatException e){sender.sendMessage("§cInvalid padding.");return true;}
        ConfigManager.ProtectionRule rule=plugin.getConfigManager().getProtectionRule(pattern); boolean isNew=(rule==null); if(rule==null){rule=new ConfigManager.ProtectionRule(); rule.pattern=pattern; rule.flags=new HashMap<>(plugin.getConfigManager().getDefaultFlags());}
        rule.enabled=true; rule.padding=padding; plugin.getConfigManager().addProtectionRule(rule);
        if(isNew) sender.sendMessage("§a✓ Added protection rule: §e"+pattern); else sender.sendMessage("§a✓ Updated protection rule: §e"+pattern);
        sender.sendMessage("§7Padding: "+padding+" | BB Y = minY-padding to maxY+padding");
        final String finalPattern=pattern; final ConfigManager.ProtectionRule finalRule=rule; int created=protectExistingStructures(finalPattern, finalRule);
        if(sender instanceof Player && plugin.getRegionManager()!=null && plugin.getRegionManager().isWorldGuardAvailable()){
            Player player=(Player)sender; int chunkX=player.getLocation().getBlockX()>>4, chunkZ=player.getLocation().getBlockZ()>>4;
            for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++){ List<StructureFinder.StructureResult> nearby=plugin.getStructureFinder().getStructuresInChunk(player.getWorld(), chunkX+dx, chunkZ+dz); for(StructureFinder.StructureResult s:nearby) if(matchesPattern(s.structureType, finalPattern)){
                plugin.getDatabase().addStructure(player.getWorld().getName(), s.structureType, s.minX, s.minZ, s.maxX, s.maxZ, s.minY, s.maxY, s.chunkX, s.chunkZ);
                StructureDatabase.StructureInfo info=new StructureDatabase.StructureInfo(player.getWorld().getName(), s.structureType, s.minX, s.minZ, s.maxX, s.maxZ, s.minY, s.maxY, s.chunkX, s.chunkZ, false, null);
                String regionId=plugin.getRegionManager().createRegionWithFlags(info, finalRule.padding, finalRule.flags);
                if(regionId!=null){ plugin.getDatabase().setRegionId(player.getWorld().getName(), s.structureType, s.chunkX, s.chunkZ, regionId); created++; sender.sendMessage("§a✓ Protected nearby: §e"+s.structureType+" §7["+s.minX+","+s.minZ+" -> "+s.maxX+","+s.maxZ+"]");}
            } }
        }
        if(created>0) sender.sendMessage("§a✓ Created §e"+created+"§a region(s) total."); else sender.sendMessage("§7Structures will be auto-protected when chunks load.");
        return true;
    }

    private int protectExistingStructures(String pattern, ConfigManager.ProtectionRule rule){ int created=0; List<StructureDatabase.StructureInfo> structures=plugin.getDatabase().getUnprotectedStructures(pattern); for(StructureDatabase.StructureInfo info:structures) if(matchesPattern(info.type, pattern)){ String regionId=plugin.getRegionManager().createRegionWithFlags(info, rule.padding, rule.flags); if(regionId!=null) created++; } return created; }
    private boolean matchesPattern(String t,String p){ if(p.equals("*"))return true; if(p.contains("*")) return t.matches(p.replace(".","\\.").replace("*",".*")); return p.equals(t); }
    private ConfigManager.ProtectionRule findMatchingRule(String t){ for(Map.Entry<String,ConfigManager.ProtectionRule> e:plugin.getConfigManager().getProtectionRules().entrySet()) if(matchesPattern(t,e.getKey())) return e.getValue(); return null; }

    private boolean cmdUnprotect(CommandSender sender, String[] args){
        if (!sender.hasPermission("structureguard.admin")) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length<2){ sender.sendMessage("§cUsage: /sg unprotect <pattern> [--clear]"); return true; }
        String pattern=args[1].toLowerCase(); boolean clear=false; for(int i=2;i<args.length;i++) if(args[i].equalsIgnoreCase("--clear")||args[i].equalsIgnoreCase("-c")) clear=true;
        if(!pattern.contains(":") && !pattern.equals("*")) pattern="*:*"+pattern+"*";
        boolean removed=plugin.getConfigManager().removeProtectionRule(pattern); if(!removed){ sender.sendMessage("§cNo rule found for: "+pattern); return true; }
        sender.sendMessage("§a✓ Removed protection rule: §e"+pattern); if(clear){ int c=plugin.getRegionManager().clearRegions(pattern); sender.sendMessage("§7Cleared §e"+c+"§7 regions."); } else sender.sendMessage("§7Existing regions NOT removed. Use /sg clearregions "+pattern);
        return true;
    }

    private boolean cmdEnable(CommandSender sender, String[] args){
        if (!sender.hasPermission("structureguard.admin")) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length<2){ sender.sendMessage("§cUsage: /sg enable <pattern> [padding]"); return true; }
        String pattern=args[1].toLowerCase(); int padding=plugin.getConfigManager().getDefaultPadding(); if(args.length>=3) try{padding=Integer.parseInt(args[2]);}catch(Exception e){sender.sendMessage("§cInvalid padding.");return true;}
        ConfigManager.ProtectionRule rule=plugin.getConfigManager().getProtectionRule(pattern); if(rule==null){rule=new ConfigManager.ProtectionRule(); rule.pattern=pattern; rule.flags=new HashMap<>(plugin.getConfigManager().getDefaultFlags());}
        rule.enabled=true; rule.padding=padding; plugin.getConfigManager().addProtectionRule(rule); sender.sendMessage("§a✓ Enabled protection rule: §e"+pattern+" §7Padding: "+padding); return true;
    }
    private boolean cmdDisable(CommandSender sender, String[] args){
        if (!sender.hasPermission("structureguard.admin")) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length<2){ sender.sendMessage("§cUsage: /sg disable <pattern>"); return true; }
        String pattern=args[1].toLowerCase(); ConfigManager.ProtectionRule rule=plugin.getConfigManager().getProtectionRule(pattern); if(rule==null){sender.sendMessage("§cNo rule found for: "+pattern); return true;}
        rule.enabled=false; plugin.getConfigManager().addProtectionRule(rule); sender.sendMessage("§a✓ Disabled: §e"+pattern); return true;
    }
    private boolean cmdRules(CommandSender sender, String[] args){
        if (!sender.hasPermission("structureguard.admin")) { sender.sendMessage("§cNo permission."); return true; }
        Map<String,ConfigManager.ProtectionRule> rules=plugin.getConfigManager().getProtectionRules(); sender.sendMessage("§6§l=== Protection Rules ==="); if(rules.isEmpty()){sender.sendMessage("§7No rules. Use /sg enable <pattern>"); return true;}
        for(ConfigManager.ProtectionRule rule:rules.values()){ String status=rule.enabled?"§a●":"§c○"; sender.sendMessage(status+" §e"+rule.pattern+" §7padding §f"+rule.padding); if(!rule.flags.isEmpty()){ StringBuilder sb=new StringBuilder(); int c=0; for(Map.Entry<String,String> f:rule.flags.entrySet()){if(c++>0) sb.append(", "); if(c>3){sb.append("...");break;} sb.append(f.getKey()).append("=").append(f.getValue());} sender.sendMessage("  §7Flags: §f"+sb);} }
        sender.sendMessage("§7Legend: §a● enabled §c○ disabled — region = BB + padding");
        return true;
    }

    private boolean cmdFlag(CommandSender sender, String[] args){
        if (!sender.hasPermission("structureguard.admin")) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length<4){ sender.sendMessage("§cUsage: /sg flag <pattern> <flag> <value>"); return true; }
        String pattern=args[1].toLowerCase(), flagName=args[2].toLowerCase(), value=String.join(" ", Arrays.copyOfRange(args,3,args.length)); if(value.startsWith("\"")&&value.endsWith("\"")&&value.length()>1) value=value.substring(1,value.length()-1);
        int updatedRules=0, updatedRegions=0; ConfigManager.ProtectionRule rule=plugin.getConfigManager().getProtectionRule(pattern); if(rule!=null){ if(value.equalsIgnoreCase("none")||value.equalsIgnoreCase("remove")) rule.flags.remove(flagName); else rule.flags.put(flagName,value); plugin.getConfigManager().addProtectionRule(rule); updatedRules=1;}
        if(plugin.getRegionManager().isWorldGuardAvailable()) updatedRegions=plugin.getRegionManager().setFlag(pattern, flagName, value);
        if(updatedRules>0||updatedRegions>0){ sender.sendMessage("§a✓ Set §e"+flagName+" = "+value); if(updatedRules>0) sender.sendMessage("§7Updated rule: §e"+pattern); if(updatedRegions>0) sender.sendMessage("§7Updated §e"+updatedRegions+"§7 regions"); } else sender.sendMessage("§cNo matching rule or regions for: "+pattern);
        return true;
    }

    private boolean cmdClearRegions(CommandSender sender, String[] args){
        if (!sender.hasPermission("structureguard.admin")) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length<2){ sender.sendMessage("§cUsage: /sg clearregions <pattern> [world]"); return true; }
        String pattern=args[1]; String worldName=args.length>=3?args[2]:null; int removed; if(pattern.equals("*")){ if(worldName!=null) removed=plugin.getRegionManager().clearAllStructureGuardRegionsInWorld(worldName); else { removed=plugin.getRegionManager().clearAllStructureGuardRegions(); plugin.getDatabase().reset(); }
            sender.sendMessage("§a✓ Removed "+removed+" regions"); } else { if(worldName!=null) removed=plugin.getRegionManager().clearRegionsInWorld(pattern.toLowerCase(), worldName); else removed=plugin.getRegionManager().clearRegions(pattern.toLowerCase()); sender.sendMessage("§a✓ Removed "+removed+" regions matching '"+pattern+"'"); }
        return true;
    }
    private boolean cmdResetWorld(CommandSender sender, String[] args){
        if (!sender.hasPermission("structureguard.admin")) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length<2){ sender.sendMessage("§cUsage: /sg resetworld <world>"); return true; }
        String worldName=args[1]; if(args.length<3 || !args[2].equalsIgnoreCase("confirm")){ sender.sendMessage("§e⚠ This will remove ALL data for "+worldName); sender.sendMessage("§cType: /sg resetworld "+worldName+" confirm"); return true; }
        sender.sendMessage("§7Resetting world: "+worldName+"..."); int regionsRemoved=plugin.getRegionManager().clearAllStructureGuardRegionsInWorld(worldName); int structuresRemoved=plugin.getDatabase().clearWorld(worldName); int chunksCleared=plugin.getDatabase().clearScannedChunks(worldName); plugin.getChunkLoadListener().clearWorldCache(worldName); sender.sendMessage("§a✓ Reset: regions "+regionsRemoved+" structures "+structuresRemoved+" chunks "+chunksCleared); return true;
    }
    private boolean cmdAddOwner(CommandSender sender, String[] args){ if (!sender.hasPermission("structureguard.admin")) { sender.sendMessage("§cNo permission."); return true; } if(args.length<3){sender.sendMessage("§cUsage: /sg addowner <pattern> <player|g:group>"); return true;} int u=plugin.getRegionManager().addOwner(args[1].toLowerCase(), args[2]); if(u>0) sender.sendMessage("§a✓ Added owner to "+u+" regions."); else sender.sendMessage("§cNo regions matching."); return true; }
    private boolean cmdRemoveOwner(CommandSender sender, String[] args){ if (!sender.hasPermission("structureguard.admin")) { sender.sendMessage("§cNo permission."); return true; } if(args.length<3){sender.sendMessage("§cUsage: /sg removeowner <pattern> <player|g:group>"); return true;} int u=plugin.getRegionManager().removeOwner(args[1].toLowerCase(), args[2]); if(u>0) sender.sendMessage("§a✓ Removed owner from "+u+" regions."); else sender.sendMessage("§cNo matching regions."); return true; }
    private boolean cmdAddMember(CommandSender sender, String[] args){ if (!sender.hasPermission("structureguard.admin")) { sender.sendMessage("§cNo permission."); return true; } if(args.length<3){sender.sendMessage("§cUsage: /sg addmember <pattern> <player|g:group>"); return true;} int u=plugin.getRegionManager().addMember(args[1].toLowerCase(), args[2]); if(u>0) sender.sendMessage("§a✓ Added member to "+u+" regions."); else sender.sendMessage("§cNo regions matching."); return true; }
    private boolean cmdRemoveMember(CommandSender sender, String[] args){ if (!sender.hasPermission("structureguard.admin")) { sender.sendMessage("§cNo permission."); return true; } if(args.length<3){sender.sendMessage("§cUsage: /sg removemember <pattern> <player|g:group>"); return true;} int u=plugin.getRegionManager().removeMember(args[1].toLowerCase(), args[2]); if(u>0) sender.sendMessage("§a✓ Removed member from "+u+" regions."); else sender.sendMessage("§cNo matching regions."); return true; }

    private boolean cmdList(CommandSender sender, String[] args){
        if (!sender.hasPermission("structureguard.admin")) { sender.sendMessage("§cNo permission."); return true; }
        String pattern=args.length>=2?args[1].toLowerCase():"*"; int page=1; if(args.length>=3) try{page=Integer.parseInt(args[2]);}catch(Exception ignored){}
        List<StructureDatabase.StructureInfo> structures=plugin.getDatabase().getStructures(pattern); if(structures.isEmpty()){sender.sendMessage("§7No structures matching '"+pattern+"'"); return true;}
        int perPage=10; int totalPages=(structures.size()+perPage-1)/perPage; page=Math.max(1,Math.min(page,totalPages)); int start=(page-1)*perPage; int end=Math.min(start+perPage, structures.size());
        sender.sendMessage("§6§l=== Protected Structures ["+page+"/"+totalPages+"] ===");
        for(int i=start;i<end;i++){ StructureDatabase.StructureInfo info=structures.get(i); String status=info.hasRegion?"§a✓":"§7○"; String bb="["+info.minX+","+info.minZ+" -> "+info.maxX+","+info.maxZ+" Y"+info.minY+"->"+info.maxY+"]"; if(sender instanceof Player){ TextComponent line=new TextComponent(status+" §f"+info.type+" "+bb); line.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/minecraft:tp @s "+info.getCenterX()+" 100 "+info.getCenterZ())); line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§eClick to teleport\n§7"+info.world).create())); ((Player)sender).spigot().sendMessage(line);} else sender.sendMessage(status+" "+info.type+" "+bb+" in "+info.world); }
        sender.sendMessage("§7Total: "+structures.size()+" | §a✓ has region");
        return true;
    }

    private boolean cmdStatus(CommandSender sender, String[] args){
        if (!sender.hasPermission("structureguard.admin")) { sender.sendMessage("§cNo permission."); return true; }
        sender.sendMessage("§6§l=== StructureGuard Status (1.21 BB) ===");
        boolean wg=plugin.getRegionManager().isWorldGuardAvailable(); sender.sendMessage("§7WorldGuard: "+(wg?"§aAvailable":"§cNot found"));
        Map<String,ConfigManager.ProtectionRule> rules=plugin.getConfigManager().getProtectionRules(); int enabled=(int)rules.values().stream().filter(r->r.enabled).count(); sender.sendMessage("§7Rules: §f"+enabled+"§7 enabled / §f"+rules.size()+"§7 total (padding BB)");
        int total=plugin.getDatabase().getTotalCount(), prot=plugin.getDatabase().getProtectedCount(); sender.sendMessage("§7Database: §f"+total+"§7 structures (§a"+prot+"§7 protected) — BB corners");
        ChunkLoadListener l=plugin.getChunkLoadListener(); if(l!=null){ long proc=l.getProcessedChunkCount(), pend=l.getPendingCount(); sender.sendMessage("§7On-Demand: §aActive §7("+proc+" chunks"+(pend>0?", "+pend+" queued":"")+ ")"); } else sender.sendMessage("§7On-Demand: §cInactive");
        sender.sendMessage("§7Detection: §f"+plugin.getStructureFinder().getDetectionPathInfo());
        sender.sendMessage("§7Debug: "+(plugin.getConfigManager().isDebugMode()?"§aOn":"§7Off"));
        return true;
    }
    private boolean cmdReload(CommandSender sender, String[] args){ if(!sender.hasPermission("structureguard.admin")){sender.sendMessage("§cNo permission.");return true;} plugin.reload(); sender.sendMessage("§a✓ Reloaded. Old DB was wiped on startup (BB schema)."); return true; }
    private boolean cmdDebug(CommandSender sender, String[] args){ if(!sender.hasPermission("structureguard.admin")){sender.sendMessage("§cNo permission.");return true;} boolean cur=plugin.getConfigManager().isDebugMode(); plugin.getConfigManager().setDebugMode(!cur); sender.sendMessage("§7Debug: "+(!cur?"§aOn":"§cOff")); return true; }
    private boolean cmdProbe(CommandSender sender, String[] args){
        if (!sender.hasPermission("structureguard.admin")) { sender.sendMessage("§cNo permission."); return true; }
        if(!(sender instanceof Player)){ sender.sendMessage("§cPlayers only."); return true;}
        Player p=(Player)sender; int cx=p.getLocation().getBlockX()>>4, cz=p.getLocation().getBlockZ()>>4; if(args.length>=3) try{cx=Integer.parseInt(args[1]); cz=Integer.parseInt(args[2]);}catch(Exception ignored){}
        List<String> out=plugin.getStructureFinder().probeChunkVerbose(p.getWorld(), cx, cz); for(String s:out) sender.sendMessage(s); return true;
    }
    private boolean cmdMethods(CommandSender sender, String[] args){
        if (!sender.hasPermission("structureguard.admin")) { sender.sendMessage("§cNo permission."); return true; }
        if(!(sender instanceof Player)){ sender.sendMessage("§cConsole not supported"); return true;}
        Player p=(Player)sender; List<String> out=plugin.getStructureFinder().dumpChunkMethods(p.getWorld()); for(String s:out) sender.sendMessage(s); return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args){
        if(args.length==1) return Arrays.asList("find","listall","info","protect","unprotect","enable","disable","rules","flag","clearregions","resetworld","addowner","removeowner","addmember","removemember","list","status","reload","debug","probe","methods").stream().filter(s->s.startsWith(args[0].toLowerCase())).collect(java.util.stream.Collectors.toList());
        if(args.length==2 && (args[0].equalsIgnoreCase("protect")||args[0].equalsIgnoreCase("enable")||args[0].equalsIgnoreCase("flag")||args[0].equalsIgnoreCase("clearregions")||args[0].equalsIgnoreCase("list"))) { List<String> all=plugin.getStructureFinder().getAllStructureTypes(); List<String> res=new ArrayList<>(); String pref=args[1].toLowerCase(); for(String t:all) if(t.toLowerCase().startsWith(pref)) res.add(t); if(res.isEmpty()) res.add("<pattern>"); return res; }
        return Collections.emptyList();
    }
}
