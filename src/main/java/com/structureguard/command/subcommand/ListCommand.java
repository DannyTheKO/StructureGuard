package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import com.structureguard.database.model.StructureInfo;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ListCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public ListCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg list <pattern> [page]"; }
    @Override public boolean execute(CommandSender sender, String[] args){
        if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; }
        String pattern=args.length>=2?args[1].toLowerCase():"*"; int page=1; if(args.length>=3) try{page=Integer.parseInt(args[2]);}catch(Exception ignored){}
        List<StructureInfo> structures=plugin.getDatabase().getStructures(pattern); if(structures.isEmpty()){sender.sendMessage("§7No structures matching '"+pattern+"'"); return true;}
        int perPage=10; int totalPages=(structures.size()+perPage-1)/perPage; page=Math.max(1,Math.min(page,totalPages)); int start=(page-1)*perPage; int end=Math.min(start+perPage, structures.size());
        sender.sendMessage("§6§l=== Protected Structures ["+page+"/"+totalPages+"] ===");
        for(int i=start;i<end;i++){ StructureInfo info=structures.get(i); String status=info.hasRegion?"§a✓":"§7○"; String bb="["+info.minX+","+info.minZ+" -> "+info.maxX+","+info.maxZ+" Y"+info.minY+"->"+info.maxY+"]"; if(sender instanceof Player){ TextComponent line=new TextComponent(status+" §f"+info.type+" "+bb); line.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/minecraft:tp @s "+info.getCenterX()+" 100 "+info.getCenterZ())); line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§eClick to teleport\n§7"+info.world).create())); ((Player)sender).spigot().sendMessage(line);} else sender.sendMessage(status+" "+info.type+" "+bb+" in "+info.world); }
        sender.sendMessage("§7Total: "+structures.size()+" | §a✓ has region");
        return true;
    }
}
