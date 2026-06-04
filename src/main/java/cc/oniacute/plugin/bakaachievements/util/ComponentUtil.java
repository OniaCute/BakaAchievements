package cc.oniacute.plugin.bakaachievements.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;

/**
 * Component 构建工具类。
 * <p>
 * 提供便捷的 Adventure Component 创建方法，避免冗长的 Builder 链式调用。
 * </p>
 */
public final class ComponentUtil {

    private ComponentUtil() {
    }

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    // ─────────────────────────────────────────────────────────────────────────
    //  快捷创建
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 创建纯文本 Component。
     *
     * @param text 文本内容
     * @return 纯文本 Component
     */
    public static Component text(@NotNull String text) {
        return Component.text(text);
    }

    /**
     * 创建带颜色的文本 Component。
     *
     * @param text  文本内容
     * @param color 文本颜色
     * @return 带颜色的 Component
     */
    public static Component text(@NotNull String text, @NotNull TextColor color) {
        return Component.text(text, color);
    }

    /**
     * 使用 MiniMessage 快速创建 Component（便捷方法）。
     *
     * @param miniMessage MiniMessage 格式字符串
     * @return 解析后的 Component
     */
    public static Component mini(@NotNull String miniMessage) {
        return MINI_MESSAGE.deserialize(miniMessage);
    }

    /**
     * 创建含多个 Component 的换行分隔消息。
     *
     * @param lines 各行的 Component
     * @return 合并后的 Component
     */
    public static Component join(@NotNull Component... lines) {
        Component result = Component.empty();
        for (int i = 0; i < lines.length; i++) {
            result = result.append(lines[i]);
            if (i < lines.length - 1) {
                result = result.append(Component.newline());
            }
        }
        return result;
    }

    /**
     * 为 Component 追加文字装饰（如粗体、斜体）。
     *
     * @param component  原始 Component
     * @param decoration 装饰样式
     * @return 装饰后的 Component
     */
    public static Component decorate(@NotNull Component component,
                                     @NotNull TextDecoration decoration) {
        return component.decorate(decoration);
    }
}
