package cc.oniacute.plugin.bakaachievements.achievement;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.condition.Condition;
import cc.oniacute.plugin.bakaachievements.achievement.condition.ConditionGroup;
import cc.oniacute.plugin.bakaachievements.util.PathUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * 成就加载器——从 {@code achievements.yml} 的 {@code customAchievements} 节解析自定义成就树。
 *
 * <p>职责包括：
 * <ul>
 *   <li>YAML → {@link Achievement} / {@link Category} 对象树</li>
 *   <li>材质解析：普通材质与 {@code hdb-<id>} 格式（HeadDatabase）</li>
 *   <li>descriptions / flags / commands 字段解析</li>
 *   <li>conditions.X.display 字段解析</li>
 *   <li>成就节点 material 必填校验（缺失/无效则 SEVERE + 跳过）</li>
 *   <li>深度限制（最多 {@link PathUtil#MAX_DEPTH} 层）</li>
 *   <li>单节点解析失败不影响其他节点</li>
 * </ul>
 * </p>
 *
 * <h3>YAML 字段说明</h3>
 * <ul>
 *   <li>{@code desciptions}（注意：原文有拼写错误，按此键名读取）— 字符串列表</li>
 *   <li>{@code flags} — ItemFlag 枚举名列表</li>
 *   <li>{@code commands} — 仅成就节点，字符串列表</li>
 *   <li>{@code conditions.X.display} — 条件展示文本（MiniMessage 格式）</li>
 *   <li>{@code son} — 分类子节点，可为 Map 或空列表 {@code []}</li>
 * </ul>
 */
public final class AchievementLoader {

    private final BakaAchievements plugin;

    public AchievementLoader(BakaAchievements plugin) {
        this.plugin = plugin;
    }

    // ── 入口 ──────────────────────────────────────────────

    /**
     * 从 achievements.yml 加载自定义成就树，返回虚拟根分类。
     *
     * @return 包含所有顶层节点的虚拟根 {@link Category}
     */
    public Category load() {
        YamlConfiguration cfg = plugin.getConfigManager().getAchievementsConfig();
        ConfigurationSection rootSection = cfg.getConfigurationSection("customAchievements");

        if (rootSection == null) {
            plugin.getLogger().warning("achievements.yml 中没有 customAchievements 节");
            return buildEmptyRoot();
        }

        List<AchievementNode> children = parseChildren(rootSection, PathUtil.ROOT, 0);
        return new Category(
                PathUtil.ROOT, "root", "成就",
                Collections.emptyList(),
                Material.CHEST, -1,
                Collections.emptySet(),
                false, children
        );
    }

    // ── 递归解析子节点 ────────────────────────────────────

    /**
     * 遍历 ConfigurationSection 中的所有键，解析为子节点列表。
     *
     * @param section    当前配置节
     * @param parentPath 父节点路径
     * @param depth      当前嵌套深度（从 0 开始，代表根的直接子节点）
     * @return 解析出的子节点列表
     */
    private List<AchievementNode> parseChildren(ConfigurationSection section,
                                                String parentPath, int depth) {
        if (depth >= PathUtil.MAX_DEPTH) {
            plugin.getLogger().warning(
                    "已达最大嵌套深度 " + PathUtil.MAX_DEPTH + "，跳过节点: " + parentPath);
            return Collections.emptyList();
        }

        List<AchievementNode> children = new ArrayList<>();

        for (String key : section.getKeys(false)) {
            ConfigurationSection childSec = section.getConfigurationSection(key);
            if (childSec == null) {
                // 该键不是 ConfigurationSection（例如 son: [] 中 key 为空列表元素），跳过
                continue;
            }

            String type = childSec.getString("type", "").toUpperCase();
            // name 字段：优先读配置中的 name，缺失则退回 YAML key
            String name = childSec.getString("name", key);
            // 路径构建：根节点直接用 name，否则拼接
            String nodePath = PathUtil.ROOT.equals(parentPath)
                    ? name
                    : parentPath + "." + name;

            try {
                switch (type) {
                    case "CATEGORY" -> {
                        Category cat = parseCategory(childSec, nodePath, name, depth + 1);
                        if (cat != null) children.add(cat);
                    }
                    case "ACHIEVEMENT" -> {
                        Achievement ach = parseAchievement(childSec, nodePath, name);
                        if (ach != null) children.add(ach);
                    }
                    case "MIXED" -> {
                        MixedNode mixed = parseMixedNode(childSec, nodePath, name, depth + 1);
                        if (mixed != null) children.add(mixed);
                    }
                    default -> plugin.getLogger().warning(
                            "未知节点类型 '" + type + "'，跳过: " + nodePath);
                }
            } catch (Exception e) {
                // 捕获所有意外异常，保证单节点失败不影响整体加载
                plugin.getLogger().log(Level.SEVERE,
                        "解析节点 " + nodePath + " 时发生意外错误，已跳过", e);
            }
        }

        return children;
    }

    // ── 解析分类节点 ──────────────────────────────────────

    /**
     * 解析 CATEGORY 类型的节点。
     *
     * @param sec      该节点的 ConfigurationSection
     * @param nodePath 节点完整路径
     * @param name     节点内部名称
     * @param depth    递归深度（用于子节点）
     * @return 解析成功的 {@link Category}，失败时返回 {@code null}
     */
    private Category parseCategory(ConfigurationSection sec, String nodePath,
                                   String name, int depth) {
        String display = sec.getString("display", name);
        List<String> descriptions = parseDescriptions(sec);
        boolean permission = sec.getBoolean("permission", false);
        Set<ItemFlag> flags = parseFlags(sec, nodePath);

        // material 可选，缺失/无效时默认 CHEST
        MaterialResult matResult = parseMaterial(sec, nodePath, false);

        // son 节点：可能是 ConfigurationSection（子节点 Map），也可能是空列表 []，也可能不存在
        List<AchievementNode> children = Collections.emptyList();
        Object sonRaw = sec.get("son");
        if (sonRaw instanceof ConfigurationSection sonSec) {
            children = parseChildren(sonSec, nodePath, depth);
        } else if (sonRaw instanceof List<?> list && !list.isEmpty()) {
            // son 为非空列表时，格式异常，给出警告
            plugin.getLogger().warning("分类节点 " + nodePath + " 的 son 字段为列表格式，预期为 Map，已忽略子节点");
        }
        // sonRaw 为 null 或空列表 [] 时，children 保持 emptyList

        return new Category(
                nodePath, name, display,
                descriptions,
                matResult.material(), matResult.hdbId(),
                flags,
                permission, children
        );
    }

    // ── 解析成就节点 ──────────────────────────────────────

    /**
     * 解析 ACHIEVEMENT 类型的节点。
     *
     * @param sec      该节点的 ConfigurationSection
     * @param nodePath 节点完整路径
     * @param name     节点内部名称
     * @return 解析成功的 {@link Achievement}，material 缺失/无效时返回 {@code null}
     */
    private Achievement parseAchievement(ConfigurationSection sec, String nodePath, String name) {
        String display = sec.getString("display", name);
        List<String> descriptions = parseDescriptions(sec);
        boolean permission = sec.getBoolean("permission", false);
        boolean auto = sec.getBoolean("auto", false);
        Set<ItemFlag> flags = parseFlags(sec, nodePath);

        // material 成就节点必填
        MaterialResult matResult = parseMaterial(sec, nodePath, true);
        if (matResult == null) {
            // parseMaterial 已记录 SEVERE 日志，直接跳过
            return null;
        }

        // 条件解析（在 Loader 内部处理，以支持 display 字段）
        ConditionGroup conditionGroup = parseConditionGroup(sec, nodePath);

        // commands 字段
        List<String> commands = parseStringList(sec, "commands");

        return new Achievement(
                nodePath, name, display,
                descriptions,
                matResult.material(), matResult.hdbId(),
                flags,
                permission, auto,
                conditionGroup, commands
        );
    }

    // ── 解析混合节点 ──────────────────────────────────────

    /**
     * 解析 MIXED 类型的节点——兼具成就和分类的属性。
     *
     * @param sec      该节点的 ConfigurationSection
     * @param nodePath 节点完整路径
     * @param name     节点内部名称
     * @param depth    递归深度（用于子节点）
     * @return 解析成功的 {@link MixedNode}，material 缺失/无效时返回 {@code null}
     */
    private MixedNode parseMixedNode(ConfigurationSection sec, String nodePath,
                                     String name, int depth) {
        String display = sec.getString("display", name);
        List<String> descriptions = parseDescriptions(sec);
        boolean permission = sec.getBoolean("permission", false);
        boolean auto = sec.getBoolean("auto", false);
        Set<ItemFlag> flags = parseFlags(sec, nodePath);

        // material 必填（与成就一致）
        MaterialResult matResult = parseMaterial(sec, nodePath, true);
        if (matResult == null) {
            return null;
        }

        // 条件解析
        ConditionGroup conditionGroup = parseConditionGroup(sec, nodePath);

        // commands 字段
        List<String> commands = parseStringList(sec, "commands");

        // son 节点：解析子节点（与分类一致）
        List<AchievementNode> children = Collections.emptyList();
        Object sonRaw = sec.get("son");
        if (sonRaw instanceof ConfigurationSection sonSec) {
            children = parseChildren(sonSec, nodePath, depth);
        } else if (sonRaw instanceof List<?> list && !list.isEmpty()) {
            plugin.getLogger().warning("混合节点 " + nodePath + " 的 son 字段为列表格式，预期为 Map，已忽略子节点");
        }

        return new MixedNode(
                nodePath, name, display,
                descriptions,
                matResult.material(), matResult.hdbId(),
                flags,
                permission, auto,
                conditionGroup, commands,
                children
        );
    }

    // ── 材质解析 ─────────────────────────────────────────

    /**
     * 内部记录类，承载材质解析结果。
     */
    private record MaterialResult(Material material, int hdbId) {}

    /**
     * 从配置节中解析 material 字段，支持普通材质和 {@code hdb-<id>} 格式。
     *
     * <p>解析规则：
     * <ul>
     *   <li>{@code "STONE"} → {@code Material.STONE}，hdbId = -1</li>
     *   <li>{@code "hdb-123"} → {@code Material.PLAYER_HEAD}，hdbId = 123</li>
     *   <li>成就节点缺失/无效 → 记录 SEVERE 并返回 {@code null}</li>
     *   <li>分类节点缺失/无效 → 记录 WARNING 并回退到 CHEST，hdbId = -1</li>
     * </ul>
     *
     * @param sec      配置节
     * @param nodePath 节点路径（日志用）
     * @param required 是否必填（成就节点为 true）
     * @return 解析结果；required=true 且解析失败时返回 {@code null}
     */
    private MaterialResult parseMaterial(ConfigurationSection sec,
                                         String nodePath, boolean required) {
        String raw = sec.getString("material");

        // ── 字段缺失 ──
        if (raw == null || raw.isBlank()) {
            if (required) {
                plugin.getLogger().log(Level.SEVERE,
                        "成就节点 {0} 未配置 material 字段，已跳过！", nodePath);
                return null;
            }
            // 分类节点：使用默认 CHEST
            return new MaterialResult(Material.CHEST, -1);
        }

        // ── HDB 格式：hdb-<id> ──
        if (raw.toLowerCase().startsWith("hdb-")) {
            String idPart = raw.substring(4);
            try {
                int hdbId = Integer.parseInt(idPart);
                return new MaterialResult(Material.PLAYER_HEAD, hdbId);
            } catch (NumberFormatException e) {
                if (required) {
                    plugin.getLogger().log(Level.SEVERE,
                            "成就节点 {0} 的 hdb material 值 ''{1}'' ID 部分无效，已跳过！",
                            new Object[]{nodePath, raw});
                    return null;
                }
                plugin.getLogger().log(Level.WARNING,
                        "分类节点 {0} 的 hdb material 值 ''{1}'' ID 部分无效，使用默认 CHEST",
                        new Object[]{nodePath, raw});
                return new MaterialResult(Material.CHEST, -1);
            }
        }

        // ── 普通材质 ──
        Material mat = Material.matchMaterial(raw);
        if (mat == null) {
            if (required) {
                plugin.getLogger().log(Level.SEVERE,
                        "成就节点 {0} 的 material 值 ''{1}'' 无效，已跳过！",
                        new Object[]{nodePath, raw});
                return null;
            }
            plugin.getLogger().log(Level.WARNING,
                    "分类节点 {0} 的 material 值 ''{1}'' 无效，使用默认 CHEST",
                    new Object[]{nodePath, raw});
            return new MaterialResult(Material.CHEST, -1);
        }

        return new MaterialResult(mat, -1);
    }

    // ── descriptions 解析 ────────────────────────────────

    /**
     * 解析描述字段，支持正确拼写 {@code descriptions} 和历史错误拼写 {@code desciptions}。
     * <p>
     * 读取策略（优先级从高到低）：
     * <ol>
     *   <li>{@code descriptions}（正确拼写，新格式）</li>
     *   <li>{@code desciptions}（历史错误拼写，兼容旧配置，命中时记录 INFO 提示迁移）</li>
     * </ol>
     * </p>
     *
     * @param sec 配置节
     * @return 描述字符串列表；字段不存在时返回空列表
     */
    private List<String> parseDescriptions(ConfigurationSection sec) {
        // 优先读正确拼写
        if (sec.contains("descriptions")) {
            return parseStringList(sec, "descriptions");
        }
        // 兼容历史错误拼写
        if (sec.contains("desciptions")) {
            plugin.getLogger().info(
                    "[AchievementLoader] 检测到旧字段拼写 'desciptions'，建议迁移为 'descriptions'。"
                    + "（节点位于: " + sec.getCurrentPath() + "）");
            return parseStringList(sec, "desciptions");
        }
        return java.util.Collections.emptyList();
    }

    // ── flags 解析 ────────────────────────────────────────

    /**
     * 解析 {@code flags} 字段，将字符串列表转为 {@link ItemFlag} 集合。
     * 无效的 flag 名称记录 WARNING 并跳过。
     *
     * @param sec      配置节
     * @param nodePath 节点路径（日志用）
     * @return ItemFlag 集合；字段不存在时返回空集合
     */
    private Set<ItemFlag> parseFlags(ConfigurationSection sec, String nodePath) {
        List<String> rawFlags = sec.getStringList("flags");
        if (rawFlags.isEmpty()) {
            return Collections.emptySet();
        }

        Set<ItemFlag> result = new HashSet<>();
        for (String flagName : rawFlags) {
            try {
                ItemFlag flag = ItemFlag.valueOf(flagName.toUpperCase());
                result.add(flag);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING,
                        "节点 {0} 的 flags 中存在无效值 ''{1}''，已跳过",
                        new Object[]{nodePath, flagName});
            }
        }
        return result;
    }

    // ── conditions 解析 ──────────────────────────────────

    /**
     * 解析 {@code conditions} 子节，支持每个条件的 {@code display} 字段。
     *
     * <p>此方法在 Loader 内部实现（而非委托给 {@link cc.oniacute.plugin.bakaachievements.achievement.condition.ConditionParser}），
     * 以确保新增的 {@code display} 字段能被正确读取。</p>
     *
     * @param sec      成就节点的 ConfigurationSection
     * @param nodePath 节点路径（日志用）
     * @return 解析出的条件组；无条件时返回 {@link ConditionGroup#EMPTY}
     */
    private ConditionGroup parseConditionGroup(ConfigurationSection sec, String nodePath) {
        ConfigurationSection condSec = sec.getConfigurationSection("conditions");
        if (condSec == null) {
            return ConditionGroup.EMPTY;
        }

        List<Condition> conditions = new ArrayList<>();

        for (String condKey : condSec.getKeys(false)) {
            ConfigurationSection condEntry = condSec.getConfigurationSection(condKey);
            if (condEntry == null) continue;

            // op / negate 解析（以 '!' 前缀表示取反）
            String rawOp = condEntry.getString("type", "=");
            boolean negate = false;
            String op = rawOp;
            if (op.startsWith("!")) {
                negate = true;
                op = op.substring(1);
            }

            String target = condEntry.getString("target", "");
            String current = condEntry.getString("current", "");
            // display 字段：MiniMessage 格式，可为空字符串
            String display = condEntry.getString("display", "");

            // 解析 itemOptions（仅 hasItem 条件使用）
            Condition.ItemConditionOptions itemOpts = null;
            ConfigurationSection optsSec = condEntry.getConfigurationSection("itemOptions");
            if (optsSec != null) {
                int amount = optsSec.getInt("amount", 0);
                String name = optsSec.getString("name");
                String loreIncluded = optsSec.getString("loreIncluded");
                int durability = optsSec.getInt("durability", -1);
                Integer modelData = optsSec.contains("modelData")
                        ? optsSec.getInt("modelData") : null;
                itemOpts = new Condition.ItemConditionOptions(amount, name, loreIncluded, durability, modelData);
            }

            conditions.add(new Condition(op, negate, target, current, display, itemOpts));
        }

        return conditions.isEmpty() ? ConditionGroup.EMPTY : new ConditionGroup(conditions);
    }

    // ── 通用字符串列表解析 ────────────────────────────────

    /**
     * 安全地从配置节读取字符串列表。
     * 若键不存在或值为空列表 {@code []}，返回 {@link Collections#emptyList()}。
     *
     * @param sec 配置节
     * @param key YAML 键名
     * @return 字符串列表（不可变），永远不为 {@code null}
     */
    private List<String> parseStringList(ConfigurationSection sec, String key) {
        if (!sec.contains(key)) {
            return Collections.emptyList();
        }
        List<String> list = sec.getStringList(key);
        // getStringList 已对非列表值做了兼容处理，此处直接返回
        return list.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    // ── 辅助方法 ──────────────────────────────────────────

    /**
     * 构建一个空的虚拟根分类（加载失败时的降级返回值）。
     */
    private Category buildEmptyRoot() {
        return new Category(
                PathUtil.ROOT, "root", "成就",
                Collections.emptyList(),
                Material.CHEST, -1,
                Collections.emptySet(),
                false, Collections.emptyList()
        );
    }
}
