package com.structureguard.command;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public interface SgSubCommand {
    boolean execute(CommandSender sender, String[] args);
    default List<String> tabComplete(CommandSender sender, String[] args) { return Collections.emptyList(); }
    String permission();
    String usage();
}
