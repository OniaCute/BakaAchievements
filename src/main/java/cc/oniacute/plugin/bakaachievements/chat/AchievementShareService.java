package cc.oniacute.plugin.bakaachievements.chat;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.Achievement;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementNode;
import cc.oniacute.plugin.bakaachievements.achievement.PlayerAchievementData;
import cc.oniacute.plugin.bakaachievements.achievement.condition.Condition;
import cc.oniacute.plugin.bakaachievements.achievement.condition.ConditionEvaluator;
import cc.oniacute.plugin.bakaachievements.storage.PlayerDataStorage;
import cc.oniacute.plugin.bakaachievements.util.ProgressBarUtil;
import cc.oniacute.plugin.bakaachievements.util.MiniMessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 成就分享服务——处理玩家在 GUI 中 Shift+左键分享成就到聊天频道。
 * <p>
 * 特性：
 * <ul>
 *   <li>权限检查：{@code bakaachievements.share}</li>
 *   <li>冷却绕过权限：{@code bakaachievements.share.cooldown}</li>
 *   <li>双层冷却：全局冷却（global-cooldown-seconds）+ 单成就冷却（cooldown-seconds）</li>
 *   <li>已解锁 / 未解锁状态区分（未解锁需 {@code share.allow-locked=true}）</li>
 *   <li>悬停提示（hover event）展示成就详情，包括显示名、描述、达成状态</li>
 *   <li>尊重接收者的 {@code tipsOthers} 偏好</li>
 * </ul>
 * </p>
 * <p>
 * 所有 {@link #tryShare(Player, AchievementNode)} 调用来自 GUI 点击事件，
 * 保证在主线程执行，可直接访问 Bukkit API。
 * </p>
 */
public final class AchievementShareService {

    /** {__HOVER__} 占位符——消息模板中需要替换为带 HoverEvent 的成就名组件 */
    private static final String HOVER_PLACEHOLDER = "{__HOVER__}";

    /** 分享提示的悬停高亮色 */
    private static final TextColor SHARE_HIGHLIGHT_COLOR = TextColor.color(0x6cd3ff);

    private final BakaAchievements plugin;
    private final PlayerDataStorage storage;

    /** 全局冷却：玩家 UUID → 上次分享时间戳（毫秒） */
    private final Map<UUID, Long> globalCooldown = new ConcurrentHashMap<>();

    /** 单成就冷却：玩家 UUID → (nodePath → 上次分享时间戳 ms) */
    private final Map<UUID, Map<String, Long>> perNodeCooldown = new ConcurrentHashMap<>();

    /** 时间格式化器（线程安全，每个线程独立使用） */
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    public AchievementShareService(@NotNull BakaAchievements plugin,
                                   @NotNull PlayerDataStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    // ── 主入口 ─────────────────────────────────────────────────

    /**
     * 玩家尝试分享成就（由 GUI Shift+左键触发，主线程调用）。
     * <p>
     * 完整的分享流程：
     * <ol>
     *   <li>检查分享功能是否启用</li>
     *   <li>检查玩家是否有分享权限</li>
     *   <li>仅处理 {@link AchievementNode.NodeType#ACHIEVEMENT} 节点</li>
     *   <li>全局冷却检查</li>
     *   <li>单成就冷却检查</li>
     *   <li>未解锁成就检查（需 {@code share.allow-locked=true}）</li>
     *   <li>更新冷却时间</li>
     *   <li>构建悬停提示（含成就详情）</li>
     *   <li>构建广播消息（按解锁状态选择模板）</li>
     *   <li>按 {@code tipsOthers} 偏好广播到在线玩家</li>
     * </ol>
     * </p>
     *
     * @param sharer 分享成就的玩家
     * @param node   被分享的成就节点
     */
    public void tryShare(@NotNull Player sharer, @NotNull AchievementNode node) {
        // 1. 检查是否启用
        if (!plugin.getConfigManager().isShareEnabled()) {
            sendToPlayer(sharer, plugin.getMessages().getMessage("SHARE_DISABLED",
                    "<red>成就分享功能已禁用.</red>"));
            return;
        }

        // 2. 检查权限
        if (!sharer.hasPermission("bakaachievements.share")) {
            sendToPlayer(sharer, plugin.getMessages().getMessage("SHARE_NO_PERMISSION",
                    "<red>你没有分享成就的权限.</red>"));
            return;
        }

        // 3. 仅支持 ACHIEVEMENT 和 MIXED 类型
        if (node.nodeType() != AchievementNode.NodeType.ACHIEVEMENT
                && node.nodeType() != AchievementNode.NodeType.MIXED) return;
        AchievementNode achNode = node;

        long now = System.currentTimeMillis();
        boolean bypassCooldown = sharer.hasPermission("bakaachievements.share.cooldown");

        // 4. 全局冷却检查
        long globalCdMs = plugin.getConfigManager().getShareGlobalCooldownSeconds() * 1000L;
        UUID sharerUuid = sharer.getUniqueId();
        if (!bypassCooldown) {
            Long lastGlobal = globalCooldown.get(sharerUuid);
            if (lastGlobal != null && now - lastGlobal < globalCdMs) {
                long remainingMs = globalCdMs - (now - lastGlobal);
                sendToPlayer(sharer, plugin.getMessages().shareOnCooldown(toCooldownSeconds(remainingMs)));
                return;
            }
        }

        // 5. 单成就冷却检查
        long nodeCdMs = plugin.getConfigManager().getShareCooldownSeconds() * 1000L;
        Map<String, Long> nodeMap = perNodeCooldown.computeIfAbsent(sharerUuid,
                k -> new ConcurrentHashMap<>());
        if (!bypassCooldown) {
            Long lastNode = nodeMap.get(achNode.nodePath());
            if (lastNode != null && now - lastNode < nodeCdMs) {
                long remainingMs = nodeCdMs - (now - lastNode);
                sendToPlayer(sharer, plugin.getMessages().shareOnCooldown(toCooldownSeconds(remainingMs)));
                return;
            }
        }

        // 6. 检查是否允许分享未解锁成就
        PlayerAchievementData sharerData = storage.getCached(sharerUuid);
        boolean unlocked = sharerData != null && sharerData.isUnlocked(achNode.nodePath());
        if (!unlocked && !plugin.getConfigManager().isShareAllowLocked()) {
            sendToPlayer(sharer, plugin.getMessages().getMessage("SHARE_LOCKED_NOT_ALLOWED",
                    "<red>你尚未达成此成就, 无法分享.</red>"));
            return;
        }

        // 7. 更新冷却
        if (!bypassCooldown) {
            globalCooldown.put(sharerUuid, now);
            nodeMap.put(achNode.nodePath(), now);
        }

        // 8. 构建悬停组件
        Component hoverComponent = buildHoverComponent(achNode, sharerData, sharer);

        // 9. 构建广播消息
        Component broadcastMsg;
        if (unlocked) {
            broadcastMsg = buildUnlockedMessage(sharer, achNode, sharerData, hoverComponent);
        } else {
            broadcastMsg = buildLockedMessage(sharer, achNode, hoverComponent);
        }

        // 10. 按接收者偏好广播
        broadcast(sharer, broadcastMsg);
    }

    // ── 广播 ───────────────────────────────────────────────────

    /**
     * 向在线玩家广播分享消息。
     * <p>
     * 分享者本人固定收到消息；其他玩家按 {@code tipsOthers} 偏好过滤。
     * </p>
     *
     * @param sharer 分享者
     * @param msg    已构建完成的 Adventure Component
     */
    private void broadcast(@NotNull Player sharer, @NotNull Component msg) {
        UUID sharerUuid = sharer.getUniqueId();

        for (Player receiver : Bukkit.getOnlinePlayers()) {
            boolean isSelf = receiver.getUniqueId().equals(sharerUuid);

            if (isSelf) {
                receiver.sendMessage(msg); // 自己固定接收
                continue;
            }

            // 按他人偏好过滤
            PlayerAchievementData recvData = storage.getCached(receiver.getUniqueId());
            boolean receive = (recvData == null) || recvData.isTipsOthers();
            if (receive) {
                receiver.sendMessage(msg);
            }
        }
    }

    // ── 消息构建 ───────────────────────────────────────────────

    /**
     * 构建已解锁成就的分享消息。
     */
    private @NotNull Component buildUnlockedMessage(@NotNull Player sharer,
                                                      @NotNull AchievementNode node,
                                                      @NotNull PlayerAchievementData data,
                                                      @NotNull Component hoverComponent) {
        long achieveTime = data.getStatus(node.nodePath()).achieveTime();
        String timeStr = formatTime(achieveTime);

        String template = plugin.getMessages().shareUnlocked()
                .replace("{prefix}", plugin.getMessages().getMessage("PREFIX", ""))
                .replace("{time}", timeStr)
                .replace("{progress_bar}", renderShareProgressBar(1.0))
                .replace("{percent}", renderSharePercent(1.0));
        template = resolvePlayerPlaceholders(template, sharer);

        return buildMessageWithHover(template, hoverComponent, node);
    }

    /**
     * 构建未解锁成就的分享消息——基于所有条件的真实平均进度。
     */
    private @NotNull Component buildLockedMessage(@NotNull Player sharer,
                                                    @NotNull AchievementNode node,
                                                    @NotNull Component hoverComponent) {
        int condCount = node.conditionGroup().conditions().size();

        // 无条件限制的成就（包括所有原版成就）——不显示进度条
        if (condCount == 0) {
            String template = plugin.getMessages().getMessage("SHARE_ACHIEVEMENT_LOCKED_NO_CONDITION",
                    "{prefix} <gold>{player_displayname}</gold> <gray>分享了成就</gray> {__HOVER__}");
            template = template
                    .replace("{prefix}", plugin.getMessages().getMessage("PREFIX", ""));
            template = resolvePlayerPlaceholders(template, sharer);
            return buildMessageWithHover(template, hoverComponent, node);
        }

        // 计算所有条件的平均进度
        double totalProgress = 0.0;
        for (Condition cond : node.conditionGroup().conditions()) {
            String target = resolvePapi(sharer, cond.target());
            String current = resolvePapi(sharer, cond.current());
            boolean passed = ConditionEvaluator.evaluateResolved(sharer, cond, target, current);
            totalProgress += ConditionEvaluator.progress(cond, target, current, passed);
        }
        double avgProgress = totalProgress / condCount;

        String template = plugin.getMessages().shareLocked()
                .replace("{prefix}", plugin.getMessages().getMessage("PREFIX", ""))
                .replace("{progress_bar}", renderShareProgressBar(avgProgress))
                .replace("{percent}", renderSharePercent(avgProgress));
        template = resolvePlayerPlaceholders(template, sharer);

        return buildMessageWithHover(template, hoverComponent, node);
    }

    private String renderShareProgressBar(double progress) {
        ProgressBarUtil.BarStyle barStyle = plugin.getConfigManager().getProgressBarStyle();
        int barLen = barStyle.length();
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        int filledLen = (int) Math.round(clamped * barLen);
        int emptyLen = barLen - filledLen;

        var barColors = plugin.getConfigManager().getProgressBarColors();
        String filledStr = repeatChar(barStyle.filled(), filledLen);
        String emptyStr  = repeatChar(barStyle.empty(), emptyLen);

        return "<" + barColors.leftColor()    + ">" + barStyle.left()  + "</" + barColors.leftColor()    + ">"
             + "<" + barColors.filledColor()  + ">" + filledStr        + "</" + barColors.filledColor()  + ">"
             + "<" + barColors.emptyColor()   + ">" + emptyStr         + "</" + barColors.emptyColor()   + ">"
             + "<" + barColors.rightColor()   + ">" + barStyle.right() + "</" + barColors.rightColor()   + ">";
    }

    private String renderSharePercent(double progress) {
        var barColors = plugin.getConfigManager().getProgressBarColors();
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        return "<" + barColors.percentColor() + ">" + String.format("%.2f%%", clamped * 100.0)
                + "</" + barColors.percentColor() + ">";
    }

    /** 主线程安全解析 PAPI 占位符 */
    private String resolvePapi(Player player, String text) {
        if (text == null || text.isEmpty() || !text.contains("%")) return text != null ? text : "";
        try {
            if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            }
        } catch (Exception ignored) {}
        return text;
    }

    /** 重复字符串首字符 n 次 */
    private static String repeatChar(String pattern, int count) {
        if (count <= 0 || pattern == null || pattern.isEmpty()) return "";
        char ch = pattern.charAt(0);
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) sb.append(ch);
        return sb.toString();
    }

    private static long toCooldownSeconds(long remainingMs) {
        return Math.max(1L, (remainingMs + 999L) / 1000L);
    }

    private @NotNull Component miniOrPlain(@NotNull String miniMessageStr) {
        try {
            return plugin.getMiniMessage().deserialize(miniMessageStr);
        } catch (Exception e) {
            return Component.text(miniMessageStr);
        }
    }

    /**
     * 处理消息模板中的 {@code {__HOVER__}} 占位符。
     * <p>
     * 将模板按 {@code {__HOVER__}} 切分为前、后两部分，分别用 MiniMessage 解析，
     * 中间插入带 {@link HoverEvent} 的成就名组件，最后用 {@link Component#append} 合并。
     * </p>
     * <p>
     * 若模板中不包含 {@code {__HOVER__}}，退化为纯 MiniMessage 解析。
     * </p>
     *
     * @param template       消息模板（含 {__HOVER__} 或纯 MiniMessage 字符串）
     * @param hoverComponent 悬停展示的成就详情组件
     * @param achievement    成就对象（用于提取纯文本显示名）
     * @return 合并后的完整消息 Component
     */
    private @NotNull Component buildMessageWithHover(@NotNull String template,
                                                       @NotNull Component hoverComponent,
                                                       @NotNull AchievementNode node) {
        int hoverIdx = template.indexOf(HOVER_PLACEHOLDER);
        if (hoverIdx < 0) {
            // 模板不含 __HOVER__ 占位符，退化为纯 MiniMessage 解析
            try {
                return plugin.getMiniMessage().deserialize(template);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "分享消息 MiniMessage 解析失败: " + template, e);
                return Component.text(template);
            }
        }

        String before = template.substring(0, hoverIdx);
        String after = template.substring(hoverIdx + HOVER_PLACEHOLDER.length());

        // 用 MiniMessage 解析前半段和后半段
        Component beforeComp;
        Component afterComp;
        try {
            beforeComp = plugin.getMiniMessage().deserialize(before);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "分享消息前半段 MiniMessage 解析失败: " + before, e);
            beforeComp = Component.text(before);
        }
        try {
            afterComp = plugin.getMiniMessage().deserialize(after);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "分享消息后半段 MiniMessage 解析失败: " + after, e);
            afterComp = Component.text(after);
        }

        // 提取成就纯文本显示名（作为 hover 触发器文字）
        String plainDisplayName = toPlainText(node.display());

        // 构建带 hover 事件的成就名触发器组件（点击导航到该成就所属分类）
        String clickCmd = "/bac " + node.nodePath();
        Component hoverTrigger = Component.text("[" + plainDisplayName + "]")
                .color(SHARE_HIGHLIGHT_COLOR)
                .hoverEvent(HoverEvent.showText(hoverComponent))
                .clickEvent(ClickEvent.runCommand(clickCmd));

        // 合并：前半 + hover 触发器 + 后半
        return Component.empty().append(beforeComp).append(hoverTrigger).append(afterComp);
    }

    // ── 悬停提示构建 ───────────────────────────────────────────

    /**
     * 构建成就分享时的悬停提示（HoverEvent 内容）。
     * <p>
     * 悬停卡片格式（模仿 ItemFactory Lore）：
     * <pre>
     *   ┌─────────────────┐
     *   │  [成就显示名]    │
     *   │  描述第1行       │
     *   │  描述第2行       │
     *   │  ─────           │
     *   │  已达成 / 未达成  │
     *   └─────────────────┘
     * </pre>
     * </p>
     *
     * @param node   成就/混合节点
     * @param data   分享者玩家数据（可为 null）
     * @return 悬停提示 Component
     */
    private @NotNull Component buildHoverComponent(@NotNull AchievementNode node,
                                                     PlayerAchievementData data,
                                                     @NotNull Player sharer) {
        List<Component> lines = new ArrayList<>();
        var msgs = plugin.getMessages();

        // 标题（支持 {player_displayname}/{player}）
        lines.add(miniOrPlain(resolvePlayerPlaceholders(msgs.hoverTitle(), sharer)));

        // 成就显示名（MiniMessage 解析，支持占位符）
        String resolvedDisplay = resolvePlayerPlaceholders(node.display(), sharer);
        try {
            lines.add(plugin.getMiniMessage().deserialize("<bold>" + resolvedDisplay + "</bold>"));
        } catch (Exception e) {
            lines.add(Component.text(resolvedDisplay));
        }

        // 描述行（支持占位符 + PAPI）
        if (node.descriptions() != null && !node.descriptions().isEmpty()) {
            lines.add(Component.empty());
            for (String desc : node.descriptions()) {
                if (desc == null || desc.isBlank()) continue;
                String resolved = resolvePlaceholders(desc, sharer);
                try {
                    lines.add(plugin.getMiniMessage().deserialize("<!italic><gray>" + resolved + "</gray>"));
                } catch (Exception e) {
                    lines.add(MiniMessageUtil.parseGray(resolved, plugin.getMiniMessage()));
                }
            }
        }

        // 条件要求（支持 {current}/{target} + PAPI）
        if (!node.conditionGroup().isEmpty()) {
            lines.add(Component.empty());
            lines.add(miniOrPlain(resolvePlayerPlaceholders(msgs.hoverRequirementsHeader(), sharer)));
            for (var cond : node.conditionGroup().conditions()) {
                String display = cond.display();
                String target = resolvePapi(sharer, cond.target());
                String current = resolvePapi(sharer, cond.current());
                if (display == null || display.isBlank()) {
                    display = current + " / " + target;
                } else {
                    display = resolvePlaceholders(display, sharer)
                            .replace("{current}", current)
                            .replace("{target}", target);
                }
                lines.add(Component.text("  • " + display).color(TextColor.color(0xcccccc)));
            }
        }

        // 分隔线
        lines.add(Component.empty());
        lines.add(miniOrPlain(resolvePlayerPlaceholders(msgs.hoverSeparator(), sharer)));

        // 达成状态
        if (data != null && data.isUnlocked(node.nodePath())) {
            long achieveTime = data.getStatus(node.nodePath()).achieveTime();
            String timeStr = formatTime(achieveTime);
            lines.add(miniOrPlain(resolvePlayerPlaceholders(
                    msgs.hoverUnlockedTime().replace("{time}", timeStr), sharer)));
        } else {
            int condCount = node.conditionGroup().conditions().size();
            if (condCount == 0) {
                lines.add(miniOrPlain(resolvePlayerPlaceholders(msgs.hoverLockedNoCondition(), sharer)));
            } else {
                lines.add(miniOrPlain(resolvePlayerPlaceholders(
                        msgs.hoverLockedWithCount().replace("{count}", String.valueOf(condCount)),
                        sharer)));
            }
        }

        return Component.join(JoinConfiguration.separator(Component.newline()), lines);
    }

    /** 解析文本中的玩家占位符和 %xxx% PAPI 变量 */
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

    // ── 工具方法 ───────────────────────────────────────────────

    /**
     * 向单个玩家发送 MiniMessage 格式的消息。
     *
     * @param player         目标玩家
     * @param miniMessageStr MiniMessage 格式的字符串
     */
    private void sendToPlayer(@NotNull Player player, @NotNull String miniMessageStr) {
        String resolved = miniMessageStr.replace("{prefix}",
                plugin.getMessages().getMessage("PREFIX", ""));
        try {
            player.sendMessage(plugin.getMiniMessage().deserialize(resolved));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "向玩家发送消息失败: " + resolved, e);
            player.sendMessage(Component.text(resolved));
        }
    }

    /**
     * 将 MiniMessage 格式字符串转为纯文本。
     *
     * @param miniMessageStr MiniMessage 格式字符串
     * @return 纯文本字符串；解析失败时返回原字符串
     */
    private @NotNull String toPlainText(@NotNull String miniMessageStr) {
        try {
            Component c = plugin.getMiniMessage().deserialize(miniMessageStr);
            return PlainTextComponentSerializer.plainText().serialize(c);
        } catch (Exception e) {
            return miniMessageStr;
        }
    }

    /**
     * 格式化时间戳为可读字符串。
     *
     * @param timestampMs 时间戳（毫秒），≤0 时返回"未知"
     * @return 格式化后的时间字符串
     */
    private @NotNull String formatTime(long timestampMs) {
        String unknown = plugin.getMessages().timeUnknown();
        if (timestampMs <= 0) return unknown;
        try {
            return DATE_FORMAT.get().format(new Date(timestampMs));
        } catch (Exception e) {
            return unknown;
        }
    }
}
