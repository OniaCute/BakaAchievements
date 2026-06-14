package cc.oniacute.plugin.bakaachievements.achievement;

import cc.oniacute.plugin.bakaachievements.achievement.condition.ConditionGroup;
import org.bukkit.Material;

import java.util.List;
import java.util.Optional;

/**
 * 成就节点——分类、成就或混合节点的密封接口。
 * <p>
 * 整个成就树由 {@link Category}（纯分类节点）、{@link Achievement}（纯成就节点）
 * 和 {@link MixedNode}（既是分类又是成就）三种类型构成，通过此接口统一处理。
 * </p>
 */
public sealed interface AchievementNode permits Achievement, Category, MixedNode {

    /** 节点类型枚举 */
    enum NodeType {
        CATEGORY,
        ACHIEVEMENT,
        MIXED
    }

    /** 节点路径（全局唯一，如 {@code test.category.cus_1}） */
    String nodePath();

    /** 节点类型 */
    NodeType nodeType();

    /** 内部名称 */
    String name();

    /** 显示名称（支持 MiniMessage 格式） */
    String display();

    /** 图标材质 */
    Material material();

    /**
     * HeadDatabase 头颅 ID。
     * -1 表示使用普通材质；≥0 表示使用 HDB 头颅。
     */
    int hdbId();

    /** 是否需要权限 */
    boolean permission();

    // ── 辅助方法（减少 instanceof 判断）──────────────────

    /**
     * 子节点列表。
     * 仅 {@link Category} 和 {@link MixedNode} 有实际子节点；
     * {@link Achievement} 返回空列表。
     */
    default List<AchievementNode> children() {
        return List.of();
    }

    /**
     * 条件组。
     * 仅 {@link Achievement} 和 {@link MixedNode} 有条件；
     * {@link Category} 返回空组。
     */
    default ConditionGroup conditionGroup() {
        return ConditionGroup.EMPTY;
    }

    /**
     * 是否自动完成。
     * 仅 {@link Achievement} 和 {@link MixedNode} 有意义；
     * {@link Category} 返回 false。
     */
    default boolean auto() {
        return false;
    }

    /**
     * 达成时执行的命令列表。
     * 仅 {@link Achievement} 和 {@link MixedNode} 有命令；
     * {@link Category} 返回空列表。
     */
    default List<String> commands() {
        return List.of();
    }

    /**
     * 描述列表。
     * {@link Category} 返回空列表；
     * {@link Achievement} 和 {@link MixedNode} 返回实际描述。
     */
    default List<String> descriptions() {
        return List.of();
    }
}
