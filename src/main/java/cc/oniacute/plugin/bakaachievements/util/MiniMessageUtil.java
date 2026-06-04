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
     * 将 Component 序列化为 Legacy 颜色码字符串。
     *
     * @param component Component 实例
     * @return Legacy 颜色码字符串（使用 § 作为颜色符号）
     */
    public static String componentToLegacy(@NotNull Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}
