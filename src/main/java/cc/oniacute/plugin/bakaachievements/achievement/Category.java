package cc.oniacute.plugin.bakaachievements.achievement;

import org.bukkit.Material;

/**
 * 成就分类实体——不可变数据。
 * <p>
 * 分类可嵌套，最多 5 层。每个分类包含子节点列表，
 * 子节点可以是其他分类或叶子成就。
 * </p>
 *
 * @param nodePath   分类节点路径（全局唯一）
 * @param name       分类内部名称
 * @param display    分类显示名称（MiniMessage 格式）
 * @param material   分类图标材质（可选，默认 {@code CHEST}）
 * @param permission 是否需要权限节点才能打开
 * @param children   子节点列表
 */
public record Category(
        String nodePath,
        String name,
        String display,
        Material material,
        boolean permission,
        java.util.List<AchievementNode> children
) implements AchievementNode {

    @Override
    public NodeType nodeType() {
        return NodeType.CATEGORY;
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
    public boolean permission() {
        return permission;
    }

    /**
     * 递归统计此分类下所有叶子成就的总数。
     *
     * @return 成就总数
     */
    public int countAchievements() {
        int count = 0;
        for (AchievementNode child : children) {
            if (child instanceof Achievement) {
                count++;
            } else if (child instanceof Category cat) {
                count += cat.countAchievements();
            }
        }
        return count;
    }
}
