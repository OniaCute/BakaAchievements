package cc.oniacute.plugin.bakaachievements.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * 成就状态变更事件——管理员通过 {@code /bac set} 强制设状态时触发。
 * <p>
 * 不可取消。
 * </p>
 */
public class AchievementUpdateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String nodePath;
    private final boolean newStatus;
    private final String source; // e.g. "admin_set", "vanilla_sync"

    public AchievementUpdateEvent(Player player, String nodePath, boolean newStatus, String source) {
        this.player = player;
        this.nodePath = nodePath;
        this.newStatus = newStatus;
        this.source = source;
    }

    public Player getPlayer() {
        return player;
    }

    public String getNodePath() {
        return nodePath;
    }

    public boolean getNewStatus() {
        return newStatus;
    }

    public String getSource() {
        return source;
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
