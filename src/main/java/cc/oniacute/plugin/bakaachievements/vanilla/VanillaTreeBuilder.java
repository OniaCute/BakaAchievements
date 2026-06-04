package cc.oniacute.plugin.bakaachievements.vanilla;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.Achievement;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementNode;
import cc.oniacute.plugin.bakaachievements.achievement.Category;
import cc.oniacute.plugin.bakaachievements.achievement.condition.ConditionGroup;
import cc.oniacute.plugin.bakaachievements.util.PathUtil;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.advancement.Advancement;
import io.papermc.paper.advancement.AdvancementDisplay;

import java.util.*;

/**
 * 原版成就树构建器——将 Bukkit Advancement 树镜像为虚拟分类。
 * <p>
 * 根分类为 {@code "vanilla"}，按原版 advancement 的 {@code parent()} 关系组织树结构。
 * </p>
 */
public final class VanillaTreeBuilder {

    private final BakaAchievements plugin;

    public VanillaTreeBuilder(BakaAchievements plugin) {
        this.plugin = plugin;
    }

    /**
     * 构建原版成就的虚拟分类树。
     *
     * @return 原版根分类
     */
    public Category build() {
        Iterator<Advancement> iter = Bukkit.advancementIterator();
        Map<String, Advancement> keyed = new HashMap<>();

        while (iter.hasNext()) {
            Advancement adv = iter.next();
            keyed.put(adv.getKey().toString(), adv);
        }

        // 找到所有根 advancement（无 parent 或 parent 不在迭代器中）
        List<Advancement> roots = new ArrayList<>();
        for (Advancement adv : keyed.values()) {
            Advancement parent = adv.getParent();
            if (parent == null || !keyed.containsKey(parent.getKey().toString())) {
                roots.add(adv);
            }
        }

        List<AchievementNode> children = new ArrayList<>();
        for (Advancement root : roots) {
            AchievementNode node = buildNode(root, PathUtil.VANILLA_ROOT, keyed);
            if (node != null) children.add(node);
        }

        return new Category(
                PathUtil.VANILLA_ROOT, "vanilla", "原版成就",
                Material.KNOWLEDGE_BOOK, false, children
        );
    }

    private AchievementNode buildNode(Advancement adv, String parentPath, Map<String, Advancement> all) {
        String key = adv.getKey().toString();
        String nodePath = PathUtil.VANILLA_ROOT + "." + key.replace(':', '.').replace('/', '_');

        // 检查是否有子 advancement
        List<Advancement> children = new ArrayList<>();
        for (Advancement other : all.values()) {
            Advancement parent = other.getParent();
            if (parent != null && parent.getKey().equals(adv.getKey())) {
                children.add(other);
            }
        }

        AdvancementDisplay display = adv.getDisplay();
        if (display == null) {
            // 没有 display 的 advancement 通常没有 UI 意义，仍创建但用默认值
            if (children.isEmpty()) {
                return new Achievement(
                        nodePath, adv.getKey().getKey(), adv.getKey().getKey(),
                        Material.KNOWLEDGE_BOOK, false, false, ConditionGroup.EMPTY
                );
            }
            // 分类
            List<AchievementNode> childNodes = new ArrayList<>();
            for (Advancement child : children) {
                AchievementNode cn = buildNode(child, nodePath, all);
                if (cn != null) childNodes.add(cn);
            }
            return new Category(
                    nodePath, adv.getKey().getKey(), adv.getKey().getKey(),
                    Material.KNOWLEDGE_BOOK, false, childNodes
            );
        }

        Material material = display.icon() != null ? display.icon().getType() : Material.KNOWLEDGE_BOOK;
        String displayName;
        if (display.title() != null) {
            displayName = PlainTextComponentSerializer.plainText().serialize(display.title());
        } else {
            displayName = adv.getKey().getKey();
        }

        if (children.isEmpty()) {
            return new Achievement(
                    nodePath, adv.getKey().getKey(), displayName,
                    material, false, false, ConditionGroup.EMPTY
            );
        }

        // 有子节点 → 分类
        List<AchievementNode> childNodes = new ArrayList<>();
        for (Advancement child : children) {
            AchievementNode cn = buildNode(child, nodePath, all);
            if (cn != null) childNodes.add(cn);
        }

        return new Category(
                nodePath, adv.getKey().getKey(), displayName,
                material, false, childNodes
        );
    }
}
