package cc.oniacute.plugin.bakaachievements.hook;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.api.BakaAchievementsApi;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * BakaAchievements 的 PlaceholderAPI 扩展。
 */
public final class BakaAchievementsExpansion extends PlaceholderExpansion {

    private final BakaAchievements plugin;

    public BakaAchievementsExpansion(BakaAchievements plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "bakaachievements";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String lower = params.toLowerCase();

        if ("version".equals(lower)) {
            return getVersion();
        }
        if ("placeholderapi".equals(lower)) {
            return String.valueOf(plugin.getPapiHook().isEnabled());
        }
        if ("count_total".equals(lower)) {
            return String.valueOf(plugin.getApi().listAchievementPaths().size());
        }
        if ("count_done".equals(lower)) {
            return String.valueOf(plugin.getApi().getUnlockedCount(player.getUniqueId()));
        }
        if (lower.startsWith("unlocked_")) {
            String nodePath = params.substring("unlocked_".length());
            return String.valueOf(plugin.getApi().isUnlocked(player.getUniqueId(), nodePath));
        }

        return null;
    }
}
