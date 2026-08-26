package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class MethodsCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public MethodsCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg methods"; }
    @Override public boolean execute(CommandSender sender, String[] args){
        if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; }
        if(!(sender instanceof Player)){ sender.sendMessage("§cConsole not supported"); return true;}
        Player p=(Player)sender; List<String> out=plugin.getStructureFinder().dumpChunkMethods(p.getWorld()); for(String s:out) sender.sendMessage(s); return true;
    }
}
