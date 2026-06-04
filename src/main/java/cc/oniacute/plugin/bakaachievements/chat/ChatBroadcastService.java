package cc.oniacute.plugin.bakaachievements.chat;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.config.ConfigManager;
import cc.oniacute.plugin.bakaachievements.config.Messages;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

/**
 * 聊天广播服务——监听原版成就完成事件，替换默认广播。
 * <p>
 * 若 {@code chat.override-vanilla=true}，取消原版广播并使用自定义模板。
 * </p>
 */
public final class ChatBroadcastService implements Listener {

    private final BakaAchievements plugin;
    private final ConfigManager configManager;
    private final Messages messages;
    private final MiniMessage miniMessage;

    public ChatBroadcastService(BakaAchievements plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.messages = plugin.getMessages();
        this.miniMessage = plugin.getMiniMessage();
    }

    /**
     * 监听原版成就完成事件，替换默认广播。
     */
    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (!configManager.isChatOverrideEnabled()) return;

        // 取消原版广播
        event.message(null);

        // 获取成就显示名称
        String title = "";
        if (event.getAdvancement().getDisplay() != null) {
            title = plainText(event.getAdvancement().getDisplay().title());
        }

        broadcast(event.getPlayer(), event.getAdvancement().getKey().toString(), title);
    }

    /**
     * 广播自定义成就解锁。
     *
     * @param player  解锁的玩家
     * @param nodePath 成就节点路径
     * @param display  成就显示名称
     */
    public void broadcast(Player player, String nodePath, String display) {
        String format = configManager.getChatFormat();
        format = format.replace("%player_name%", player.getName());
        format = format.replace("%display%", display);

        // PAPI 解析
        if (plugin.getPapiHook().isEnabled()) {
            format = PlaceholderAPI.setPlaceholders(player, format);
        }

        Component msg = miniMessage.deserialize(format);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(msg);
        }
    }

    private String plainText(net.kyori.adventure.text.Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(component);
    }
}
