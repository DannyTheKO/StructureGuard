package com.structureguard;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigManager {
    
    private final StructureGuardPlugin plugin;
    private boolean debugMode;
    private int defaultPadding;
    private boolean processExistingChunks;
    private Map<String, String> defaultFlags;
    private Set<String> disabledWorlds;
    
    private final Map<String, ProtectionRule> protectionRules = new HashMap<>();
    
    public ConfigManager(StructureGuardPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        
        debugMode = config.getBoolean("debug", false);
        if (config.contains("default-padding")) defaultPadding = config.getInt("default-padding", 5);
        else if (config.contains("default-radius")) defaultPadding = config.getInt("default-radius", 5);
        else defaultPadding = config.getInt("default-padding", 5);
        processExistingChunks = config.getBoolean("process-existing-chunks", true);
        
        disabledWorlds = new HashSet<>();
        List<String> disabledList = config.getStringList("disabled-worlds");
        for (String world : disabledList) disabledWorlds.add(world.toLowerCase());
        if (!disabledWorlds.isEmpty()) plugin.getLogger().info("Protection disabled in worlds: " + String.join(", ", disabledWorlds));
        
        defaultFlags = new HashMap<>();
        if (config.isConfigurationSection("default-flags")) {
            for (String key : config.getConfigurationSection("default-flags").getKeys(false)) {
                defaultFlags.put(key, config.getString("default-flags." + key));
            }
        }
        
        protectionRules.clear();
        if (config.isConfigurationSection("protected-structures")) {
            ConfigurationSection structures = config.getConfigurationSection("protected-structures");
            for (String patternKey : structures.getKeys(false)) {
                ConfigurationSection ruleSection = structures.getConfigurationSection(patternKey);
                if (ruleSection != null) {
                    String pattern = configKeyToPattern(patternKey);
                    boolean enabled = ruleSection.getBoolean("enabled", true);
                    int padding;
                    if (ruleSection.contains("padding")) padding = ruleSection.getInt("padding", defaultPadding);
                    else if (ruleSection.contains("radius")) padding = ruleSection.getInt("radius", defaultPadding);
                    else padding = defaultPadding;
                    int priority = ruleSection.getInt("priority", 10);
                    
                    Map<String, String> flags = new HashMap<>(defaultFlags);
                    if (ruleSection.isConfigurationSection("flags")) {
                        for (String flagKey : ruleSection.getConfigurationSection("flags").getKeys(false)) {
                            flags.put(flagKey, ruleSection.getString("flags." + flagKey));
                        }
                    }
                    
                    ProtectionRule rule = new ProtectionRule();
                    rule.pattern = pattern;
                    rule.enabled = enabled;
                    rule.padding = padding;
                    rule.priority = priority;
                    rule.flags = flags;
                    
                    protectionRules.put(pattern, rule);
                    debug("Loaded protection rule: " + pattern + " (enabled=" + enabled + ", padding=" + padding + ", priority=" + priority + ")");
                }
            }
        }
        
        plugin.getLogger().info("Loaded " + protectionRules.size() + " protection rules");
    }
    
    public ProtectionRule getProtectionRule(String structureType) {
        if (structureType == null) return null;
        if (protectionRules.containsKey(structureType)) return protectionRules.get(structureType);
        ProtectionRule bestMatch = null;
        int bestPriority = Integer.MIN_VALUE;
        for (Map.Entry<String, ProtectionRule> entry : protectionRules.entrySet()) {
            String pattern = entry.getKey();
            ProtectionRule rule = entry.getValue();
            if (matchesPattern(structureType, pattern)) {
                if (rule.priority > bestPriority || bestMatch == null) { bestMatch = rule; bestPriority = rule.priority; }
            }
        }
        return bestMatch;
    }
    
    private boolean matchesPattern(String structureType, String pattern) {
        if (pattern.equals("*")) return true;
        if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return structureType.matches(regex);
        }
        return pattern.equals(structureType);
    }
    
    public void addProtectionRule(ProtectionRule rule) {
        protectionRules.put(rule.pattern, rule);
        FileConfiguration config = plugin.getConfig();
        String path = "protected-structures." + patternToConfigKey(rule.pattern);
        config.set(path + ".enabled", rule.enabled);
        config.set(path + ".padding", rule.padding);
        config.set(path + ".radius", null);
        config.set(path + ".y-min", null);
        config.set(path + ".y-max", null);
        config.set(path + ".priority", rule.priority);
        for (Map.Entry<String, String> flag : rule.flags.entrySet()) config.set(path + ".flags." + flag.getKey(), flag.getValue());
        plugin.saveConfig();
    }
    
    public boolean removeProtectionRule(String pattern) {
        if (protectionRules.remove(pattern) != null) {
            FileConfiguration config = plugin.getConfig();
            config.set("protected-structures." + patternToConfigKey(pattern), null);
            plugin.saveConfig();
            return true;
        }
        return false;
    }
    
    public Map<String, ProtectionRule> getProtectionRules() { return new HashMap<>(protectionRules); }
    
    private String patternToConfigKey(String pattern) { return pattern; }
    
    private String configKeyToPattern(String configKey) {
        if (configKey.contains(":")) return configKey;
        if (configKey.contains("--")) return configKey.replace("--", ":");
        int underscoreIndex = configKey.indexOf('_');
        if (underscoreIndex > 0) return configKey.substring(0, underscoreIndex) + ":" + configKey.substring(underscoreIndex + 1);
        return configKey;
    }
    
    public boolean hasEnabledProtectionRules() {
        for (ProtectionRule rule : protectionRules.values()) if (rule.enabled) return true;
        return false;
    }
    
    public boolean isStructureProtected(String structureType) {
        ProtectionRule rule = getProtectionRule(structureType);
        return rule != null && rule.enabled;
    }
    
    public void debug(String message) { if (debugMode) plugin.getLogger().info("[DEBUG] " + message); }
    public boolean isDebugMode() { return debugMode; }
    public void setDebugMode(boolean debug) { this.debugMode = debug; plugin.getConfig().set("debug", debug); plugin.saveConfig(); }
    public int getDefaultPadding() { return defaultPadding; }
    @Deprecated public int getDefaultRadius() { return defaultPadding; }
    @Deprecated public int getDefaultYMin() { return -64; }
    @Deprecated public int getDefaultYMax() { return 320; }
    public boolean shouldProcessExistingChunks() { return processExistingChunks; }
    public boolean isWorldDisabled(String worldName) { return disabledWorlds.contains(worldName.toLowerCase()); }
    public Set<String> getDisabledWorlds() { return new HashSet<>(disabledWorlds); }
    public Map<String, String> getDefaultFlags() { return new HashMap<>(defaultFlags); }
    public int getScanChunksPerTick() { return plugin.getConfig().getInt("scan-chunks-per-tick", 512); }
    public boolean isStructureIgnored(String structureName) { return false; }
    
    public static class ProtectionRule {
        public String pattern;
        public boolean enabled = true;
        public int padding = 5;
        public int priority = 10;
        public Map<String, String> flags = new HashMap<>();
    }
}
