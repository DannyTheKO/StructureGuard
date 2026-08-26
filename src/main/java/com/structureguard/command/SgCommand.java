package com.structureguard.command;

import com.structureguard.StructureGuardPlugin;
import com.structureguard.command.subcommand.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;
import java.util.stream.Collectors;

public class SgCommand implements CommandExecutor, TabCompleter {
    private final StructureGuardPlugin plugin;
    private final Map<String, SgSubCommand> commands = new LinkedHashMap<>();

    public SgCommand(StructureGuardPlugin plugin) {
        this.plugin = plugin;
        register("find", new FindCommand(plugin));
        register("listall", new ListAllCommand(plugin));
        register("info", new InfoCommand(plugin));
        register("protect", new ProtectCommand(plugin));
        register("unprotect", new UnprotectCommand(plugin));
        register("enable", new EnableCommand(plugin));
        register("disable", new DisableCommand(plugin));
        register("rules", new RulesCommand(plugin));
        register("flag", new FlagCommand(plugin));
        register("clearregions", new ClearRegionsCommand(plugin));
        register("resetworld", new ResetWorldCommand(plugin));
        register("addowner", new AddOwnerCommand(plugin));
        register("removeowner", new RemoveOwnerCommand(plugin));
        register("addmember", new AddMemberCommand(plugin));
        register("removemember", new RemoveMemberCommand(plugin));
        register("list", new ListCommand(plugin));
        register("status", new StatusCommand(plugin));
        register("reload", new ReloadCommand(plugin));
        register("debug", new DebugCommand(plugin));
        register("probe", new ProbeCommand(plugin));
        register("methods", new MethodsCommand(plugin));
    }

    private void register(String name, SgSubCommand cmd) { commands.put(name, cmd); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { showHelp(sender); return true; }
        SgSubCommand sub = commands.get(args[0].toLowerCase());
        if (sub != null) return sub.execute(sender, args);
        sender.sendMessage("§cUnknown command. Use /sg for help.");
        return true;
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

    private static final Set<String> PATTERN_COMMANDS = Set.of(
            "protect","unprotect","enable","disable","flag","clearregions","list","find","addowner","removeowner","addmember","removemember"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return commands.keySet().stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        String subName = args[0].toLowerCase();
        SgSubCommand sub = commands.get(subName);
        if (sub != null) {
            List<String> custom = sub.tabComplete(sender, args);
            if (custom != null && !custom.isEmpty()) return custom;
        }
        if (args.length == 2 && PATTERN_COMMANDS.contains(subName)) {
            String pref = args[1].toLowerCase();
            List<String> res = new ArrayList<>();
            for (String preset : List.of("*", "minecraft:*", "*:village*")) {
                if (preset.toLowerCase().startsWith(pref)) res.add(preset);
            }
            try {
                List<String> all = plugin.getStructureFinder().getAllStructureTypes();
                for (String t : all) if (t.toLowerCase().startsWith(pref)) res.add(t);
            } catch (Exception ignored) {}
            if (res.isEmpty()) res.add("*");
            return res;
        }
        if (args.length == 3 && subName.equals("flag")) {
            String pref = args[2].toLowerCase();
            List<String> flags = plugin.getRegionManager().getAvailableFlags();
            if (flags.isEmpty()) flags = List.of("build","interact","pvp","use","entry");
            return flags.stream().filter(f -> f.toLowerCase().startsWith(pref)).collect(Collectors.toList());
        }
        if (args.length == 4 && subName.equals("flag")) {
            String pref = args[3].toLowerCase();
            return List.of("allow","deny","none").stream().filter(v -> v.startsWith(pref)).collect(Collectors.toList());
        }
        if (args.length == 3 && subName.equals("clearregions")) {
            String pref = args[2].toLowerCase();
            return org.bukkit.Bukkit.getWorlds().stream().map(w -> w.getName()).filter(n -> n.toLowerCase().startsWith(pref)).collect(Collectors.toList());
        }
        if (args.length == 2 && subName.equals("resetworld")) {
            String pref = args[1].toLowerCase();
            return org.bukkit.Bukkit.getWorlds().stream().map(w -> w.getName()).filter(n -> n.toLowerCase().startsWith(pref)).collect(Collectors.toList());
        }
        if (args.length == 3 && (subName.equals("protect") || subName.equals("enable"))) {
            String pref = args[2].toLowerCase();
            return List.of("5","10","15").stream().filter(v -> v.startsWith(pref)).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
