package cc.oniacute.plugin.bakaachievements.achievement.condition;

/**
 * 单个条件——不可变数据。
 * <p>
 * 条件由操作符（op）、目标值（target）、当前值（current）组成。
 * 支持取反前缀 {@code !}、数字/字符串比较、PlaceholderAPI 占位符解析，
 * 以及特殊操作符 {@code hasPermission}。
 * </p>
 *
 * @param op      操作符（如 {@code "="}, {@code ">="}, {@code "hasPermission"}）
 * @param negate  是否取反（以 {@code !} 前缀标识）
 * @param target  目标值（PAPI 占位符或固定值）
 * @param current 当前值（PAPI 占位符或固定值；hasPermission 时忽略内容但必须存在）
 */
public record Condition(
        String op,
        boolean negate,
        String target,
        String current
) {}

