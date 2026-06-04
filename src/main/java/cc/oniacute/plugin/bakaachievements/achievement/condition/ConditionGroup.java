package cc.oniacute.plugin.bakaachievements.achievement.condition;

import java.util.List;

/**
 * 条件组——由多个条件以 AND 关系组成。
 * <p>
 * 当所有条件都满足时，此条件组视为通过。
 * 空条件组（{@code conditions.isEmpty()}）视为始终满足。
 * </p>
 *
 * @param conditions 条件列表（AND 关系）
 */
public record ConditionGroup(List<Condition> conditions) {

    /** 空条件组实例 */
    public static final ConditionGroup EMPTY = new ConditionGroup(List.of());

    /**
     * 检查此条件组是否为空（没有条件，视为自动满足）。
     *
     * @return {@code true} 表示空组
     */
    public boolean isEmpty() {
        return conditions.isEmpty();
    }
}
