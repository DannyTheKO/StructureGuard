package com.structureguard.util;

public final class PatternMatcher {
    private PatternMatcher() {}

    public static boolean matches(String type, String pattern) {
        if (pattern == null || type == null) return false;
        if (pattern.equals("*")) return true;
        if (pattern.equals(type)) return true;
        if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return type.matches(regex);
        }
        return false;
    }

    public static String wildcardToSql(String pattern) {
        return pattern.replace("*", "%");
    }
}
