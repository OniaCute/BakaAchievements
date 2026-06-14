package cc.oniacute.plugin.bakaachievements.gui;

import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护每玩家的菜单路径栈，用于"返回上级"功能。
 * <p>
 * 当玩家从根菜单进入子分类时，当前路径被压栈；
 * 点击"返回上级"按钮或执行 {@code [previous]} 命令时弹出栈顶，
 * 还原到之前打开的菜单。
 * 线程安全：使用 {@link ConcurrentHashMap} + 每玩家独立 {@link Deque}。
 * </p>
 */
public final class MenuStack {

    /** 根路径常量——用于标识根菜单 */
    public static final String ROOT_PATH = "__root__";

    /** 玩家 UUID → 路径栈（栈顶为最近进入的子菜单） */
    private final Map<UUID, Deque<String>> stacks = new ConcurrentHashMap<>();

    /**
     * 压栈当前路径，用于后续"返回上级"操作。
     * <p>
     * 通常在打开子分类菜单前调用，压入当前菜单路径。
     * </p>
     *
     * @param player 玩家
     * @param path   当前菜单路径（如 {@code "__root__"} 或 {@code "test.category"}）
     */
    public void push(Player player, String path) {
        stacks.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>())
                .push(path);
    }

    /**
     * 弹出上一个路径。
     *
     * @param player 玩家
     * @return 上一个路径；若栈已空则返回空 {@link Optional}
     */
    public Optional<String> pop(Player player) {
        Deque<String> deque = stacks.get(player.getUniqueId());
        if (deque == null || deque.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(deque.poll());
    }

    /**
     * 查看栈顶路径，不弹出。
     *
     * @param player 玩家
     * @return 栈顶路径；若栈已空则返回空 {@link Optional}
     */
    public Optional<String> peek(Player player) {
        Deque<String> deque = stacks.get(player.getUniqueId());
        if (deque == null || deque.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(deque.peek());
    }

    /**
     * 清空该玩家的菜单历史栈（通常在玩家关闭所有菜单时调用）。
     *
     * @param player 玩家
     */
    public void clear(Player player) {
        stacks.remove(player.getUniqueId());
    }

    /**
     * 查询该玩家的栈是否为空。
     *
     * @param player 玩家
     * @return {@code true} 表示无历史记录
     */
    public boolean isEmpty(Player player) {
        Deque<String> deque = stacks.get(player.getUniqueId());
        return deque == null || deque.isEmpty();
    }
}
