package com.structureguard.command.subcommand;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.SgSubCommand;
import com.structureguard.config.ProtectionRule;
import com.structureguard.listener.ChunkLoadListener;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class StatusCommand implements SgSubCommand {
    private final StructureGuardPlugin plugin;
    public StatusCommand(StructureGuardPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "structureguard.admin"; }
    @Override public String usage() { return "/sg status"; }
    @Override public boolean execute(CommandSender sender, String[] args){
        if (!sender.hasPermission(permission())) { sender.sendMessage("§cNo permission."); return true; }
        sender.sendMessage("§6§l=== StructureGuard Status (1.21 BB) ===");
        boolean wg=plugin.getRegionManager().isWorldGuardAvailable(); sender.sendMessage("§7WorldGuard: "+(wg?"§aAvailable":"§cNot found"));
        Map<String,ProtectionRule> rules=plugin.getConfigManager().getProtectionRules(); int enabled=(int)rules.values().stream().filter(r->r.enabled).count(); sender.sendMessage("§7Rules: §f"+enabled+"§7 enabled / §f"+rules.size()+"§7 total (padding BB)");
        int total=plugin.getDatabase().getTotalCount(), prot=plugin.getDatabase().getProtectedCount(); sender.sendMessage("§7Database: §f"+total+"§7 structures (§a"+prot+"§7 protected) — BB corners");
        ChunkLoadListener l=plugin.getChunkLoadListener(); if(l!=null){ long proc=l.getProcessedChunkCount(), pend=l.getPendingCount(); sender.sendMessage("§7On-Demand: §aActive §7("+proc+" chunks"+(pend>0?", "+pend+" queued":"")+ ")"); } else sender.sendMessage("§7On-Demand: §cInactive");
        sender.sendMessage("§7Detection: §f"+plugin.getStructureFinder().getDetectionPathInfo());
        sender.sendMessage("§7Debug: "+(plugin.getConfigManager().isDebugMode()?"§aOn":"§7Off"));
        return true;
    }
}
