package cc.oniacute.plugin.bakaachievements.util;

import net.kyori.adventure.text.Component;

/**
 * 进度条工具类——为 GUI Lore 生成字符进度条。
 */
public final class ProgressBarUtil {

    private ProgressBarUtil() {}

    private static final char BAR_FILLED  = '█';
    private static final char BAR_EMPTY   = '░';
    private static final int  BAR_LENGTH  = 20;

    /**
     * 生成固定长度的进度条字符串。
     *
     * @param current 当前值
     * @param total   总值
     * @return 进度条字符串（纯文本）
     */
    public static String bar(int current, int total) {
        if (total <= 0) return repeat(BAR_EMPTY, BAR_LENGTH);
        double ratio = Math.min(1.0, (double) current / total);
        int filled = (int) Math.round(ratio * BAR_LENGTH);
        return repeat(BAR_FILLED, filled) + repeat(BAR_EMPTY, BAR_LENGTH - filled);
    }

    /**
     * 生成百分比字符串。
     */
    public static String percent(int current, int total) {
        if (total <= 0) return "0%";
        return Math.round(100.0 * current / total) + "%";
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }
}
