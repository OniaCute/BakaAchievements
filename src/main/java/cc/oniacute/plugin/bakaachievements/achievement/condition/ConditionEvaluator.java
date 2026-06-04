package cc.oniacute.plugin.bakaachievements.achievement.condition;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * 条件评估器——对单个 {@link Condition} 执行判断。
 * <p>
 * 支持数字比较、字符串比较、权限检查，以及取反逻辑。
 * PAPI 解析由调用方在主线程完成后传入已解析值，本类仅做纯逻辑判断。
 * </p>
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {}

    /**
     * 使用已解析的 target/current 评估条件（PAPI 已由调用方在主线程解析）。
     *
     * @param player          目标玩家
     * @param condition       条件
     * @param targetResolved  已解析的 target
     * @param currentResolved 已解析的 current
     * @return {@code true} 表示条件满足
     */
    public static boolean evaluateResolved(Player player, Condition condition,
                                           String targetResolved, String currentResolved) {
        try {
            boolean result = evaluateRaw(player, condition.op(), targetResolved, currentResolved);
            return condition.negate() != result;
        } catch (Exception e) {
            BakaAchievements.getInstance().getLogger()
                    .log(Level.WARNING, "条件评估异常: " + condition, e);
            return false;
        }
    }

    // ── 内部评估逻辑（纯函数，线程安全） ──────────────────

    private static boolean evaluateRaw(Player player, String op, String target, String current) {
        return switch (op) {
            case "="  -> evaluateEqual(target, current, false, false);
            case ">=" -> evaluateNumeric(target, current, ">=", false);
            case "<=" -> evaluateNumeric(target, current, "<=", false);
            case ">"  -> evaluateNumeric(target, current, ">", false);
            case "<"  -> evaluateNumeric(target, current, "<", false);
            case "|=" -> evaluateEqual(target, current, true, false);
            case "|>=" -> evaluateNumeric(target, current, ">=", true);
            case "|<=" -> evaluateNumeric(target, current, "<=", true);
            case "|>" -> evaluateNumeric(target, current, ">", true);
            case "|<" -> evaluateNumeric(target, current, "<", true);
            case "hasPermission" -> player.hasPermission(target);
            default -> false;
        };
    }

    private static boolean evaluateEqual(String target, String current, boolean ignoreCase, boolean round) {
        Double tNum = parseDouble(target);
        Double cNum = parseDouble(current);
        if (tNum != null && cNum != null) {
            if (round) cNum = (double) Math.round(cNum);
            return Double.compare(tNum, cNum) == 0;
        }
        return ignoreCase ? target.equalsIgnoreCase(current) : target.equals(current);
    }

    private static boolean evaluateNumeric(String target, String current, String op, boolean round) {
        Double tNum = parseDouble(target);
        Double cNum = parseDouble(current);
        if (tNum == null || cNum == null) return false;
        if (round) cNum = (double) Math.round(cNum);

        return switch (op) {
            case ">=" -> cNum >= tNum;
            case "<=" -> cNum <= tNum;
            case ">"  -> cNum > tNum;
            case "<"  -> cNum < tNum;
            default   -> false;
        };
    }

    private static Double parseDouble(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
