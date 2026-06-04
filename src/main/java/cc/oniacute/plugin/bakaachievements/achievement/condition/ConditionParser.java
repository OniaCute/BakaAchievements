package cc.oniacute.plugin.bakaachievements.achievement.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 条件解析器——将 YAML Map 结构解析为 {@link Condition} 对象。
 */
public final class ConditionParser {

    private ConditionParser() {}

    /**
     * 解析条件 Map（来自 YAML configuration section）。
     *
     * @param conditionsMap YAML 中的 conditions 段
     * @return 条件组
     */
    public static ConditionGroup parse(Map<String, Object> conditionsMap) {
        if (conditionsMap == null || conditionsMap.isEmpty()) {
            return ConditionGroup.EMPTY;
        }

        List<Condition> conditions = new ArrayList<>();
        for (Object value : conditionsMap.values()) {
            if (!(value instanceof Map<?, ?> raw)) continue;

            String rawOp = getString(raw, "type", "=");
            boolean negate = false;
            String op = rawOp;

            if (op.startsWith("!")) {
                negate = true;
                op = op.substring(1);
            }

            String target = getString(raw, "target", "");
            String current = getString(raw, "current", "");

            conditions.add(new Condition(op, negate, target, current));
        }

        return conditions.isEmpty() ? ConditionGroup.EMPTY : new ConditionGroup(conditions);
    }

    @SuppressWarnings("unchecked")
    private static String getString(Map<?, ?> map, String key, String def) {
        Object val = map.get(key);
        return val != null ? val.toString() : def;
    }
}
