package cc.oniacute.plugin.bakaachievements.vanilla;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.Achievement;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementNode;
import cc.oniacute.plugin.bakaachievements.achievement.Category;
import cc.oniacute.plugin.bakaachievements.achievement.MixedNode;
import cc.oniacute.plugin.bakaachievements.achievement.condition.ConditionGroup;
import cc.oniacute.plugin.bakaachievements.util.PathUtil;
import io.papermc.paper.advancement.AdvancementDisplay;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.advancement.Advancement;

import cc.oniacute.plugin.bakaachievements.config.ConfigManager.VanillaOverride;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 原版成就树构建器——将 Bukkit Advancement 树镜像为虚拟分类。
 * <p>
 * 根分类为 {@code "vanilla"}，按原版 advancement 的 {@code parent()} 关系组织树结构。
 * 支持通过 {@code vanilla.yml} 覆盖显示名称和描述（中文化），
 * 未覆盖的成就回退到 Bukkit API 提供的原版翻译。
 * </p>
 */
public final class VanillaTreeBuilder {

    private final BakaAchievements plugin;
    private final Map<String, VanillaOverride> overrides;
    private static final Map<String, String> VANILLA_PARENT_OVERRIDES = Map.of(
            "minecraft:end/kill_dragon", "minecraft:end/root",
            "minecraft:end/dragon_egg", "minecraft:end/kill_dragon",
            "minecraft:end/enter_end_gateway", "minecraft:end/kill_dragon",
            "minecraft:end/respawn_dragon", "minecraft:end/kill_dragon",
            "minecraft:end/dragon_breath", "minecraft:end/kill_dragon",
            "minecraft:end/find_end_city", "minecraft:end/enter_end_gateway",
            "minecraft:end/elytra", "minecraft:end/find_end_city",
            "minecraft:end/levitate", "minecraft:end/find_end_city"
    );

    public VanillaTreeBuilder(BakaAchievements plugin) {
        this.plugin = plugin;
        this.overrides = plugin.getConfigManager().getVanillaOverrides();
    }

    /**
     * 构建原版成就的虚拟分类树。
     *
     * @return 原版根分类
     */
    public Category build() {
        java.util.Iterator<Advancement> iter = Bukkit.advancementIterator();
        Map<String, Advancement> keyed = new HashMap<>();

        while (iter.hasNext()) {
            Advancement adv = iter.next();
            String key = adv.getKey().toString();
            // 跳过配方解锁成就（minecraft:recipes/...）
            if (key.startsWith("minecraft:recipes/")) continue;
            keyed.put(key, adv);
        }

        // 找到所有根 advancement（无 parent 或 parent 不在迭代器中）
        // 这些是各标签页的根节点（story/root, nether/root, ...），应作为纯 Category
        List<Advancement> roots = new ArrayList<>();
        for (Advancement adv : keyed.values()) {
            String parentKey = effectiveParentKey(adv);
            if (parentKey == null || !keyed.containsKey(parentKey)) {
                roots.add(adv);
            }
        }

        List<AchievementNode> children = new ArrayList<>();
        for (Advancement root : roots) {
            // isTabRoot=true：标签页根节点始终为 CATEGORY
            AchievementNode node = buildNode(root, PathUtil.VANILLA_ROOT, keyed, true);
            if (node != null) children.add(node);
        }

        return new Category(
                PathUtil.VANILLA_ROOT, "vanilla", plugin.getMessages().guiVanillaDisplay(),
                Collections.emptyList(),
                Material.KNOWLEDGE_BOOK, -1, Collections.emptySet(),
                false, children
        );
    }

    private AchievementNode buildNode(Advancement adv, String parentPath,
                                     Map<String, Advancement> all, boolean isTabRoot) {
        String key = adv.getKey().toString();
        String nodePath = PathUtil.vanillaNodePath(key);

        // 收集子 advancement
        List<Advancement> children = new ArrayList<>();
        for (Advancement other : all.values()) {
            String parentKey = effectiveParentKey(other);
            if (adv.getKey().toString().equals(parentKey)) {
                children.add(other);
            }
        }

        AdvancementDisplay display = adv.getDisplay();

        // ── 显示名称：优先 vanilla.yml 覆盖，其次 Bukkit API ──
        String displayName = null;
        VanillaOverride override = overrides.get(key);

        if (override != null && !override.display().isEmpty()) {
            displayName = override.display();
        } else if (display != null && display.title() != null) {
            displayName = PlainTextComponentSerializer.plainText().serialize(display.title());
            if (override == null) {
                plugin.getLogger().warning("未找到原版成就翻译: " + key + " -> " + displayName);
            }
        }
        if (displayName == null) {
            displayName = adv.getKey().getKey();
            plugin.getLogger().warning("未找到原版成就翻译且无法读取标题: " + key + " -> " + displayName);
        }

        // ── 描述：优先 vanilla.yml 覆盖，其次 Bukkit API ──
        List<String> descriptions = new ArrayList<>();
        if (override != null && !override.descriptions().isEmpty()) {
            descriptions.addAll(override.descriptions());
        } else if (display != null && display.description() != null) {
            descriptions.add(PlainTextComponentSerializer.plainText().serialize(display.description()));
        }

        // ── 材质：始终从 Bukkit API 获取 ──
        Material material;
        if (display != null && display.icon() != null) {
            material = display.icon().getType();
        } else {
            material = Material.KNOWLEDGE_BOOK;
        }

        boolean hasDisplay = (display != null);

        if (children.isEmpty()) {
            // 叶子成就 → ACHIEVEMENT
            return new Achievement(
                    nodePath, adv.getKey().getKey(), displayName,
                    descriptions,
                    material, -1, Collections.emptySet(),
                    false, false, ConditionGroup.EMPTY, Collections.emptyList()
            );
        }

        // 构建子节点列表
        List<AchievementNode> childNodes = new ArrayList<>();
        for (Advancement child : children) {
            AchievementNode cn = buildNode(child, nodePath, all, false);
            if (cn != null) childNodes.add(cn);
        }

        // 标签页根节点（story/root, nether/root, ...）作为纯目录展示。
        // "Minecraft", "下界", "末地" 这类一级入口不参与成就完成状态。
        if (isTabRoot) {
            return new Category(
                    nodePath, adv.getKey().getKey(), displayName,
                    descriptions,
                    material, -1, Collections.emptySet(),
                    false, childNodes
            );
        }

        if (hasDisplay) {
            // 有 display + 有 children → MIXED（既是成就又是分类）
            return new MixedNode(
                    nodePath, adv.getKey().getKey(), displayName,
                    descriptions,
                    material, -1, Collections.emptySet(),
                    false, false, ConditionGroup.EMPTY, Collections.emptyList(),
                    childNodes
            );
        }

        // 无 display + 有 children → CATEGORY（纯分类，兜底）
        return new Category(
                nodePath, adv.getKey().getKey(), displayName,
                Collections.emptyList(),
                material, -1, Collections.emptySet(),
                false, childNodes
        );
    }

    private String effectiveParentKey(Advancement advancement) {
        String key = advancement.getKey().toString();
        String override = VANILLA_PARENT_OVERRIDES.get(key);
        if (override != null) return override;

        Advancement parent = advancement.getParent();
        return parent != null ? parent.getKey().toString() : null;
    }
}
