package cc.oniacute.plugin.bakaachievements.achievement;

import org.bukkit.Material;

/**
 * 成就节点——分类或成就的密封接口。
 * <p>
 * 整个成就树由 {@link Category}（内部节点）和 {@link Achievement}（叶子节点）
 * 两种类型构成，通过此接口统一处理。
 * </p>
 */
public sealed interface AchievementNode permits Achievement, Category {

    /** 节点类型枚举 */
    enum NodeType {
        CATEGORY,
        ACHIEVEMENT
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

    /** 是否需要权限 */
    boolean permission();
}
