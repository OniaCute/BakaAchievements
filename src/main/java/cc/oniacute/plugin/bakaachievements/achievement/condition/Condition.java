package cc.oniacute.plugin.bakaachievements.achievement.condition;

import java.util.Map;

/**
 * 单个条件——不可变数据。
 * <p>
 * 条件由操作符（op）、目标值（target）、当前值（current）和展示文本（display）组成。
 * 支持取反前缀 {@code !}、数字/字符串比较、PlaceholderAPI 占位符解析，
 * 以及特殊操作符：{@code hasPermission}、{@code gotAchievement}、{@code gotCategory}、{@code hasItem}。
 * </p>
 *
 * @param op          操作符（如 {@code "="}, {@code "hasPermission"}, {@code "hasItem"}）
 * @param negate      是否取反（以 {@code !} 前缀标识）
 * @param target      目标值（PAPI 占位符或固定值）
 * @param current     当前值（PAPI 占位符或固定值）
 * @param display     条件展示文本（MiniMessage 格式，支持 {current}/{target} 占位符）
 * @param itemOptions hasItem 条件的物品检查选项（null 表示不适用）
 */
public record Condition(
        String op,
        boolean negate,
        String target,
        String current,
        String display,
        ItemConditionOptions itemOptions
) {
    /** 兼容旧版构造，display 默认为空字符串 */
    public Condition(String op, boolean negate, String target, String current) {
        this(op, negate, target, current, "", null);
    }

    /** 兼容 display 构造（无 itemOptions） */
    public Condition(String op, boolean negate, String target, String current, String display) {
        this(op, negate, target, current, display, null);
    }

    /**
     * hasItem 条件的物品检查选项。
     * <p>
     * 所有选项之间为 AND 关系；未设置的可选字段检查时跳过。
     * 若整个 itemOptions 为 null，hasItem 仅进行 Material 比对。
     * </p>
     *
     * @param amount       要求数量（≤0 表示不检查）
     * @param name         要求显示名称（null 表示不检查）
     * @param loreIncluded 要求 lore 包含某文本（null 表示不检查）
     * @param durability   要求耐久度（-1 表示不检查）
     * @param modelData    要求 CustomModelData（null 表示不检查）
     */
    public record ItemConditionOptions(
            int amount,
            String name,
            String loreIncluded,
            int durability,
            Integer modelData
    ) {
        public static final ItemConditionOptions NONE = new ItemConditionOptions(0, null, null, -1, null);

        /** 无任何子选项——仅材质比对 */
        public boolean isEmpty() {
            return amount <= 0 && name == null && loreIncluded == null
                    && durability < 0 && modelData == null;
        }
    }
}
