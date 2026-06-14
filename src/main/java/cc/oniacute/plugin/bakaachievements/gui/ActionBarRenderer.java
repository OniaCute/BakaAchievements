package cc.oniacute.plugin.bakaachievements.gui;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementNode;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementRegistry;
import cc.oniacute.plugin.bakaachievements.config.Messages;
import cc.oniacute.plugin.bakaachievements.util.MiniMessageUtil;
import cc.oniacute.plugin.bakaachievements.util.PathUtil;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Renders the fixed bottom action bar for achievement menus.
 */
public final class ActionBarRenderer {

    public static final int SLOT_BACK = 45;
    public static final int SLOT_FILL1 = 46;
    public static final int SLOT_FILL2 = 47;
    public static final int SLOT_FILL3 = 48;
    public static final int SLOT_STATS = 49;
    public static final int SLOT_SPECIAL = 50;
    public static final int SLOT_FILL4 = 51;
    public static final int SLOT_PAGE_PREV = 52;
    public static final int SLOT_NEXT = 53;

    private final BakaAchievements plugin;
    private final AchievementRegistry registry;
    private final ItemFactory itemFactory;
    private final Messages messages;

    public ActionBarRenderer(BakaAchievements plugin,
                             AchievementRegistry registry,
                             ItemFactory itemFactory) {
        this.plugin = plugin;
        this.registry = registry;
        this.itemFactory = itemFactory;
        this.messages = plugin.getMessages();
    }

    public void render(Inventory inv, org.bukkit.entity.Player player, String currentPath,
                       int page, int totalPages, int done, int total, String searchStackPrefix) {
        ItemStack filler = itemFactory.fillerPane(readActionBarMaterial("filler",
                Material.GRAY_STAINED_GLASS_PANE));
        int currentPage = page + 1;

        if (plugin.getMenuStack().peek(player).isPresent()) {
            String target = describeStackTarget(plugin.getMenuStack().peek(player).orElse(""),
                    searchStackPrefix);
            inv.setItem(SLOT_BACK, itemFactory.backButton(
                    messages.guiActionBackName(),
                    replacePagePlaceholders(messages.guiActionBackLore(), currentPage, totalPages, target)));
        } else {
            inv.setItem(SLOT_BACK, filler);
        }

        inv.setItem(SLOT_FILL1, filler);
        inv.setItem(SLOT_FILL2, filler);
        inv.setItem(SLOT_FILL3, filler);

        Material statsMat = readActionBarMaterial("stats", Material.BOOK);
        String statsTitle = statsTitle(currentPath);
        String statsLore = messages.guiActionStatsLore()
                .replace("{path}", currentPath.equals(PathUtil.ROOT) ? messages.guiRootDisplay() : currentPath)
                .replace("{current}", String.valueOf(currentPage))
                .replace("{root_hint}", messages.guiActionRootHint())
                .replace("{share_hint}", messages.guiActionShareHint());
        inv.setItem(SLOT_STATS, itemFactory.statsButton(statsMat, statsTitle, done, total, statsLore));

        inv.setItem(SLOT_SPECIAL, itemFactory.fillerPane(readActionBarMaterial("special",
                Material.GRAY_STAINED_GLASS_PANE)));
        inv.setItem(SLOT_FILL4, filler);

        if (page > 0) {
            inv.setItem(SLOT_PAGE_PREV, itemFactory.prevButton(
                    messages.guiActionPrevName(),
                    replacePagePlaceholders(messages.guiActionPrevLore(), currentPage, totalPages, String.valueOf(page))));
        } else {
            inv.setItem(SLOT_PAGE_PREV, filler);
        }

        if (page + 1 < totalPages) {
            inv.setItem(SLOT_NEXT, itemFactory.nextButton(
                    messages.guiActionNextName(),
                    replacePagePlaceholders(messages.guiActionNextLore(), currentPage, totalPages, String.valueOf(page + 2))));
        } else {
            inv.setItem(SLOT_NEXT, filler);
        }
    }

    private String statsTitle(String currentPath) {
        if (currentPath.equals(PathUtil.ROOT)) {
            return messages.guiTitleAchievements();
        }
        AchievementNode node = registry.getNode(currentPath).orElse(null);
        return node != null ? node.display() : currentPath;
    }

    private String describeStackTarget(String path, String searchStackPrefix) {
        if (path.startsWith(searchStackPrefix)) {
            return messages.guiTitleSearch().replace("{keyword}", path.substring(searchStackPrefix.length()));
        }
        if (MenuStack.ROOT_PATH.equals(path) || PathUtil.ROOT.equals(path)) {
            return messages.guiRootDisplay();
        }
        AchievementNode node = registry.getNode(path).orElse(null);
        return node != null ? toPlainText(node.display()) : path;
    }

    private String replacePagePlaceholders(String text, int currentPage, int totalPages, String targetPage) {
        return text
                .replace("{current}", String.valueOf(currentPage))
                .replace("{total}", String.valueOf(totalPages))
                .replace("{target}", targetPage);
    }

    private String toPlainText(String miniMessageStr) {
        try {
            return PlainTextComponentSerializer.plainText()
                    .serialize(MiniMessageUtil.parse(miniMessageStr, plugin.getMiniMessage()));
        } catch (Exception e) {
            return miniMessageStr;
        }
    }

    private Material readActionBarMaterial(String key, Material defaultMat) {
        try {
            String path = "gui.action-bar." + key + ".material";
            String matStr = plugin.getConfigManager().getConfig().getString(path);
            if (matStr != null && !matStr.isBlank()) {
                Material mat = Material.matchMaterial(matStr);
                if (mat != null && mat.isItem()) return mat;
            }
        } catch (Exception ignored) {}
        return defaultMat;
    }
}
