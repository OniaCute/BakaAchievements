package cc.oniacute.plugin.bakaachievements.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * 成就解锁事件——在玩家达成成就时触发（可取消）。
 * <p>
 * 取消此事件将阻止成就解锁和后续广播。
 * </p>
 */
public class AchievementUnlockEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String nodePath;
    private boolean cancelled;

    public AchievementUnlockEvent(Player player, String nodePath) {
        this.player = player;
        this.nodePath = nodePath;
    }

    public Player getPlayer() {
        return player;
    }

    public String getNodePath() {
        return nodePath;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    @SuppressWarnings("unused")
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
