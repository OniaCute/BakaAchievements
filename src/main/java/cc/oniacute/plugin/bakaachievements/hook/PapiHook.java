package cc.oniacute.plugin.bakaachievements.hook;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import org.bukkit.Bukkit;

/**
 * PlaceholderAPI 软依赖管理器。
 */
public final class PapiHook {

    private final BakaAchievements plugin;
    private BakaAchievementsExpansion expansion;
    private boolean enabled;

    public PapiHook(BakaAchievements plugin) {
        this.plugin = plugin;
    }

    public void tryRegister() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            plugin.getLogger().warning("PlaceholderAPI 未安装，占位符功能不可用。");
            enabled = false;
            return;
        }

        try {
            expansion = new BakaAchievementsExpansion(plugin);
            expansion.register();
            enabled = true;
            plugin.getLogger().info("PlaceholderAPI 扩展已注册。");
        } catch (Exception e) {
            plugin.getLogger().severe("PlaceholderAPI 扩展注册失败: " + e.getMessage());
            enabled = false;
        }
    }

    public void unregister() {
        if (expansion != null && expansion.isRegistered()) {
            expansion.unregister();
        }
        enabled = false;
        expansion = null;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
