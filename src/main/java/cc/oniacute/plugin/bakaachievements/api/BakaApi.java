package cc.oniacute.plugin.bakaachievements.api;

import cc.oniacute.plugin.bakaachievements.achievement.AchievementNode;
import cc.oniacute.plugin.bakaachievements.api.condition.ConditionType;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * BakaAchievements 对外提供的公共 API 接口。
 * <p>
 * 外部插件可通过 {@code BakaAchievements.getInstance().getApi()} 获取此接口的实现。
 * </p>
 */
public interface BakaApi {

    // ── 基础信息 ─────────────────────────────────────────

    /** 获取插件版本号 */
    String getVersion();

    /** 检查 PlaceholderAPI 是否已挂载 */
    boolean isPlaceholderApiAvailable();

    /** 将 MiniMessage 格式字符串解析为 Adventure Component */
    net.kyori.adventure.text.Component parseMiniMessage(String miniMessage);

    /** 向控制台输出信息日志 */
    void logInfo(String message);

    /** 向控制台输出警告日志 */
    void logWarning(String message);

    /** 向控制台输出错误日志 */
    void logError(String message);

    // ── 成就查询 ─────────────────────────────────────────

    /** 检查玩家是否已达成指定成就 */
    boolean isUnlocked(UUID player, String nodePath);

    /** 获取玩家达成成就的时间戳（未达成返回 -1） */
    long getAchieveTime(UUID player, String nodePath);

    /** 获取玩家已解锁数量 */
    int getUnlockedCount(UUID player);

    /** 获取所有成就节点路径 */
    Collection<String> listAchievementPaths();

    /** 获取所有分类节点路径 */
    Collection<String> listCategoryPaths();

    /** 根据路径查找节点 */
    Optional<AchievementNode> getNode(String path);

    // ── 状态变更 ─────────────────────────────────────────

    /** 设置玩家成就状态（异步写盘） */
    CompletableFuture<Void> setStatus(UUID player, String nodePath, boolean unlocked);

    // ── 自定义条件类型扩展 ──────────────────────────────

    /** 注册自定义条件操作符处理器 */
    void registerConditionType(String op, ConditionType handler);
}
