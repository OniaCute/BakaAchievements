package cc.oniacute.plugin.bakaachievements.vanilla;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementRegistry;
import cc.oniacute.plugin.bakaachievements.achievement.PlayerAchievementData;
import cc.oniacute.plugin.bakaachievements.api.event.AchievementUpdateEvent;
import cc.oniacute.plugin.bakaachievements.chat.ChatBroadcastService;
import cc.oniacute.plugin.bakaachievements.storage.PlayerDataStorage;
import cc.oniacute.plugin.bakaachievements.util.PathUtil;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.util.UUID;

/**
 * 原版成就同步服务。
 */
public final class VanillaSyncService implements Listener {

    private final BakaAchievements plugin;
    private final AchievementRegistry registry;
    private final PlayerDataStorage storage;
    private ChatBroadcastService chatBroadcastService;

    public VanillaSyncService(BakaAchievements plugin, AchievementRegistry registry, PlayerDataStorage storage) {
        this.plugin = plugin;
        this.registry = registry;
        this.storage = storage;
    }

    public void setChatBroadcastService(ChatBroadcastService chatBroadcastService) {
        this.chatBroadcastService = chatBroadcastService;
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        Advancement adv = event.getAdvancement();
        if (adv.getDisplay() == null) return;

        String nodePath = PathUtil.VANILLA_ROOT + "."
                + adv.getKey().toString().replace(':', '.').replace('/', '_');

        if (registry.getNode(nodePath).isEmpty()) {
            plugin.getLogger().warning("原版成就镜像节点不存在: " + nodePath);
            return;
        }

        // 解锁并触发事件 + 广播
        unlock(player, nodePath,
                adv.getDisplay().title() != null
                        ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(adv.getDisplay().title())
                        : adv.getKey().getKey());
    }

    /**
     * 手动解锁原版成就。
     */
    public void unlock(Player player, String nodePath, String displayName) {
        UUID uuid = player.getUniqueId();
        storage.load(uuid).thenAccept(data -> {
            if (data.isUnlocked(nodePath)) return; // 已解锁，跳过

            data.setStatus(nodePath,
                    new PlayerAchievementData.AchievementStatus(true, System.currentTimeMillis()));
            storage.save(uuid, data);

            // 在主线程触发事件和广播
            Bukkit.getScheduler().runTask(plugin, () -> {
                AchievementUpdateEvent updateEvent = new AchievementUpdateEvent(
                        player, nodePath, true, "vanilla_sync");
                Bukkit.getPluginManager().callEvent(updateEvent);

                if (chatBroadcastService != null) {
                    chatBroadcastService.broadcast(player, nodePath, displayName);
                }
            });
        });
    }
}
