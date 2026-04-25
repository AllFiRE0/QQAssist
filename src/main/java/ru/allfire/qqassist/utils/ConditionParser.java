package ru.allfire.qqassist.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ConditionParser {

    public static boolean evaluate(Player player, String condition) {
        if (condition == null || condition.isEmpty()) return true;

        condition = PlaceholderAPI.setPlaceholders(player, condition);

        condition = condition.replace("&&", "&").replace("||", "|");

        if (condition.contains(">=")) {
            String[] parts = condition.split(">=");
            return parseDouble(parts[0]) >= parseDouble(parts[1]);
        }
        if (condition.contains("<=")) {
            String[] parts = condition.split("<=");
            return parseDouble(parts[0]) <= parseDouble(parts[1]);
        }
        if (condition.contains("!=")) {
            String[] parts = condition.split("!=");
            return !parts[0].trim().equalsIgnoreCase(parts[1].trim());
        }
        if (condition.contains("==")) {
            String[] parts = condition.split("==");
            return parts[0].trim().equalsIgnoreCase(parts[1].trim());
        }
        if (condition.contains(">")) {
            String[] parts = condition.split(">");
            return parseDouble(parts[0]) > parseDouble(parts[1]);
        }
        if (condition.contains("<")) {
            String[] parts = condition.split("<");
            return parseDouble(parts[0]) < parseDouble(parts[1]);
        }
        if (condition.contains("=")) {
            String[] parts = condition.split("=");
            return parts[0].trim().equalsIgnoreCase(parts[1].trim());
        }

        return !condition.equalsIgnoreCase("false") && !condition.equals("0");
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
