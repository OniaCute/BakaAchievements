package cc.oniacute.plugin.bakaachievements.storage;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 被禁用节点的注册表。
 * <p>
 * 维护由管理员通过 {@code /bac disable} 禁用的节点路径集合。
 * 被禁用的节点（及子树）在 GUI 中完全隐藏。
 * 线程安全：使用 {@link CopyOnWriteArraySet}。
 * </p>
 */
public class DisabledRegistry {

    private final Set<String> disabledPaths = new CopyOnWriteArraySet<>();

    /**
     * 检查节点是否被禁用。
     *
     * @param nodePath 节点路径
     * @return {@code true} 表示已禁用
     */
    public boolean isDisabled(String nodePath) {
        return disabledPaths.contains(nodePath);
    }

    /**
     * 检查节点或其任意祖先是否被禁用。
     * <p>
     * 例如禁用 {@code "test.category"} 后，其子节点
     * {@code "test.category.sub"} 也被视为禁用。
     * </p>
     */
    public boolean isDisabledOrAncestor(String nodePath) {
        if (disabledPaths.contains(nodePath)) return true;
        int idx = nodePath.lastIndexOf('.');
        while (idx > 0) {
            String parent = nodePath.substring(0, idx);
            if (disabledPaths.contains(parent)) return true;
            idx = parent.lastIndexOf('.');
        }
        return disabledPaths.contains(nodePath);
    }

    /**
     * 禁用节点。
     */
    public void disable(String nodePath) {
        disabledPaths.add(nodePath);
    }

    /**
     * 启用节点。
     */
    public void enable(String nodePath) {
        disabledPaths.remove(nodePath);
    }

    /**
     * 获取所有禁用路径的浅拷贝。
     */
    public Set<String> getAllDisabled() {
        return Set.copyOf(disabledPaths);
    }

    /**
     * 批量设置禁用列表。
     */
    public void setAll(Set<String> paths) {
        disabledPaths.clear();
        disabledPaths.addAll(paths);
    }

    /** 清空禁用列表 */
    public void clear() {
        disabledPaths.clear();
    }
}
