package cc.oniacute.plugin.bakaachievements.gui;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 管理每玩家的菜单自动刷新定时任务。
 * <p>
 * 每个打开 GUI 的玩家会被分配一个定时刷新任务，
 * 间隔由 {@code config.yml} 中的 {@code gui.refresh-interval-ticks} 决定（默认 600 ticks = 30 秒）。
 * 玩家关闭菜单时任务自动取消；插件关闭时通过 {@link #cancelAll()} 统一清理。
 * </p>
 */
public final class MenuRefreshScheduler {

    /** 玩家 UUID → 刷新任务 */
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();
    private final BakaAchievements plugin;

    /**
     * 构造刷新调度器。
     *
     * @param plugin 插件主实例
     */
    public MenuRefreshScheduler(BakaAchievements plugin) {
        this.plugin = plugin;
    }

    /**
     * 为玩家安排刷新任务。
     * <p>
     * 先取消该玩家已有的旧任务（避免任务堆积），
     * 然后以固定间隔安排新的刷新任务。
     * 若配置的刷新间隔 ≤ 0 则不安排。
     * </p>
     *
     * @param player   玩家
     * @param runnable 刷新逻辑（在主线程执行）
     */
    public void schedule(Player player, Runnable runnable) {
        cancel(player);
        long interval = plugin.getConfigManager().getGuiRefreshIntervalTicks();
        if (interval <= 0) return;

        try {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    runnable,
                    interval,
                    interval
            );
            tasks.put(player.getUniqueId(), task);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "无法为玩家 " + player.getName() + " 安排 GUI 刷新任务: " + e.getMessage());
        }
    }

    /**
     * 取消指定玩家的刷新任务。
     *
     * @param player 玩家
     */
    public void cancel(Player player) {
        BukkitTask task = tasks.remove(player.getUniqueId());
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception ignored) {
                // 任务可能已经自然结束
            }
        }
    }

    /**
     * 取消所有刷新任务。
     * <p>
     * 插件关闭时由 {@link BakaAchievements#onDisable()} 调用。
     * </p>
     */
    public void cancelAll() {
        for (Map.Entry<UUID, BukkitTask> entry : tasks.entrySet()) {
            try {
                entry.getValue().cancel();
            } catch (Exception ignored) {
                // 部分任务可能已自然结束
            }
        }
        tasks.clear();
    }

    /**
     * 立即触发一次刷新（然后重置计时）。
     * <p>
     * 取消当前定时任务；实际刷新由调用方（{@link MenuController#refreshCurrentMenu(Player)}）执行。
     * 调用方在完成刷新后应重新调用 {@link #schedule(Player, Runnable)} 以恢复定时刷新。
     * </p>
     *
     * @param player 玩家
     */
    public void triggerNow(Player player) {
        cancel(player);
    }
}
