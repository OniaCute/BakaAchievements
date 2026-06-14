package cc.oniacute.plugin.bakaachievements.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Centralizes PlaceholderAPI access behind a small main-thread-safe facade.
 */
public final class PlaceholderResolver {

    public String resolve(Player player, String text) {
        if (text == null || text.isEmpty() || !text.contains("%")) {
            return text != null ? text : "";
        }
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            }
        } catch (Exception ignored) {
            // Keep placeholder resolution best-effort for compatibility.
        }
        return text;
    }
}
