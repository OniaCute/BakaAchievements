package cc.oniacute.plugin.bakaachievements.util;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import org.bukkit.Bukkit;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * 异步执行工具——封装线程池和调度器。
 * <p>
 * 所有条件评估、数据读写等耗时操作都应通过此类调度，
 * 避免阻塞 Minecraft 主线程。
 * </p>
 */
public final class AsyncExecutor {

    private final BakaAchievements plugin;
    private ExecutorService threadPool;

    public AsyncExecutor(BakaAchievements plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化线程池。
     *
     * @param poolSize 线程池大小
     */
    public void init(int poolSize) {
        shutdown();
        threadPool = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "BakaAchievements-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 提交异步任务。
     *
     * @param task 任务
     */
    public void runAsync(Runnable task) {
        if (threadPool == null || threadPool.isShutdown()) {
            plugin.getLogger().warning("AsyncExecutor: 线程池未初始化，在主线程执行。");
            task.run();
            return;
        }
        threadPool.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "异步任务异常", e);
            }
        });
    }

    /**
     * 提交异步任务并返回 CompletableFuture。
     */
    public CompletableFuture<Void> runAsyncFuture(Runnable task) {
        return CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "异步任务异常", e);
            }
        }, threadPool != null ? threadPool : Runnable::run);
    }

    /**
     * 将 Runnable 调度到主线程执行。
     */
    public void runOnMainThread(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /**
     * 调度循环定时任务（tick）。
     *
     * @param task       任务
     * @param intervalTicks 间隔（tick）
     * @return 任务 ID
     */
    public int scheduleRepeating(Runnable task, long intervalTicks) {
        return Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, task, intervalTicks, intervalTicks
        ).getTaskId();
    }

    /**
     * 关闭线程池。
     */
    public void shutdown() {
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
