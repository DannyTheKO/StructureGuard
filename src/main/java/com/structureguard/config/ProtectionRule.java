package com.structureguard.config;

import java.util.HashMap;
import java.util.Map;

public class ProtectionRule {
    public String pattern;
    public boolean enabled = true;
    public int padding = 5;
    public int priority = 10;
    public Map<String, String> flags = new HashMap<>();
}
