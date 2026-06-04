package cc.oniacute.plugin.bakaachievements.hook;

import org.bukkit.entity.Player;

/**
 * 权限解析器——仅通过 Bukkit Permission API 判断权限。
 * <p>
 * 由于 LuckPerms 会自动注入 Bukkit 权限系统，
 * 此处无需任何特殊处理即可兼容 LP。
 * </p>
 */
public final class PermissionResolver {

    private PermissionResolver() {}

    /**
     * 检查玩家是否拥有指定权限节点。
     *
     * @param player 玩家
     * @param node   权限节点（如 {@code bakaachievements.category.test.cus_1}）
     * @return {@code true} 表示拥有权限
     */
    public static boolean has(Player player, String node) {
        return player.hasPermission(node);
    }
}
