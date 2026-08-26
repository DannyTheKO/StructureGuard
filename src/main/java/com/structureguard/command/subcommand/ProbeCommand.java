package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ProbeCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public ProbeCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg probe [chunkX chunkZ]"; }
    @Override public boolean execute(CommandSender sender, String[] args){
        if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; }
        if(!(sender instanceof Player)){ sender.sendMessage("§cPlayers only."); return true;}
        Player p=(Player)sender; int cx=p.getLocation().getBlockX()>>4, cz=p.getLocation().getBlockZ()>>4; if(args.length>=3) try{cx=Integer.parseInt(args[1]); cz=Integer.parseInt(args[2]);}catch(Exception ignored){}
        List<String> out=plugin.getStructureFinder().probeChunkVerbose(p.getWorld(), cx, cz); for(String s:out) sender.sendMessage(s); return true;
    }
}
