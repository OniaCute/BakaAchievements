package cc.oniacute.plugin.bakaachievements.achievement;

import cc.oniacute.plugin.bakaachievements.achievement.condition.ConditionGroup;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;

import java.util.List;
import java.util.Set;

/**
 * 混合成就节点——既是分类（可展开子节点），又是成就（可解锁）。
 * <p>
 * 根据 INTRODUCE.md 的 Type 规范，MIXED 类型兼具
 * {@link Category} 和 {@link Achievement} 的属性：
 * <ul>
 *   <li>与 {@code CATEGORY} 一样拥有 {@code children} 属性</li>
 *   <li>未完成时与 {@code ACHIEVEMENT} 类似，左键尝试完成，右键打开子分类</li>
 *   <li>完成时左键直接打开子分类</li>
 *   <li>允许 Shift+左键分享（分享当前节点自身进度，不计子分类）</li>
 * </ul>
 * </p>
 *
 * @param nodePath       节点路径（全局唯一）
 * @param name           内部名称
 * @param display        显示名称（MiniMessage 格式）
 * @param descriptions   描述列表（MiniMessage 格式）
 * @param material       图标材质
 * @param hdbId          HeadDatabase 头颅 ID（-1 表示不使用 HDB）
 * @param flags          物品 ItemFlag 集合
 * @param permission     是否需要权限
 * @param auto           是否达成所有条件后自动完成
 * @param conditionGroup 条件组（AND 关系）
 * @param commands       达成时执行的命令列表
 * @param children       子节点列表
 */
public record MixedNode(
        String nodePath,
        String name,
        String display,
        List<String> descriptions,
        Material material,
        int hdbId,
        Set<ItemFlag> flags,
        boolean permission,
        boolean auto,
        ConditionGroup conditionGroup,
        List<String> commands,
        List<AchievementNode> children
) implements AchievementNode {

    @Override
    public NodeType nodeType() {
        return NodeType.MIXED;
    }

    @Override
    public String display() {
        return display;
    }

    @Override
    public Material material() {
        return material;
    }

    @Override
    public int hdbId() {
        return hdbId;
    }

    @Override
    public boolean permission() {
        return permission;
    }

    @Override
    public List<AchievementNode> children() {
        return children;
    }

    @Override
    public ConditionGroup conditionGroup() {
        return conditionGroup;
    }

    @Override
    public boolean auto() {
        return auto;
    }

    @Override
    public List<String> commands() {
        return commands;
    }

    @Override
    public List<String> descriptions() {
        return descriptions;
    }

    /**
     * 递归统计此混合节点下所有成就的总数。
     * <p>
     * 自身算 1 个成就（因为 MIXED 节点本身可以解锁），
     * 再加上所有子节点中的叶子成就。
     * </p>
     *
     * @return 成就总数（至少为 1）
     */
    public int countAchievements() {
        int count = 1; // 自身计为 1 个成就
        for (AchievementNode child : children) {
            if (child instanceof Achievement) {
                count++;
            } else if (child instanceof MixedNode mixed) {
                count += mixed.countAchievements();
            } else if (child instanceof Category cat) {
                count += cat.countAchievements();
            }
        }
        return count;
    }
}
