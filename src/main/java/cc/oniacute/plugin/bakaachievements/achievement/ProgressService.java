package cc.oniacute.plugin.bakaachievements.achievement;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.command.AchievementCommandRunner;
import cc.oniacute.plugin.bakaachievements.achievement.condition.Condition;
import cc.oniacute.plugin.bakaachievements.achievement.condition.ConditionEvaluator;
import cc.oniacute.plugin.bakaachievements.api.event.AchievementUnlockEvent;
import cc.oniacute.plugin.bakaachievements.api.event.AchievementUpdateEvent;
import cc.oniacute.plugin.bakaachievements.storage.PlayerDataStorage;
import cc.oniacute.plugin.bakaachievements.util.AsyncExecutor;
import cc.oniacute.plugin.bakaachievements.util.PlaceholderResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 进度服务——负责成就条件的评估与解锁。
 * <p>
 * 评估流程：主线程解析 PAPI → 异步线程逻辑判断 → 主线程批量解锁+广播。
 * 线程安全：每玩家通过 {@link ConcurrentHashMap#putIfAbsent} 标记防重入。
 * </p>
 */
public final class ProgressService {

    private final BakaAchievements plugin;
    private final AchievementRegistry registry;
    private final PlayerDataStorage storage;
    private final AsyncExecutor asyncExecutor;
    private final PlaceholderResolver placeholderResolver;
    private AchievementCommandRunner commandRunner;

    /** 每玩家评估中标记，避免同一玩家并发评估（跨线程安全） */
    private final ConcurrentHashMap<UUID, Boolean> evaluating = new ConcurrentHashMap<>();

    public ProgressService(BakaAchievements plugin, AchievementRegistry registry,
                           PlayerDataStorage storage, AsyncExecutor asyncExecutor) {
        this.plugin = plugin;
        this.registry = registry;
        this.storage = storage;
        this.asyncExecutor = asyncExecutor;
        this.placeholderResolver = plugin.getServices().placeholderResolver();
    }

    public void setCommandRunner(AchievementCommandRunner runner) {
        this.commandRunner = runner;
    }

    /**
     * 评估指定玩家的所有自动成就条件。
     * <p>
     * 安全流程：主线程解析 PAPI → 异步评估收集待解锁列表
     * → 主线程批量解锁（完成时清除标记）。
     * </p>
     */
    public void evaluateAll(Player player) {
        UUID uuid = player.getUniqueId();

        // 防重入：若已在评估中则跳过
        if (evaluating.putIfAbsent(uuid, true) != null) return;

        // 第一步：在主线程批量解析所有 PAPI 条件
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                PlayerAchievementData data = storage.getCached(uuid);
                if (data == null) {
                    evaluating.remove(uuid);
                    return;
                }

                // 收集所有需要评估的成就 + 预解析 PAPI
                List<PendingEvaluation> pending = new ArrayList<>();
                for (Map.Entry<String, AchievementNode> entry : registry.getAllNodes().entrySet()) {
                    var nt = entry.getValue().nodeType();
                    if (nt != AchievementNode.NodeType.ACHIEVEMENT && nt != AchievementNode.NodeType.MIXED) continue;
                    var node = entry.getValue();
                    if (!node.auto()) continue;
                    if (data.isUnlocked(node.nodePath())) continue;
                    if (node.conditionGroup().isEmpty()) continue;

                    // 在主线程解析所有 PAPI 占位符
                    List<ConditionResolved> resolvedConditions = new ArrayList<>();
                    boolean allResolved = true;
                    for (Condition cond : node.conditionGroup().conditions()) {
                        String t = resolvePapi(player, cond.target());
                        String c = resolvePapi(player, cond.current());
                        if (t == null || c == null) { allResolved = false; break; }
                        resolvedConditions.add(new ConditionResolved(cond, t, c));
                    }
                    if (!allResolved || resolvedConditions.isEmpty()) continue;

                    pending.add(new PendingEvaluation(node, resolvedConditions));
                }

                // 第二步：异步评估，收集所有 passed=true 的成就
                asyncExecutor.runAsync(() -> {
                    try {
                        List<AchievementNode> toUnlock = new ArrayList<>();
                        for (PendingEvaluation pe : pending) {
                            if (data.isUnlocked(pe.node.nodePath())) continue;

                            boolean passed = true;
                            for (ConditionResolved cr : pe.resolvedConditions) {
                                if (!ConditionEvaluator.evaluateResolved(
                                        player, cr.condition(), cr.target(), cr.current())) {
                                    passed = false;
                                    break;
                                }
                            }
                            if (passed) toUnlock.add(pe.node);
                        }

                        if (toUnlock.isEmpty()) {
                            evaluating.remove(uuid);
                        } else {
                            // 在主线程批量解锁，完成后清除标记
                            asyncExecutor.runOnMainThread(() -> {
                                try {
                                    for (AchievementNode node : toUnlock) {
                                        unlock(player, node);
                                    }
                                } finally {
                                    evaluating.remove(uuid);
                                }
                            });
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "玩家 " + uuid + " 的成就评估异常", e);
                        evaluating.remove(uuid);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "PAPI 解析异常", e);
                evaluating.remove(uuid);
            }
        });
    }

    /**
     * 评估单个成就的条件（主线程安全）。
     */
    public boolean evaluateSingle(Player player, AchievementNode node) {
        if (node.conditionGroup().isEmpty()) return false;
        for (Condition cond : node.conditionGroup().conditions()) {
            String t = resolvePapi(player, cond.target());
            String c = resolvePapi(player, cond.current());
            if (t == null || c == null) return false;
            if (!ConditionEvaluator.evaluateResolved(player, cond, t, c)) return false;
        }
        return true;
    }

    /**
     * 尝试立即完成成就（GUI 左键点击触发）。
     * <p>
     * 评估条件 → 若全部满足且未解锁 → 解锁 + 广播 + 执行命令。
     * 必须在主线程调用。
     * </p>
     *
     * @return {@code true} 表示成就被成功解锁
     */
    public boolean tryComplete(Player player, AchievementNode node) {
        if (node.conditionGroup().isEmpty()) return false;
        PlayerAchievementData data = storage.getCached(player.getUniqueId());
        if (data == null || data.isUnlocked(node.nodePath())) return false;
        if (!evaluateSingle(player, node)) return false;
        unlock(player, node);
        return true;
    }

    // ── 解锁逻辑 ──────────────────────────────────────────

    /**
     * 解锁单个成就（必须在主线程调用）。
     */
    private void unlock(Player player, AchievementNode node) {
        AchievementUnlockEvent event = new AchievementUnlockEvent(player, node.nodePath());
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) return;

        PlayerAchievementData data = storage.getCached(player.getUniqueId());
        if (data == null) return;

        // 若已解锁则跳过（防止竞争条件）
        if (data.isUnlocked(node.nodePath())) return;

        data.setStatus(node.nodePath(),
                new PlayerAchievementData.AchievementStatus(true, System.currentTimeMillis()));

        storage.save(player.getUniqueId(), data);

        // 广播由 ChatBroadcastService 监听 AchievementUnlockEvent 统一处理
        // 避免重复广播

        // 执行成就命令
        if (commandRunner != null && !node.commands().isEmpty()) {
            commandRunner.run(player, node);
        }
    }

    /** 管理员强制设置成就状态（不触发评估，不广播，但会触发 AchievementUpdateEvent） */
    public void forceSet(Player player, String nodePath, boolean unlocked) {
        PlayerAchievementData data = storage.getCached(player.getUniqueId());
        if (data == null) return;

        data.setStatus(nodePath,
                new PlayerAchievementData.AchievementStatus(unlocked,
                        unlocked ? System.currentTimeMillis() : -1L));
        storage.save(player.getUniqueId(), data);

        // 通知外部监听者
        AchievementUpdateEvent event = new AchievementUpdateEvent(
                player, nodePath, unlocked, "admin_set");
        Bukkit.getPluginManager().callEvent(event);
    }

    // ── PAPI 解析（必须主线程） ──────────────────────────

    /** 在主线程安全解析 PAPI */
    private String resolvePapi(Player player, String text) {
        return placeholderResolver.resolve(player, text);
    }

    // ── 内部记录类型 ─────────────────────────────────────

    private record PendingEvaluation(AchievementNode node, List<ConditionResolved> resolvedConditions) {}
    private record ConditionResolved(Condition condition, String target, String current) {}
}
