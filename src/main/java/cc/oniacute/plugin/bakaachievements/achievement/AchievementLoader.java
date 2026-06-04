package cc.oniacute.plugin.bakaachievements.achievement;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.condition.ConditionGroup;
import cc.oniacute.plugin.bakaachievements.achievement.condition.ConditionParser;
import cc.oniacute.plugin.bakaachievements.util.PathUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * 成就加载器——从 {@code achievements.yml} 解析自定义成就树。
 * <p>
 * 职责包括：
 * <ul>
 *   <li>YAML → {@link Achievement} / {@link Category} 对象树</li>
 *   <li>material 字段必填校验（缺失则 ERROR + 跳过）</li>
 *   <li>深度限制（最多 5 层）</li>
 * </ul>
 * </p>
 */
public final class AchievementLoader {

    private final BakaAchievements plugin;

    public AchievementLoader(BakaAchievements plugin) {
        this.plugin = plugin;
    }

    /**
     * 从配置加载成就树。
     *
     * @return 根分类
     */
    public Category load() {
        YamlConfiguration cfg = plugin.getConfigManager().getAchievementsConfig();
        ConfigurationSection rootSection = cfg.getConfigurationSection("customAchievements");
        if (rootSection == null) {
            plugin.getLogger().warning("achievements.yml 中没有 customAchievements 节");
            return new Category(PathUtil.ROOT, "root", "成就", Material.CHEST, false, List.of());
        }

        List<AchievementNode> children = parseChildren(rootSection, PathUtil.ROOT, 0);
        return new Category(PathUtil.ROOT, "root", "成就", Material.CHEST, false, children);
    }

    // ── 递归解析 ──────────────────────────────────────────

    private List<AchievementNode> parseChildren(ConfigurationSection section, String parentPath, int depth) {
        if (depth >= PathUtil.MAX_DEPTH) {
            plugin.getLogger().warning("达到最大嵌套深度 " + PathUtil.MAX_DEPTH + "，跳过: " + parentPath);
            return List.of();
        }

        List<AchievementNode> children = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection childSec = section.getConfigurationSection(key);
            if (childSec == null) continue;

            String type = childSec.getString("type", "").toUpperCase();
            String name = childSec.getString("name", key);
            String nodePath = parentPath.equals(PathUtil.ROOT) ? name : parentPath + "." + name;

            switch (type) {
                case "CATEGORY" -> {
                    Category cat = parseCategory(childSec, nodePath, name, depth + 1);
                    if (cat != null) children.add(cat);
                }
                case "ACHIEVEMENT" -> {
                    Achievement ach = parseAchievement(childSec, nodePath, name);
                    if (ach != null) children.add(ach);
                }
                default -> plugin.getLogger().warning("未知节点类型 '" + type + "' at: " + nodePath);
            }
        }
        return children;
    }

    // ── 解析分类 ──────────────────────────────────────────

    private Category parseCategory(ConfigurationSection sec, String nodePath, String name, int depth) {
        String display = sec.getString("display", name);
        Material material = parseMaterial(sec, nodePath, Material.CHEST, false);
        boolean permission = sec.getBoolean("permission", false);

        ConfigurationSection son = sec.getConfigurationSection("son");
        List<AchievementNode> children = son != null ? parseChildren(son, nodePath, depth) : List.of();

        return new Category(nodePath, name, display, material, permission, children);
    }

    // ── 解析成就 ──────────────────────────────────────────

    private Achievement parseAchievement(ConfigurationSection sec, String nodePath, String name) {
        String display = sec.getString("display", name);
        boolean permission = sec.getBoolean("permission", false);
        boolean auto = sec.getBoolean("auto", false);

        // material 必填校验
        Material material = parseMaterial(sec, nodePath, null, true);
        if (material == null) return null; // 已报错，跳过

        // 条件
        ConfigurationSection condSec = sec.getConfigurationSection("conditions");
        ConditionGroup group = ConditionParser.parse(
                condSec != null ? condSec.getValues(false) : null
        );

        return new Achievement(nodePath, name, display, material, permission, auto, group);
    }

    // ── material 解析与校验 ───────────────────────────────

    /**
     * 从配置节中解析 Material。
     *
     * @param sec      配置节
     * @param nodePath 节点路径（用于日志）
     * @param defaultMat 默认材质（分类可选，成就必填时传 null）
     * @param required 成就节点是否必填
     * @return 解析出的 Material；必填缺失时返回 null
     */
    private Material parseMaterial(ConfigurationSection sec, String nodePath,
                                   Material defaultMat, boolean required) {
        String raw = sec.getString("material");
        if (raw == null || raw.isEmpty()) {
            if (required) {
                plugin.getLogger().log(Level.SEVERE,
                        "成就节点 {0} 未配置 material 字段，已跳过！", nodePath);
                return null;
            }
            return defaultMat;
        }

        Material mat = Material.matchMaterial(raw);
        if (mat == null) {
            if (required) {
                plugin.getLogger().log(Level.SEVERE,
                        "成就节点 {0} 的 material 值 '{1}' 无效，已跳过！", new Object[]{nodePath, raw});
                return null;
            }
            plugin.getLogger().log(Level.WARNING,
                    "分类节点 {0} 的 material 值 '{1}' 无效，使用默认值", new Object[]{nodePath, raw});
            return defaultMat;
        }
        return mat;
    }
}
