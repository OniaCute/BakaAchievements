package cc.oniacute.plugin.bakaachievements.achievement;

import cc.oniacute.plugin.bakaachievements.achievement.condition.ConditionGroup;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;

import java.util.List;
import java.util.Set;

/**
 * 成就实体——不可变数据。
 * <p>
 * 每个成就有唯一的 {@link #nodePath()} 用于全插件索引，
 * 包含显示信息、自定义材质、权限标记、是否自动完成和达成条件。
 * </p>
 *
 * @param nodePath       成就节点路径（如 {@code test.cus_1}），全局唯一
 * @param name           成就内部名称
 * @param display        成就显示名称（MiniMessage 格式）
 * @param descriptions   成就描述列表（MiniMessage 格式，按行显示）
 * @param material       成就图标材质（必填，加载时校验；hdb 模式下为 PLAYER_HEAD）
 * @param hdbId          HeadDatabase 头颅 ID（-1 表示不使用 HDB）
 * @param flags          物品 ItemFlag 集合
 * @param permission     是否需要权限节点才能达成
 * @param auto           是否达成所有条件后自动完成
 * @param conditionGroup 条件组（AND 关系）
 * @param commands       达成时执行的命令列表（支持 [close]/[previous]/[refresh]）
 */
public record Achievement(
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
        List<String> commands
) implements AchievementNode {

    @Override
    public NodeType nodeType() {
        return NodeType.ACHIEVEMENT;
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
}
