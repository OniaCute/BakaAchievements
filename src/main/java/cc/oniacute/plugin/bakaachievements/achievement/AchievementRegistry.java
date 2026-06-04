package cc.oniacute.plugin.bakaachievements.achievement;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 成就注册表——全局成就树的唯一入口。
 * <p>
 * 维护节点路径 → 节点对象的映射，提供快速查找和遍历能力。
 * 线程安全：所有写操作在插件启动/重载时（主线程）完成，
 * 读操作通过 {@link ConcurrentHashMap} 支持异步访问。
 * </p>
 */
public final class AchievementRegistry {

    /** 根分类——所有成就节点的起点 */
    private Category root;

    /** 节点路径 → 节点对象的快速索引 */
    private final Map<String, AchievementNode> pathIndex = new ConcurrentHashMap<>();

    /**
     * 设置根分类并重建索引。
     *
     * @param root 根分类
     */
    public void setRoot(Category root) {
        this.root = root;
        pathIndex.clear();
        if (root != null) {
            indexNode(root);
        }
    }

    /** 递归构建路径索引 */
    private void indexNode(AchievementNode node) {
        pathIndex.put(node.nodePath(), node);
        if (node instanceof Category cat) {
            for (AchievementNode child : cat.children()) {
                indexNode(child);
            }
        }
    }

    /**
     * 获取根分类。
     *
     * @return 根分类，可能为 {@code null}
     */
    public Category getRoot() {
        return root;
    }

    /**
     * 根据节点路径查找节点。
     *
     * @param path 节点路径（如 {@code "test.cus_1"}）
     * @return 节点 {@link Optional}
     */
    public Optional<AchievementNode> getNode(String path) {
        return Optional.ofNullable(pathIndex.get(path));
    }

    /**
     * 获取所有成就节点的浅拷贝 Map。
     *
     * @return 路径 → 节点
     */
    public Map<String, AchievementNode> getAllNodes() {
        return new HashMap<>(pathIndex);
    }

    /**
     * 获取成就总数。
     *
     * @return 成就数量
     */
    public int getAchievementCount() {
        return (int) pathIndex.values().stream()
                .filter(n -> n.nodeType() == AchievementNode.NodeType.ACHIEVEMENT)
                .count();
    }
}
