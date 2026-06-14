package cc.oniacute.plugin.bakaachievements.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * MiniMessage 工具类。
 * <p>
 * 封装 Adventure MiniMessage 的常用操作，包括解析、发送消息、与 Legacy 格式互转。
 * </p>
 */
public final class MiniMessageUtil {

    private MiniMessageUtil() {
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  解析
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 将 MiniMessage 字符串解析为 Component。
     *
     * @param input       MiniMessage 格式文本
     * @param miniMessage MiniMessage 实例
     * @return 解析后的 Component
     */
    public static Component parse(@NotNull String input, @NotNull MiniMessage miniMessage) {
        return miniMessage.deserialize(input);
    }

    /**
     * 带 TagResolver 解析 MiniMessage 字符串。
     *
     * @param input       MiniMessage 格式文本
     * @param miniMessage MiniMessage 实例
     * @param resolvers   自定义标签解析器
     * @return 解析后的 Component
     */
    public static Component parse(@NotNull String input, @NotNull MiniMessage miniMessage,
                                  @NotNull TagResolver... resolvers) {
        return miniMessage.deserialize(input, resolvers);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  发送消息
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 向 CommandSender 发送一条 MiniMessage 文本。
     *
     * @param sender      接收者
     * @param input       MiniMessage 格式文本
     * @param miniMessage MiniMessage 实例
     */
    public static void send(@NotNull CommandSender sender, @NotNull String input,
                            @NotNull MiniMessage miniMessage) {
        sender.sendMessage(parse(input, miniMessage));
    }

    /**
     * 向 CommandSender 发送一条 MiniMessage 文本（带 TagResolver）。
     *
     * @param sender      接收者
     * @param input       MiniMessage 格式文本
     * @param miniMessage MiniMessage 实例
     * @param resolvers   自定义标签解析器
     */
    public static void send(@NotNull CommandSender sender, @NotNull String input,
                            @NotNull MiniMessage miniMessage, @NotNull TagResolver... resolvers) {
        sender.sendMessage(parse(input, miniMessage, resolvers));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  格式互转
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 将 Legacy 颜色码（如 {@code &a&lHello}）转换为 MiniMessage 格式。
     *
     * @param legacy Legacy 颜色码字符串
     * @return MiniMessage 格式字符串
     */
    public static String legacyToMiniMessage(@Nullable String legacy) {
        if (legacy == null || legacy.isEmpty()) return "";
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
        return MiniMessage.miniMessage().serialize(component);
    }

    /**
     * 将 Component 序列化为 MiniMessage 字符串。
     *
     * @param component Component 实例
     * @return MiniMessage 格式字符串
     */
    public static String componentToMiniMessage(@NotNull Component component) {
        return MiniMessage.miniMessage().serialize(component);
    }

    /**
     * 安全地将文本渲染为灰色非斜体。
     * <p>
     * 先尝试 MiniMessage 包裹 {@code <!italic><gray>text</gray>}，
     * 若文本内含未闭合的 MiniMessage 标签导致解析失败，降级为
     * 纯文本灰色 Component（斜体关闭）。
     * </p>
     *
     * @param text        原始文本（可能含 MiniMessage 标签）
     * @param miniMessage MiniMessage 解析器
     * @return 灰色非斜体 Component
     */
    public static Component parseGray(@NotNull String text, @NotNull MiniMessage miniMessage) {
        try {
            return miniMessage.deserialize("<!italic><gray>" + text + "</gray>");
        } catch (Exception e) {
            return Component.text(text)
                    .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        }
    }

    /**
     * 将 Component 序列化为 Legacy 颜色码字符串。
     *
     * @param component Component 实例
     * @return Legacy 颜色码字符串（使用 § 作为颜色符号）
     */
    public static String componentToLegacy(@NotNull Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}
