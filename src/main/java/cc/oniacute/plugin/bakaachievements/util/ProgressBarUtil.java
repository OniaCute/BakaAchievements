package cc.oniacute.plugin.bakaachievements.util;

/**
 * 进度条工具类——为 GUI Lore 生成可自定义样式的字符进度条。
 */
public final class ProgressBarUtil {

    private ProgressBarUtil() {}

    /** 进度条样式配置（不可变） */
    public record BarStyle(
            String left,
            String filled,
            String empty,
            String right,
            int length
    ) {
        public static final BarStyle DEFAULT = new BarStyle(
                "[", "|", "|", "]", 20
        );
    }

    private static final int DEFAULT_LENGTH = 20;

    // ── 简单版（使用默认样式） ──────────────────────────

    public static String bar(int current, int total) {
        return bar(current, total, BarStyle.DEFAULT);
    }

    /** 使用默认样式生成条（向后兼容） */
    public static String barWithStyle(int current, int total) {
        return bar(current, total, BarStyle.DEFAULT);
    }

    public static String percent(int current, int total) {
        if (total <= 0) return "0.00%";
        double pct = 100.0 * current / total;
        return String.format("%.2f%%", pct);
    }

    // ── 自定义版 ────────────────────────────────────────

    /**
     * 生成自定义样式的进度条字符串。
     * <p>
     * 若 filled/empty 超过 1 个字符，按模式重复填充。
     * 例如 filled="+-" → "+-+-+-+-..."
     * </p>
     */
    public static String bar(int current, int total, BarStyle style) {
        int len = style.length() > 0 ? style.length() : DEFAULT_LENGTH;
        if (total <= 0) {
            return style.left() + repeatString(style.empty(), len) + style.right();
        }
        double ratio = Math.min(1.0, (double) current / total);
        int filledLen = (int) Math.round(ratio * len);
        return style.left()
                + repeatString(style.filled(), filledLen)
                + repeatString(style.empty(), len - filledLen)
                + style.right();
    }

    /**
     * 重复给定字符串 pattern 共 totalChars 个字符的长度。
     * 若 pattern 长度 > 1，按模式循环填充。
     */
    private static String repeatString(String pattern, int totalChars) {
        if (totalChars <= 0 || pattern.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(totalChars);
        int patLen = pattern.length();
        for (int i = 0; i < totalChars; i++) {
            sb.append(pattern.charAt(i % patLen));
        }
        return sb.toString();
    }
}
