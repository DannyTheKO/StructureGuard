package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
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

import java.util.List;

public class FindCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public FindCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.find"; }
    @Override public String usage() { return "/sg find <structure-name>"; }
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length < 2) { sender.sendMessage("§cUsage: " + usage()); return true; }
        if (!(sender instanceof Player)) { sender.sendMessage("§cPlayers only."); return true; }
        Player player = (Player) sender;
        String structureType = args[1].toLowerCase();
        String pattern = structureType.contains(":") ? structureType : "*:*" + structureType + "*";
        player.sendMessage("§7Searching for " + structureType + " in nearby chunks...");
        int chunkX = player.getLocation().getBlockX() >> 4;
        int chunkZ = player.getLocation().getBlockZ() >> 4;
        int searchRadius = 4;
        StructureResult nearest = null; double nearestDist = Double.MAX_VALUE;
        for (int dx=-searchRadius; dx<=searchRadius; dx++) for (int dz=-searchRadius; dz<=searchRadius; dz++) {
            List<StructureResult> results = plugin.getStructureFinder().getStructuresInChunk(player.getWorld(), chunkX+dx, chunkZ+dz);
            for (StructureResult s : results) if (PatternMatcher.matches(s.structureType, pattern)) {
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
        List<StructureInfo> list = plugin.getDatabase().getStructuresOfType(player.getWorld().getName(), pattern);
        if (list.isEmpty()) return null;
        int px = player.getLocation().getBlockX(), pz = player.getLocation().getBlockZ();
        StructureInfo best=null; double bestDist=Double.MAX_VALUE;
        for (StructureInfo s : list) { double d=Math.sqrt(Math.pow(s.getCenterX()-px,2)+Math.pow(s.getCenterZ()-pz,2)); if (d<bestDist){bestDist=d; best=s;} }
        if (best!=null && bestDist<=10000) return new Location(player.getWorld(), best.getCenterX(), best.getCenterY(), best.getCenterZ());
        return null;
    }
}
