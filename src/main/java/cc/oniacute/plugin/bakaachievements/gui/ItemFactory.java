package cc.oniacute.plugin.bakaachievements.gui;

import cc.oniacute.plugin.bakaachievements.achievement.*;
import cc.oniacute.plugin.bakaachievements.achievement.condition.Condition;
import cc.oniacute.plugin.bakaachievements.achievement.condition.ConditionGroup;
import cc.oniacute.plugin.bakaachievements.config.Messages;
import cc.oniacute.plugin.bakaachievements.util.MiniMessageUtil;
import cc.oniacute.plugin.bakaachievements.util.PathUtil;
import cc.oniacute.plugin.bakaachievements.util.ProgressBarUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * GUI 物品工厂——根据成就数据创建展示用的 ItemStack。
 * <p>
 * 核心规则：
 * <ul>
 *   <li>已达成成就：显示 material + 附魔光效（伪装附魔 + HIDE_ENCHANTS）</li>
 *   <li>未达成：仅显示 material，无光效</li>
 *   <li>分类：显示 material（默认 CHEST），lore 含完成进度</li>
 * </ul>
 * </p>
 */
public final class ItemFactory {

    private final MiniMessage miniMessage;
    private final Messages messages;
    private final ThreadLocal<SimpleDateFormat> dateFormat =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm"));

    public ItemFactory(MiniMessage miniMessage, Messages messages) {
        this.miniMessage = miniMessage;
        this.messages = messages;
    }

    // ── 主入口 ────────────────────────────────────────────

    /**
     * 为 GUI 构建一个展示物品。
     *
     * @param node         成就节点（分类或成就）
     * @param playerData   玩家状态数据（可为 null）
     * @param viewer       查看者（用于 PAPI 解析）
     * @return 构建好的 ItemStack
     */
    public ItemStack build(AchievementNode node, PlayerAchievementData playerData, Player viewer) {
        AchievementNode.NodeType type = node.nodeType();

        if (type == AchievementNode.NodeType.CATEGORY) {
            return buildCategory((Category) node, playerData, viewer);
        } else {
            return buildAchievement((Achievement) node, playerData, viewer);
        }
    }

    // ── 分类物品 ──────────────────────────────────────────

    private ItemStack buildCategory(Category cat, PlayerAchievementData playerData, Player viewer) {
        ItemStack item = new ItemStack(cat.material());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(deserialize(cat.display()));
        List<Component> lore = new ArrayList<>();

        // 进度
        int total = cat.countAchievements();
        int done = countDone(cat, playerData);
        lore.add(Component.empty());
        lore.add(deserialize(
                messages.guiProgress()
                        .replace("{done}", String.valueOf(done))
                        .replace("{total}", String.valueOf(total))
        ));
        lore.add(Component.text(ProgressBarUtil.bar(done, total) + " " + ProgressBarUtil.percent(done, total)));

        // 权限提示
        if (cat.permission()) {
            lore.add(Component.empty());
            lore.add(deserialize(
                    messages.guiPermissionRequired()
                            .replace("{node}", "bakaachievements.category." + cat.nodePath())
            ));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── 成就物品 ──────────────────────────────────────────

    private ItemStack buildAchievement(Achievement ach, PlayerAchievementData playerData, Player viewer) {
        boolean unlocked = playerData != null && playerData.isUnlocked(ach.nodePath());

        ItemStack item = new ItemStack(ach.material());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(deserialize(ach.display()));
        List<Component> lore = new ArrayList<>();

        // 状态行
        if (unlocked) {
            lore.add(deserialize(messages.getMessage("GUI_ACHIEVEMENT_UNLOCKED", "<green>✓ 已达成</green>")));
            PlayerAchievementData.AchievementStatus status = playerData.getStatus(ach.nodePath());
            if (status.achieveTime() > 0) {
                lore.add(deserialize(
                        messages.guiUnlockedTime()
                                .replace("{time}", dateFormat.get().format(new Date(status.achieveTime())))
                ));
            }
        } else {
            lore.add(deserialize(messages.getMessage("GUI_ACHIEVEMENT_LOCKED", "<red>✗ 未达成</red>")));
        }

        // 附魔光效：仅已达成
        if (unlocked) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // 权限提示
        if (ach.permission()) {
            lore.add(Component.empty());
            lore.add(deserialize(
                    messages.guiPermissionRequired()
                            .replace("{node}", "bakaachievements.achievement." + ach.nodePath())
            ));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 为成就详情菜单构建条件进度物品。
     */
    public List<ItemStack> buildConditionItems(Achievement ach, Player viewer) {
        List<ItemStack> items = new ArrayList<>();
        ConditionGroup group = ach.conditionGroup();
        if (group.isEmpty()) return items;

        char index = 'a';
        for (Condition cond : group.conditions()) {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(deserialize("<aqua>条件 " + index + "</aqua>"));

            List<Component> lore = new ArrayList<>();
            lore.add(deserialize("<gray>操作: " + cond.op() + (cond.negate() ? " (取反)" : "") + "</gray>"));
            lore.add(deserialize("<gray>目标: " + cond.target() + "</gray>"));
            lore.add(deserialize("<gray>当前: " + cond.current() + "</gray>"));
            meta.lore(lore);
            item.setItemMeta(meta);
            items.add(item);
            index++;
        }
        return items;
    }

    // ── 工具物品 ──────────────────────────────────────────

    /** 空白填充物 */
    public ItemStack fillEmpty() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    /** 关闭按钮 */
    public ItemStack closeButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(deserialize(messages.guiClose()));
        item.setItemMeta(meta);
        return item;
    }

    /** 上一页按钮 */
    public ItemStack prevPageButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(deserialize(messages.guiPrevPage()));
        item.setItemMeta(meta);
        return item;
    }

    /** 下一页按钮 */
    public ItemStack nextPageButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(deserialize(messages.guiNextPage()));
        item.setItemMeta(meta);
        return item;
    }

    /** 返回上级按钮 */
    public ItemStack backButton() {
        ItemStack item = new ItemStack(Material.OAK_DOOR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(deserialize(messages.guiBack()));
        item.setItemMeta(meta);
        return item;
    }

    /** 面包屑按钮 */
    public ItemStack breadcrumbItem(String path, String display) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(deserialize(display));
        List<Component> lore = new ArrayList<>();
        lore.add(deserialize("<gray>点击跳转到: " + path + "</gray>"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** 搜索结果头 */
    public ItemStack searchHeader(String keyword) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(deserialize(messages.getMessage("SEARCH_HEADER", "<yellow>搜索结果: {keyword}</yellow>")
                .replace("{keyword}", keyword)));
        item.setItemMeta(meta);
        return item;
    }

    // ── 工具方法 ──────────────────────────────────────────

    private Component deserialize(String miniMessageStr) {
        if (miniMessageStr == null || miniMessageStr.isEmpty()) return Component.empty();
        try {
            return MiniMessageUtil.parse(miniMessageStr, miniMessage);
        } catch (Exception e) {
            return Component.text(miniMessageStr);
        }
    }

    private int countDone(Category cat, PlayerAchievementData playerData) {
        if (playerData == null) return 0;
        return (int) getAllAchievements(cat).stream()
                .filter(a -> playerData.isUnlocked(((Achievement) a).nodePath()))
                .count();
    }

    private List<AchievementNode> getAllAchievements(Category cat) {
        List<AchievementNode> result = new ArrayList<>();
        for (AchievementNode child : cat.children()) {
            if (child instanceof Achievement) {
                result.add(child);
            } else if (child instanceof Category subCat) {
                result.addAll(getAllAchievements(subCat));
            }
        }
        return result;
    }
}
