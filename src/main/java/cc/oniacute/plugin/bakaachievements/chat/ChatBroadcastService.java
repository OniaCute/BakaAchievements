package cc.oniacute.plugin.bakaachievements.chat;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementNode;
import cc.oniacute.plugin.bakaachievements.achievement.PlayerAchievementData;
import cc.oniacute.plugin.bakaachievements.api.event.AchievementUnlockEvent;
import cc.oniacute.plugin.bakaachievements.storage.PlayerDataStorage;
import cc.oniacute.plugin.bakaachievements.util.MiniMessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 成就广播服务——将成就解锁消息按玩家偏好分发到在线玩家。
 * <p>
 * 监听 Paper 原版成就事件和自定义 {@link AchievementUnlockEvent}，
 * 支持替代原版广播、hover 详情卡片、按 {@code tipsSelf}/{@code tipsOthers} 过滤接收者。
 * </p>
 */
public final class ChatBroadcastService implements Listener {

    private static final String HOVER_PLACEHOLDER = "{__HOVER__}";
    private static final TextColor HOVER_TRIGGER_COLOR = TextColor.color(0x6cd3ff);

    private final BakaAchievements plugin;

    public ChatBroadcastService(@NotNull BakaAchievements plugin) {
        this.plugin = plugin;
    }

    // ── 原版成就事件 ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onAdvancementDone(@NotNull PlayerAdvancementDoneEvent event) {
        String key = event.getAdvancement().getKey().toString();
        if (key.startsWith("minecraft:recipes/")) return;
        if (event.getAdvancement().getDisplay() == null) return;

        if (plugin.getConfigManager().isChatOverrideEnabled()) {
            event.message(null);
        }

        // 查找 vanilla.yml 覆盖或使用 Bukkit 原版显示名
        var override = plugin.getConfigManager().getVanillaOverrides().get(key);
        String displayName;
        List<String> descriptions = new ArrayList<>();

        if (override != null && !override.display().isEmpty()) {
            displayName = override.display();
            descriptions.addAll(override.descriptions());
        } else {
            try {
                displayName = PlainTextComponentSerializer.plainText()
                        .serialize(event.getAdvancement().getDisplay().title());
            } catch (Exception e) {
                displayName = event.getAdvancement().getKey().getKey();
            }
            if (event.getAdvancement().getDisplay().description() != null) {
                descriptions.add(PlainTextComponentSerializer.plainText()
                        .serialize(event.getAdvancement().getDisplay().description()));
            }
        }

        // 计算原版节点路径（与 VanillaTreeBuilder 一致）
        String vanillaNodePath = cc.oniacute.plugin.bakaachievements.util.PathUtil
                .vanillaNodePath(event.getAdvancement().getKey());

        Component hoverComponent = buildHoverComponent(displayName, descriptions, event.getPlayer(), vanillaNodePath);
        broadcastWithHover(event.getPlayer(), displayName, hoverComponent, vanillaNodePath);
    }

    // ── 自定义成就事件 ───────────────────────────────────────

    @EventHandler
    public void onAchievementUnlock(@NotNull AchievementUnlockEvent event) {
        if (event.isCancelled()) return;

        // 从注册表获取成就节点
        var nodeOpt = plugin.getAchievementRegistry().getNode(event.getNodePath());
        if (nodeOpt.isEmpty()) {
            // 回退：仅显示纯文本
            broadcast(event.getPlayer(), event.getNodePath(), List.of());
            return;
        }

        var node = nodeOpt.get();
        String miniDisplay = node.display();                              // MiniMessage，用于 hover
        String plainDisplay = toPlainText(miniDisplay);                   // 纯文本，用于聊天模板
        List<String> descriptions = node.descriptions();                  // 接口方法，Category/Mixed/Achievement 均可

        // 直接用 MiniMessage 构建 hover 卡片
        Component hoverComponent = buildHoverComponent(miniDisplay, descriptions, event.getPlayer(), event.getNodePath());
        broadcastWithHover(event.getPlayer(), plainDisplay, hoverComponent, event.getNodePath());
    }

    // ── 广播逻辑 ──────────────────────────────────────────────

    /**
     * 由 {@link cc.oniacute.plugin.bakaachievements.achievement.ProgressService}
     * 在成就解锁时调用的 4 参数重载。
     *
     * @param player   解锁成就的玩家
     * @param nodePath 成就节点路径
     * @param display  成就显示名（MiniMessage 格式字符串）
     * @param node     成就/混合节点
     */
    public void broadcast(@NotNull Player player, @NotNull String nodePath,
                          @NotNull String display, @NotNull AchievementNode node) {
        String plainDisplay = toPlainText(display);

        Component hoverComponent = buildHoverComponent(
                node.display(), node.descriptions(), player, node.nodePath());

        broadcastWithHover(player, plainDisplay, hoverComponent, nodePath);
    }

    private void broadcast(@NotNull Player unlockingPlayer,
                           @NotNull String plainDisplay,
                           @NotNull List<String> descriptions) {
        Component hoverComponent = buildHoverComponent(plainDisplay, descriptions, unlockingPlayer, "");
        broadcastWithHover(unlockingPlayer, plainDisplay, hoverComponent, "");
    }

    private void broadcastWithHover(@NotNull Player unlockingPlayer,
                                    @NotNull String plainDisplay,
                                    @NotNull Component hoverComponent,
                                    @NotNull String nodePath) {
        String template = plugin.getMessages().chatAchievementUnlocked()
                .replace("{prefix}", plugin.getMessages().getMessage("PREFIX", ""));
        template = resolvePlayerPlaceholders(template, unlockingPlayer);

        Component message = buildMessageWithHover(template, hoverComponent, plainDisplay, nodePath);

        PlayerDataStorage storage = plugin.getPlayerDataStorage();
        UUID unlockingUuid = unlockingPlayer.getUniqueId();

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            boolean isSelf = onlinePlayer.getUniqueId().equals(unlockingUuid);
            if (shouldReceive(onlinePlayer, isSelf, storage)) {
                onlinePlayer.sendMessage(message);
            }
        }
    }

    // ── Hover 卡片构建 ──────────────────────────────────────

    /**
     * 构建 hover 提示卡片（支持 MiniMessage 格式的显示名）。
     */
    private @NotNull Component buildHoverComponent(@NotNull String miniDisplay,
                                                    @Nullable List<String> descriptions,
                                                    @NotNull Player player,
                                                    @NotNull String nodePath) {
        List<Component> lines = new ArrayList<>();
        var msgs = plugin.getMessages();

        // 标题（从 messages.yml 读取，支持 {player_displayname}/{player} 占位符）
        lines.add(miniOrPlain(resolvePlayerPlaceholders(msgs.hoverTitle(), player)));

        // 成就显示名（粗体），支持 MiniMessage 占位符
        String resolvedDisplay = resolvePlayerPlaceholders(miniDisplay, player)
                .replace("{display}", toPlainText(miniDisplay));
        try {
            lines.add(plugin.getMiniMessage().deserialize("<bold>" + resolvedDisplay + "</bold>"));
        } catch (Exception e) {
            lines.add(Component.text(resolvedDisplay));
        }

        if (descriptions != null && !descriptions.isEmpty()) {
            lines.add(Component.empty());
            for (String desc : descriptions) {
                if (desc == null || desc.isBlank()) continue;
                String resolved = resolvePlaceholders(desc, player);
                try {
                    lines.add(plugin.getMiniMessage().deserialize("<!italic><gray>" + resolved + "</gray>"));
                } catch (Exception e) {
                    lines.add(MiniMessageUtil.parseGray(resolved, plugin.getMiniMessage()));
                }
            }
        }

        lines.add(Component.empty());
        lines.add(miniOrPlain(resolvePlayerPlaceholders(msgs.hoverSeparator(), player)));
        lines.add(miniOrPlain(resolvePlayerPlaceholders(msgs.hoverUnlocked(), player)));

        // 合并为单个 Component（用换行分隔）
        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            result = result.append(lines.get(i));
            if (i < lines.size() - 1) {
                result = result.append(Component.newline());
            }
        }
        return result;
    }

    // ── 消息构建（{__HOVER__} 处理）────────────────────────

    /**
     * 处理消息模板中的 {@code {__HOVER__}} 占位符。
     * <p>
     * 将模板按占位符切分为前后两部分，分别 MiniMessage 解析，
     * 中间插入带 hover 的成就名触发器组件。
     * </p>
     */
    private @NotNull Component buildMessageWithHover(@NotNull String template,
                                                      @NotNull Component hoverComponent,
                                                      @NotNull String plainDisplayName,
                                                      @NotNull String nodePath) {
        int hoverIdx = template.indexOf(HOVER_PLACEHOLDER);
        if (hoverIdx < 0) {
            try {
                return plugin.getMiniMessage().deserialize(template);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "广播消息 MiniMessage 解析失败: " + template, e);
                return Component.text(template);
            }
        }

        String before = template.substring(0, hoverIdx);
        String after = template.substring(hoverIdx + HOVER_PLACEHOLDER.length());

        Component beforeComp;
        Component afterComp;
        try {
            beforeComp = plugin.getMiniMessage().deserialize(before);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "广播消息前半段解析失败: " + before, e);
            beforeComp = Component.text(before);
        }
        try {
            afterComp = plugin.getMiniMessage().deserialize(after);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "广播消息后半段解析失败: " + after, e);
            afterComp = Component.text(after);
        }

        // hover 触发器：带颜色的成就名，悬停显示详情，点击导航到该成就所属分类
        String clickCmd = nodePath.isEmpty() ? "/bac" : "/bac " + nodePath;
        Component hoverTrigger = Component.text("[" + plainDisplayName + "]")
                .color(HOVER_TRIGGER_COLOR)
                .hoverEvent(HoverEvent.showText(hoverComponent))
                .clickEvent(ClickEvent.runCommand(clickCmd));

        return Component.empty().append(beforeComp).append(hoverTrigger).append(afterComp);
    }

    // ── 偏好过滤 ──────────────────────────────────────────────

    private boolean shouldReceive(@NotNull Player receiver, boolean isSelf,
                                   @Nullable PlayerDataStorage storage) {
        if (storage == null) return true;

        PlayerAchievementData data;
        try {
            data = storage.getCached(receiver.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "获取玩家偏好失败: " + receiver.getName(), e);
            return true;
        }

        if (data == null) return true;
        return isSelf ? data.isTipsSelf() : data.isTipsOthers();
    }

    // ── 工具方法 ────────────────────────────────────────────

    /**
     * MiniMessage → 纯文本。
     */
    private @NotNull String toPlainText(@NotNull String miniMessageStr) {
        try {
            return PlainTextComponentSerializer.plainText()
                    .serialize(plugin.getMiniMessage().deserialize(miniMessageStr));
        } catch (Exception e) {
            return miniMessageStr;
        }
    }

    /** 解析 MiniMessage → Component；失败时降级为纯文本 */
    private @NotNull Component miniOrPlain(@NotNull String miniMessageStr) {
        try {
            return plugin.getMiniMessage().deserialize(miniMessageStr);
        } catch (Exception e) {
            return Component.text(miniMessageStr);
        }
    }

    /** 解析文本中的玩家/{display} 占位符和 %xxx% PAPI 变量 */
    private @NotNull String resolvePlaceholders(@NotNull String text, @NotNull Player player) {
        String result = resolvePlayerPlaceholders(text, player)
                .replace("{display}", playerDisplayNamePlain(player));
        if (result.contains("%")) {
            try {
                if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, result);
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    private @NotNull String resolvePlayerPlaceholders(@NotNull String text, @NotNull Player player) {
        return text
                .replace("{player_displayname}", playerDisplayNamePlain(player))
                .replace("{player}", player.getName());
    }

    private @NotNull String playerDisplayNamePlain(@NotNull Player player) {
        return PlainTextComponentSerializer.plainText().serialize(player.displayName());
    }
}
