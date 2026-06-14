package cc.oniacute.plugin.bakaachievements.vanilla;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementNode;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementRegistry;
import cc.oniacute.plugin.bakaachievements.achievement.PlayerAchievementData;
import cc.oniacute.plugin.bakaachievements.api.event.AchievementUpdateEvent;
import cc.oniacute.plugin.bakaachievements.storage.PlayerDataStorage;
import cc.oniacute.plugin.bakaachievements.util.PathUtil;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.util.Iterator;
import java.util.UUID;

/**
 * 原版成就同步服务——仅负责数据镜像，不负责广播。
 * <p>
 * 广播由 {@link cc.oniacute.plugin.bakaachievements.chat.ChatBroadcastService} 统一处理。
 * </p>
 */
public final class VanillaSyncService implements Listener {

    private final BakaAchievements plugin;
    private final AchievementRegistry registry;
    private final PlayerDataStorage storage;

    public VanillaSyncService(BakaAchievements plugin, AchievementRegistry registry,
                              PlayerDataStorage storage) {
        this.plugin = plugin;
        this.registry = registry;
        this.storage = storage;
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        Advancement adv = event.getAdvancement();
        String key = adv.getKey().toString();
        if (key.startsWith("minecraft:recipes/")) return;

        String nodePath = PathUtil.vanillaNodePath(adv.getKey());

        var nodeOpt = registry.getNode(nodePath);
        if (nodeOpt.isEmpty()) {
            plugin.getLogger().warning("原版成就镜像节点不存在: key=" + key + " node=" + nodePath);
            return;
        }
        if (!isUnlockableVanillaNode(nodeOpt.get())) return;

        String displayName = adv.getDisplay() != null && adv.getDisplay().title() != null
                ? PlainTextComponentSerializer.plainText().serialize(adv.getDisplay().title())
                : adv.getKey().getKey();

        unlock(player, nodePath, displayName);
    }

    /**
     * 手动解锁原版成就（写存储 + 触发事件，不广播）。
     * <p>
     * 广播由 {@link cc.oniacute.plugin.bakaachievements.chat.ChatBroadcastService#onAdvancementDone}
     * 负责，二者通过各自监听同一事件实现解耦。
     * </p>
     */
    public void unlock(Player player, String nodePath, String displayName) {
        UUID uuid = player.getUniqueId();
        storage.load(uuid).thenAccept(data -> {
            if (data.isUnlocked(nodePath)) return;

            data.setStatus(nodePath,
                    new PlayerAchievementData.AchievementStatus(true, System.currentTimeMillis()));
            storage.save(uuid, data);

            Bukkit.getScheduler().runTask(plugin, () -> {
                AchievementUpdateEvent updateEvent = new AchievementUpdateEvent(
                        player, nodePath, true, "vanilla_sync");
                Bukkit.getPluginManager().callEvent(updateEvent);
            });
        });
    }

    /**
     * 静默镜像玩家当前已经完成的所有原版 advancement。
     * <p>
     * 用于插件首次记录某个玩家时补齐历史原版成就。该流程只写入本插件
     * playerdata, 不触发聊天提示, 也不调用 Bukkit 原版完成事件。
     * 必须在主线程调用, 因为 Bukkit advancement progress 只能安全地在主线程读取。
     * </p>
     */
    public void syncCompletedSilently(Player player, PlayerAchievementData data) {
        int synced = 0;
        int missing = 0;
        long now = System.currentTimeMillis();

        Iterator<Advancement> iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            String key = advancement.getKey().toString();
            if (key.startsWith("minecraft:recipes/")) continue;

            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            if (!progress.isDone()) continue;

            String nodePath = PathUtil.vanillaNodePath(advancement.getKey());
            var nodeOpt = registry.getNode(nodePath);
            if (nodeOpt.isEmpty()) {
                missing++;
                plugin.getLogger().warning("原版成就镜像节点不存在: key=" + key + " node=" + nodePath);
                continue;
            }
            if (!isUnlockableVanillaNode(nodeOpt.get())) continue;
            if (data.isUnlocked(nodePath)) continue;

            data.setStatus(nodePath, PlayerAchievementData.AchievementStatus.unlocked(now));
            synced++;
        }

        if (synced > 0) {
            storage.save(player.getUniqueId(), data);
        }
        plugin.getLogger().fine("静默同步原版成就: player=" + player.getName()
                + " synced=" + synced + " missing=" + missing);
    }

    private boolean isUnlockableVanillaNode(AchievementNode node) {
        return node.nodeType() == AchievementNode.NodeType.ACHIEVEMENT
                || node.nodeType() == AchievementNode.NodeType.MIXED;
    }
}
