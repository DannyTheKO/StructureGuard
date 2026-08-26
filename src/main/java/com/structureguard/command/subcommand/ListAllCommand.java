package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import com.structureguard.config.ProtectionRule;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ListAllCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public ListAllCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.listall"; }
    @Override public String usage() { return "/sg listall [page]"; }
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; }
        sender.sendMessage("§7Loading structure registry...");
        List<String> types = plugin.getStructureFinder().getAllStructureTypes();
        if (types.isEmpty()) { sender.sendMessage("§cNo structure types found."); return true; }
        int page=1; if(args.length>=2) try{page=Integer.parseInt(args[1]);}catch(Exception ignored){}
        int perPage=15; int totalPages=(int)Math.ceil(types.size()/(double)perPage); page=Math.max(1, Math.min(page,totalPages)); int start=(page-1)*perPage; int end=Math.min(start+perPage, types.size());
        sender.sendMessage("§6§l=== Structure Types ["+page+"/"+totalPages+"] ===");
        for(int i=start;i<end;i++){ String type=types.get(i); String ns=type.contains(":")?type.substring(0,type.indexOf(":")):"minecraft"; String color=ns.equals("minecraft")?"§e":"§d"; ProtectionRule rule=plugin.getConfigManager().getProtectionRule(type); String status=(rule!=null&&rule.enabled)?" §a✓":""; if(sender instanceof Player){ TextComponent line=new TextComponent(color+type+status); line.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sg enable "+type)); line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§7Click to enable\n§e/sg enable "+type).create())); ((Player)sender).spigot().sendMessage(line);} else sender.sendMessage(color+type+status); }
        sender.sendMessage("§7Total: §f"+types.size()+" §7| §a✓§7 = auto-protected");
        if(totalPages>1 && sender instanceof Player){ TextComponent nav=new TextComponent(""); if(page>1){TextComponent prev=new TextComponent("§a[« Prev] "); prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sg listall "+(page-1))); nav.addExtra(prev);} nav.addExtra(new TextComponent("§7Page "+page+"/"+totalPages+" ")); if(page<totalPages){TextComponent next=new TextComponent("§a[Next »]"); next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sg listall "+(page+1))); nav.addExtra(next);} ((Player)sender).spigot().sendMessage(nav); }
        return true;
    }
}
