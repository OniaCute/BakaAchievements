package cc.oniacute.plugin.bakaachievements.api;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementNode;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementRegistry;
import cc.oniacute.plugin.bakaachievements.achievement.PlayerAchievementData;
import cc.oniacute.plugin.bakaachievements.achievement.ProgressService;
import cc.oniacute.plugin.bakaachievements.api.condition.ConditionType;
import cc.oniacute.plugin.bakaachievements.storage.PlayerDataStorage;
import cc.oniacute.plugin.bakaachievements.util.MiniMessageUtil;
import net.kyori.adventure.text.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link BakaApi} 的完整实现。
 */
public final class BakaAchievementsApi implements BakaApi {

    private final BakaAchievements plugin;
    private final AchievementRegistry registry;
    private final PlayerDataStorage storage;
    private final ProgressService progressService;

    /** 自定义条件操作符注册表 */
    private final Map<String, ConditionType> customConditions = new ConcurrentHashMap<>();

    public BakaAchievementsApi(BakaAchievements plugin, AchievementRegistry registry,
                                PlayerDataStorage storage, ProgressService progressService) {
        this.plugin = plugin;
        this.registry = registry;
        this.storage = storage;
        this.progressService = progressService;
    }

    // ── 基础信息 ─────────────────────────────────────────

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean isPlaceholderApiAvailable() {
        return plugin.getPapiHook().isEnabled();
    }

    @Override
    public Component parseMiniMessage(String miniMessage) {
        return MiniMessageUtil.parse(miniMessage, plugin.getMiniMessage());
    }

    @Override
    public void logInfo(String message) {
        plugin.getLogger().info(message);
    }

    @Override
    public void logWarning(String message) {
        plugin.getLogger().warning(message);
    }

    @Override
    public void logError(String message) {
        plugin.getLogger().severe(message);
    }

    // ── 成就查询 ─────────────────────────────────────────

    @Override
    public boolean isUnlocked(UUID player, String nodePath) {
        PlayerAchievementData data = storage.getCached(player);
        return data != null && data.isUnlocked(nodePath);
    }

    @Override
    public long getAchieveTime(UUID player, String nodePath) {
        PlayerAchievementData data = storage.getCached(player);
        if (data == null) return -1L;
        return data.getStatus(nodePath).achieveTime();
    }

    @Override
    public int getUnlockedCount(UUID player) {
        PlayerAchievementData data = storage.getCached(player);
        return data != null ? data.countUnlocked() : 0;
    }

    @Override
    public Collection<String> listAchievementPaths() {
        return registry.getAllNodes().entrySet().stream()
                .filter(e -> e.getValue().nodeType() == AchievementNode.NodeType.ACHIEVEMENT)
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public Collection<String> listCategoryPaths() {
        return registry.getAllNodes().entrySet().stream()
                .filter(e -> e.getValue().nodeType() == AchievementNode.NodeType.CATEGORY)
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public Optional<AchievementNode> getNode(String path) {
        return registry.getNode(path);
    }

    // ── 状态变更 ─────────────────────────────────────────

    @Override
    public CompletableFuture<Void> setStatus(UUID player, String nodePath, boolean unlocked) {
        PlayerAchievementData data = storage.getCached(player);
        if (data == null) {
            return storage.load(player).thenCompose(d -> setStatusInternal(player, nodePath, unlocked, d));
        }
        return setStatusInternal(player, nodePath, unlocked, data);
    }

    private CompletableFuture<Void> setStatusInternal(UUID player, String nodePath, boolean unlocked,
                                                       PlayerAchievementData data) {
        data.setStatus(nodePath,
                new PlayerAchievementData.AchievementStatus(unlocked,
                        unlocked ? System.currentTimeMillis() : -1L));
        return storage.save(player, data);
    }

    // ── 自定义条件类型 ───────────────────────────────────

    @Override
    public void registerConditionType(String op, ConditionType handler) {
        customConditions.put(op, handler);
    }

    /** 获取自定义条件处理器 */
    public Optional<ConditionType> getCustomCondition(String op) {
        return Optional.ofNullable(customConditions.get(op));
    }
}
