package com.structureguard;

import com.structureguard.command.SgCommand;
import com.structureguard.config.ConfigManager;
import com.structureguard.database.StructureDatabase;
import com.structureguard.listener.ChunkLoadListener;
import com.structureguard.region.RegionManager;
import com.structureguard.structure.StructureFinder;
import org.bukkit.plugin.java.JavaPlugin;

public class StructureGuardPlugin extends JavaPlugin {
    private StructureDatabase database;
    private StructureFinder structureFinder;
    private RegionManager regionManager;
    private ConfigManager configManager;
    private ChunkLoadListener chunkLoadListener;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        database = new StructureDatabase(this);
        structureFinder = new StructureFinder(this);
        try {
            regionManager = new RegionManager(this);
        } catch (NoClassDefFoundError | Exception e) {
            getLogger().warning("Could not initialize WorldGuard integration: " + e.getMessage());
            getLogger().warning("Region protection features will be disabled.");
            regionManager = null;
        }
        if (regionManager != null && regionManager.isWorldGuardAvailable()) {
            chunkLoadListener = new ChunkLoadListener(this);
            getServer().getPluginManager().registerEvents(chunkLoadListener, this);
            getLogger().info("On-demand structure protection enabled!");
        } else {
            getLogger().warning("WorldGuard not found! On-demand protection disabled.");
        }
        SgCommand sgCommand = new SgCommand(this);
        getCommand("structureguard").setExecutor(sgCommand);
        getCommand("structureguard").setTabCompleter(sgCommand);
        getLogger().info("StructureGuard enabled!");
        if (regionManager == null || !regionManager.isWorldGuardAvailable()) {
            getLogger().warning("WorldGuard not found! Region protection features disabled.");
        }
    }

    @Override
    public void onDisable() {
        if (chunkLoadListener != null) chunkLoadListener.shutdown();
        if (database != null) database.close();
        getLogger().info("StructureGuard disabled!");
    }

    public void reload() {
        reloadConfig();
        configManager = new ConfigManager(this);
        if (regionManager != null) regionManager.syncFromConfig();
    }

    public StructureDatabase getDatabase() { return database; }
    public StructureFinder getStructureFinder() { return structureFinder; }
    public RegionManager getRegionManager() { return regionManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public ChunkLoadListener getChunkLoadListener() { return chunkLoadListener; }
}
