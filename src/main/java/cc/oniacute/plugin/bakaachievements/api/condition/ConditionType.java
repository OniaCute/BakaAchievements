package cc.oniacute.plugin.bakaachievements.api.condition;

import cc.oniacute.plugin.bakaachievements.achievement.condition.ConditionContext;

/**
 * 自定义条件类型接口——供外部插件注册额外的条件操作符。
 * <p>
 * 例如：外部插件可注册 {@code "playedTime"} 操作符来检查玩家的游戏时长。
 * </p>
 */
@FunctionalInterface
public interface ConditionType {

    /**
     * 评估条件。
     *
     * @param ctx 条件上下文（含 Player 和已解析的 target/current）
     * @return {@code true} 表示条件满足
     */
    boolean evaluate(ConditionContext ctx);
}
