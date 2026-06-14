package cc.oniacute.plugin.bakaachievements.gui;

import cc.oniacute.plugin.bakaachievements.achievement.PlayerAchievementData;

import java.util.UUID;

/**
 * Current GUI navigation state for one viewer.
 */
public record MenuSession(
        MenuType type,
        String path,
        int page,
        String source,
        UUID targetPlayer,
        String targetName,
        PlayerAchievementData targetData
) {
    public enum MenuType {
        CATEGORY,
        SEARCH,
        CHECK
    }

    public MenuSession withPage(int newPage) {
        return new MenuSession(type, path, newPage, source, targetPlayer, targetName, targetData);
    }
}
