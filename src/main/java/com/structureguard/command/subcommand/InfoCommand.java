package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import com.structureguard.config.ProtectionRule;
import com.structureguard.database.model.StructureInfo;
import com.structureguard.structure.model.StructureResult;
import com.structureguard.util.PatternMatcher;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InfoCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public InfoCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.info"; }
    @Override public String usage() { return "/sg info"; }
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("§cPlayers only."); return true; }
        Player player=(Player)sender; Location loc=player.getLocation(); int chunkX=loc.getBlockX()>>4, chunkZ=loc.getBlockZ()>>4;
        player.sendMessage("§6§l=== Structure Info (BB) ==="); player.sendMessage("§7Location: §f"+loc.getBlockX()+", "+loc.getBlockY()+", "+loc.getBlockZ()); player.sendMessage("§7Chunk: §f"+chunkX+", "+chunkZ);
        Map<String, StructureResult> uniq=new LinkedHashMap<>();
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++){ List<StructureResult> list=plugin.getStructureFinder().getStructuresSpanningChunk(player.getWorld(), chunkX+dx, chunkZ+dz); for(StructureResult s:list){ String key=s.structureType+"@"+s.chunkX+","+s.chunkZ; if(!uniq.containsKey(key)) uniq.put(key,s);} }
        if(!uniq.isEmpty()){ player.sendMessage("§7Structures in this area:"); for(StructureResult s:uniq.values()){ boolean isProtected=plugin.getDatabase().isStructureProtected(player.getWorld().getName(), s.structureType, s.chunkX, s.chunkZ); String status=isProtected?" §a(protected)":" §7(unprotected)"; double dist=Math.sqrt(Math.pow(s.getCenterX()-loc.getBlockX(),2)+Math.pow(s.getCenterZ()-loc.getBlockZ(),2)); String distStr=" §8("+String.format("%.0f",dist)+"b to center)"; String bb=" §8["+s.minX+","+s.minZ+" -> "+s.maxX+","+s.maxZ+" Y"+s.minY+"->"+s.maxY+"]"; player.sendMessage("  §e"+s.structureType+status+distStr+bb); if(!isProtected && findMatchingRule(s.structureType)!=null){ TextComponent link=new TextComponent("    §a[Click to Protect]"); link.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sg protect "+s.structureType)); link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§aProtect this structure").create())); player.spigot().sendMessage(link);} } } else player.sendMessage("§7No structures detected in this area.");
        StructureInfo nearest=plugin.getDatabase().getNearestStructure(player.getWorld().getName(), loc.getBlockX(), loc.getBlockZ(), 500);
        if(nearest!=null){ double dist=Math.sqrt(Math.pow(nearest.getCenterX()-loc.getX(),2)+Math.pow(nearest.getCenterZ()-loc.getZ(),2)); player.sendMessage("§7Nearest in DB: §e"+nearest.type+" §7("+String.format("%.0f",dist)+"b) ["+nearest.minX+","+nearest.minZ+" -> "+nearest.maxX+","+nearest.maxZ+"]"); if(nearest.hasRegion) player.sendMessage("§7Region: §f"+nearest.regionId); }
        return true;
    }
    private ProtectionRule findMatchingRule(String t){ for(Map.Entry<String,ProtectionRule> e:plugin.getConfigManager().getProtectionRules().entrySet()) if(PatternMatcher.matches(t,e.getKey())) return e.getValue(); return null; }
}
