package cc.oniacute.plugin.bakaachievements.achievement;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.condition.Condition;
import cc.oniacute.plugin.bakaachievements.achievement.condition.ConditionEvaluator;
import cc.oniacute.plugin.bakaachievements.api.event.AchievementUnlockEvent;
import cc.oniacute.plugin.bakaachievements.chat.ChatBroadcastService;
import cc.oniacute.plugin.bakaachievements.storage.PlayerDataStorage;
import cc.oniacute.plugin.bakaachievements.util.AsyncExecutor;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * 进度服务——负责成就条件的评估与解锁。
 * <p>
 * 评估流程：主线程解析 PAPI → 异步线程逻辑判断 → 主线程解锁+广播。
 * 线程安全：每玩家持有一个 {@link ReentrantLock} 避免重复评估。
 * </p>
 */
public final class ProgressService {

    private final BakaAchievements plugin;
    private final AchievementRegistry registry;
    private final PlayerDataStorage storage;
    private final AsyncExecutor asyncExecutor;
    private ChatBroadcastService chatBroadcastService;

    /** 每玩家的评估锁，避免同一玩家并发评估 */
    private final ConcurrentHashMap<UUID, ReentrantLock> evaluationLocks = new ConcurrentHashMap<>();

    public ProgressService(BakaAchievements plugin, AchievementRegistry registry,
                           PlayerDataStorage storage, AsyncExecutor asyncExecutor) {
        this.plugin = plugin;
        this.registry = registry;
        this.storage = storage;
        this.asyncExecutor = asyncExecutor;
    }

    public void setChatBroadcastService(ChatBroadcastService chatBroadcastService) {
        this.chatBroadcastService = chatBroadcastService;
    }

    /**
     * 评估指定玩家的所有自动成就条件。
     * <p>
     * 安全流程：主线程解析 PAPI → 异步评估 → 主线程解锁。
     * PlaceholderAPI.setPlaceholders() 不是线程安全的，必须在主线程调用。
     * </p>
     */
    public void evaluateAll(Player player) {
        UUID uuid = player.getUniqueId();
        ReentrantLock lock = evaluationLocks.computeIfAbsent(uuid, k -> new ReentrantLock());

        if (!lock.tryLock()) return;

        // 第一步：在主线程批量解析所有 PAPI 条件
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                PlayerAchievementData data = storage.getCached(uuid);
                if (data == null) {
                    lock.unlock();
                    return;
                }

                // 收集所有需要评估的成就 + 预解析 PAPI
                List<PendingEvaluation> pending = new ArrayList<>();
                for (Map.Entry<String, AchievementNode> entry : registry.getAllNodes().entrySet()) {
                    if (entry.getValue().nodeType() != AchievementNode.NodeType.ACHIEVEMENT) continue;
                    Achievement ach = (Achievement) entry.getValue();
                    if (!ach.auto()) continue;
                    if (data.isUnlocked(ach.nodePath())) continue;
                    if (ach.conditionGroup().isEmpty()) continue;

                    // 在主线程解析所有 PAPI 占位符
                    List<ConditionResolved> resolvedConditions = new ArrayList<>();
                    boolean allResolved = true;
                    for (Condition cond : ach.conditionGroup().conditions()) {
                        String t = resolvePapi(player, cond.target());
                        String c = resolvePapi(player, cond.current());
                        if (t == null || c == null) { allResolved = false; break; }
                        resolvedConditions.add(new ConditionResolved(cond, t, c));
                    }
                    if (!allResolved || resolvedConditions.isEmpty()) continue;

                    pending.add(new PendingEvaluation(ach, resolvedConditions));
                }

                // 第二步：异步评估
                asyncExecutor.runAsync(() -> {
                    try {
                        for (PendingEvaluation pe : pending) {
                            if (data.isUnlocked(pe.achievement.nodePath())) continue;

                            boolean passed = true;
                            for (ConditionResolved cr : pe.resolvedConditions) {
                                if (!ConditionEvaluator.evaluateResolved(
                                        player, cr.condition, cr.target, cr.current)) {
                                    passed = false;
                                    break;
                                }
                            }

                            if (passed) {
                                asyncExecutor.runOnMainThread(() -> unlock(player, pe.achievement));
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "玩家 " + uuid + " 的成就评估异常", e);
                    } finally {
                        lock.unlock();
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "PAPI 解析异常", e);
                lock.unlock();
            }
        });
    }

    /**
     * 评估单个成就的条件（主线程安全）。
     */
    public boolean evaluateSingle(Player player, Achievement achievement) {
        if (achievement.conditionGroup().isEmpty()) return false;
        for (Condition cond : achievement.conditionGroup().conditions()) {
            String t = resolvePapi(player, cond.target());
            String c = resolvePapi(player, cond.current());
            if (t == null || c == null) return false;
            if (!ConditionEvaluator.evaluateResolved(player, cond, t, c)) return false;
        }
        return true;
    }

    // ── 解锁逻辑 ──────────────────────────────────────────

    private void unlock(Player player, Achievement achievement) {
        AchievementUnlockEvent event = new AchievementUnlockEvent(player, achievement.nodePath());
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) return;

        PlayerAchievementData data = storage.getCached(player.getUniqueId());
        if (data == null) return;

        // 若已解锁则跳过（防止竞争条件）
        if (data.isUnlocked(achievement.nodePath())) return;

        data.setStatus(achievement.nodePath(),
                new PlayerAchievementData.AchievementStatus(true, System.currentTimeMillis()));

        storage.save(player.getUniqueId(), data);

        if (chatBroadcastService != null) {
            chatBroadcastService.broadcast(player, achievement.nodePath(), achievement.display());
        }
    }

    /** 管理员强制设置成就状态（不触发评估，不广播） */
    public void forceSet(Player player, String nodePath, boolean unlocked) {
        PlayerAchievementData data = storage.getCached(player.getUniqueId());
        if (data == null) return;

        data.setStatus(nodePath,
                new PlayerAchievementData.AchievementStatus(unlocked,
                        unlocked ? System.currentTimeMillis() : -1L));
        storage.save(player.getUniqueId(), data);
    }

    // ── PAPI 解析（必须主线程） ──────────────────────────

    /** 在主线程安全解析 PAPI */
    private static String resolvePapi(Player player, String text) {
        if (text == null || text.isEmpty() || !text.contains("%")) return text;
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                return PlaceholderAPI.setPlaceholders(player, text);
            }
        } catch (Exception ignored) {}
        return text;
    }

    // ── 内部记录类型 ─────────────────────────────────────

    private record PendingEvaluation(Achievement achievement, List<ConditionResolved> resolvedConditions) {}
    private record ConditionResolved(Condition condition, String target, String current) {}
}
