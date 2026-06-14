package cc.oniacute.plugin.bakaachievements.hook;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * HeadDatabase 软依赖管理器。
 * <p>
 * 若服务器未安装 HeadDatabase，{@link #getHead(int)} 将降级返回
 * {@link Material#PLAYER_HEAD}，保证插件正常运行。
 * </p>
 */
public final class HdbHook {

    private static final int MAX_WARN_COUNT = 3;

    private final BakaAchievements plugin;
    private HeadDatabaseAPI api;
    private boolean enabled;
    private int failedCount;

    public HdbHook(BakaAchievements plugin) {
        this.plugin = plugin;
    }

    /**
     * 检测 HeadDatabase 插件并初始化 API 实例。
     * 应在 {@code onEnable()} 中调用。
     */
    public void init() {
        if (Bukkit.getPluginManager().isPluginEnabled("HeadDatabase")) {
            try {
                api = new HeadDatabaseAPI();
                enabled = true;
                plugin.getLogger().info("HeadDatabase 已挂载，头颅材质支持已启用。");
            } catch (Exception e) {
                enabled = false;
                plugin.getLogger().warning("HeadDatabase 初始化失败，头颅材质支持不可用: " + e.getMessage());
            }
        } else {
            enabled = false;
            plugin.getLogger().info("HeadDatabase 未安装，hdb-<id> 材质将降级为 PLAYER_HEAD。");
        }
    }

    /**
     * 返回 HeadDatabase 是否可用。
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取指定数字 ID 的头颅 {@link ItemStack}。
     * <ul>
     *   <li>若 HDB 未启用，直接返回 {@link Material#PLAYER_HEAD}。</li>
     *   <li>若 API 调用抛出任何异常，捕获并记录警告（最多 {@value MAX_WARN_COUNT} 次），
     *       然后返回 {@link Material#PLAYER_HEAD} 作为降级值。</li>
     * </ul>
     *
     * @param id HeadDatabase 头颅数字 ID
     * @return 对应的头颅物品，失败时为普通 {@link Material#PLAYER_HEAD}
     */
    public ItemStack getHead(int id) {
        if (!enabled) {
            return new ItemStack(Material.PLAYER_HEAD);
        }
        try {
            ItemStack head = api.getItemHead(String.valueOf(id));
            if (head != null) {
                return head;
            }
            warnOnce("HeadDatabase 返回了空物品，id=" + id + "，已降级为 PLAYER_HEAD。");
        } catch (Exception e) {
            warnOnce("获取 HDB 头颅失败，id=" + id + ": " + e.getMessage());
        }
        return new ItemStack(Material.PLAYER_HEAD);
    }

    // ──────────────────────────────────────────────────────
    //  内部工具
    // ──────────────────────────────────────────────────────

    private void warnOnce(String message) {
        if (failedCount < MAX_WARN_COUNT) {
            plugin.getLogger().warning(message);
            failedCount++;
            if (failedCount == MAX_WARN_COUNT) {
                plugin.getLogger().warning("HeadDatabase 获取头颅已连续失败 " + MAX_WARN_COUNT
                        + " 次，后续警告将被抑制。");
            }
        }
    }
}
