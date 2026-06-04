package cc.oniacute.plugin.bakaachievements.achievement.condition;

import org.bukkit.entity.Player;

/**
 * 条件评估上下文——传递给自定义条件类型处理器。
 *
 * @param player        目标玩家
 * @param targetResolved  target 经 PAPI 解析后的值
 * @param currentResolved current 经 PAPI 解析后的值
 */
public record ConditionContext(
        Player player,
        String targetResolved,
        String currentResolved
) {}
