package cc.oniacute.plugin.bakaachievements.gui;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.Achievement;
import cc.oniacute.plugin.bakaachievements.achievement.Category;
import cc.oniacute.plugin.bakaachievements.achievement.MixedNode;
import cc.oniacute.plugin.bakaachievements.achievement.PlayerAchievementData;
import cc.oniacute.plugin.bakaachievements.achievement.condition.Condition;
import cc.oniacute.plugin.bakaachievements.config.Messages;
import cc.oniacute.plugin.bakaachievements.hook.HdbHook;
import cc.oniacute.plugin.bakaachievements.util.MiniMessageUtil;
import cc.oniacute.plugin.bakaachievements.util.PathUtil;
import cc.oniacute.plugin.bakaachievements.util.ProgressBarUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * GUI 物品工厂——负责生成菜单中所有 ItemStack。
 * <p>
 * 包括分类图标、成就图标、周圈玻璃板、填充物、导航按钮等。
 * 所有生成的物品都通过 {@link org.bukkit.persistence.PersistentDataContainer} 打上标记，
 * 配合 {@link ItemFlag#HIDE_ATTRIBUTES HIDE_ATTRIBUTES} 和 {@link ItemFlag#HIDE_UNBREAKABLE HIDE_UNBREAKABLE}
 * 防止被误认为普通物品或被移动。
 * </p>
 *
 * <h3>Material 解析规则</h3>
 * <ul>
 *   <li>若 {@code hdbId >= 0} 且 {@code hdbHook.isEnabled()}，调用 {@link HdbHook#getHead(int)}</li>
 *   <li>否则使用 {@code AchievementNode.material()}</li>
 * </ul>
 */
public final class ItemFactory {

    /** 本插件 GUI 物品的 PDC 标记键——"baka_gui":"1" */
    private static final String PDC_KEY = "baka_gui";
    private static final String PDC_VALUE = "1";

    private final MiniMessage miniMessage;
    private final Messages messages;
    private final HdbHook hdbHook;

    /** 时间格式化器（线程安全——SimpleDateFormat 不跨线程共享，每次新建；此处在主线程使用安全） */
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /** PDC NamespacedKey，惰性初始化 */
    private NamespacedKey pdcKey;

    /**
     * 构造物品工厂。
     *
     * @param miniMessage MiniMessage 解析器
     * @param messages    消息配置门面
     * @param hdbHook     HeadDatabase 挂载（可为 null 表示未安装）
     */
    public ItemFactory(MiniMessage miniMessage, Messages messages, HdbHook hdbHook) {
        this.miniMessage = miniMessage;
        this.messages = messages;
        this.hdbHook = hdbHook;
    }

    // ── 内部记录：已解析条件 ───────────────────────────────

    /**
     * 已解析的单个条件——由 {@link MenuController#resolveConditions(Player, Achievement)} 生成，
     * 供本工厂渲染成就 Lore 使用。
     *
     * @param condition 原始条件对象
     * @param targetStr 已解析的 target 值（PAPI 替换后）
     * @param currentStr 已解析的 current 值（PAPI 替换后）
     * @param passed 条件是否已满足
     * @param progress 条件进度 0.0~1.0
     */
    public record ResolvedCondition(
            Condition condition,
            String targetStr,
            String currentStr,
            boolean passed,
            double progress
    ) {}

    // ── 分类物品 ──────────────────────────────────────────

    /**
     * 生成分类的展示物品。
     * <p>
     * Lore 格式：
     * <pre>
     *   显示名称（MiniMessage → Component）
     *   （空行）
     *   描述行1（gray）
     *   描述行2（gray）
     *   ...
     *   （空行）
     *   进度: [||||||||||||||||||||] xx.xx%
     *   （空行）
     *   [无权限时] 需要权限节点才能访问
     *   [有权限时] 点击进入
     * </pre>
     *
     * @param cat        分类节点
     * @param done       已达成成就数
     * @param total      成就总数
     * @param accessible 玩家是否有权限访问此分类
     * @param viewer     查看的玩家
     * @return 分类展示物品
     */
    public ItemStack forCategory(Category cat, int done, int total, boolean accessible, Player viewer) {
        ItemStack item = createBaseItem(cat.material(), cat.hdbId());
        if (item == null || item.getType().isAir()) {
            item = new ItemStack(Material.CHEST);
        }
        boolean completed = total > 0 && done >= total;

        item.editMeta(meta -> {
            applyCommonMeta(meta, cat.flags());

            // 显示名称（关闭默认斜体）
            Component displayName = MiniMessageUtil.parse("<!italic>" + cat.display(), miniMessage);
            meta.displayName(displayName);

            applyGlint(meta, completed);

            // Lore 构建
            List<Component> lore = new ArrayList<>();

            // 空行
            lore.add(Component.empty());

            // 描述行
            List<String> descriptions = cat.descriptions();
            if (descriptions != null && !descriptions.isEmpty()) {
                for (String desc : descriptions) {
                    if (desc != null && !desc.isBlank()) {
                        lore.add(MiniMessageUtil.parseGray(resolvePapi(viewer, desc), miniMessage));
                    }
                }
                lore.add(Component.empty());
            }

            // 进度条
            lore.add(renderProgressBar(done, total));

            // 状态行
            lore.add(Component.empty());
            if (!accessible) {
                lore.add(parseLore(
                        messages.guiPermissionRequired().replace("{node}", cat.nodePath())));
            } else if (total == 0) {
                lore.add(parseLore(messages.guiEmptyCategory()));
            } else {
                lore.add(parseLore(messages.guiClickToEnter()));
            }

            meta.lore(lore);
            markAsGuiItem(meta);
        });

        return item;
    }

    // ── 成就物品 ──────────────────────────────────────────

    /**
     * 生成成就的展示物品。
     * <p>
     * Lore 格式（严格遵循 INTRODUCE.md 规范）：
     * <pre>
     *   显示名称
     *   （空行）
     *   描述行1（gray）
     *   描述行2（gray）
     *   ...
     *   （空行）
     *   &lt;gold&gt;要求:&lt;/gold&gt;
     *   [||||||||||||||||||||] xx.xx% 要求显示信息（{current} {target} 已替换）
     *   [||||||||||||||||||||] xx.xx% 要求显示信息
     *   ...（若无条件：显示"此成就无条件限制"）
     *   （空行）
     *   状态: 已达成! / 未达成 / 你没有权限达成该成就!
     *   [已达成时] 达成时间: yyyy-MM-dd HH:mm:ss
     *   [未达成且auto=false时] → 点击尝试完成成就（仅当 accessible=true）
     * </pre>
     * </p>
     *
     * <h3>原版成就 vs 自定义成就</h3>
 * <ul>
 *   <li><b>原版成就</b>（nodePath 以 {@code vanilla.} 开头）：仅显示介绍文字和达成状态，
 *       不显示条件进度段。</li>
 *   <li><b>自定义成就</b>：完整显示介绍、条件进度条、达成状态。</li>
 * </ul>
 *
 * <h3>颜色规范</h3>
     * <ul>
     *   <li>条件进度 &lt;25% → 红色进度条</li>
     *   <li>25-75% → 黄色进度条</li>
     *   <li>&ge;75% → 绿色进度条</li>
     *   <li>已完成 → 金色进度条</li>
     * </ul>
     *
     * @param ach                成就节点
     * @param data               玩家成就数据（可能为只读的他人数据）
     * @param resolvedConditions 已解析的条件列表（含进度）
     * @param accessible         玩家是否有权限达成此成就
     * @param viewer             查看的玩家
     * @return 成就展示物品
     */
    public ItemStack forAchievement(Achievement ach, PlayerAchievementData data,
                                     List<ResolvedCondition> resolvedConditions,
                                     boolean accessible, Player viewer) {
        return forAchievement(ach, data, resolvedConditions, accessible, viewer, false);
    }

    public ItemStack forAchievement(Achievement ach, PlayerAchievementData data,
                                     List<ResolvedCondition> resolvedConditions,
                                     boolean accessible, Player viewer,
                                     boolean readOnly) {
        ItemStack item = createBaseItem(ach.material(), ach.hdbId());
        if (item == null || item.getType().isAir()) {
            item = new ItemStack(ach.material() != null ? ach.material() : Material.STONE);
        }

        boolean unlocked = data != null && data.isUnlocked(ach.nodePath());
        PlayerAchievementData.AchievementStatus status = data != null
                ? data.getStatus(ach.nodePath())
                : PlayerAchievementData.AchievementStatus.LOCKED;

        item.editMeta(meta -> {
            applyCommonMeta(meta, ach.flags());

            // 显示名称（关闭默认斜体）
            Component displayName = MiniMessageUtil.parse("<!italic>" + ach.display(), miniMessage);
            meta.displayName(displayName);

            applyGlint(meta, unlocked);

            // Lore 构建
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());

            // 描述行
            List<String> descriptions = ach.descriptions();
            if (descriptions != null && !descriptions.isEmpty()) {
                for (String desc : descriptions) {
                    if (desc != null && !desc.isBlank()) {
                        lore.add(MiniMessageUtil.parseGray(resolvePapi(viewer, desc), miniMessage));
                    }
                }
                lore.add(Component.empty());
            }

            // 条件区域（原版成就仅显示介绍和状态，不显示条件）
            boolean isVanilla = ach.nodePath().startsWith(PathUtil.VANILLA_ROOT + ".");
            if (!isVanilla) {
                lore.add(parseLore(messages.guiRequirementsHeader()));

                if (ach.conditionGroup().isEmpty() ||
                        (resolvedConditions == null || resolvedConditions.isEmpty())) {
                    lore.add(parseLore(messages.guiNoConditions()));
                } else {
                    for (ResolvedCondition rc : resolvedConditions) {
                        lore.add(buildConditionLine(rc));
                    }
                }
                lore.add(Component.empty());
            }

            // 状态行
            if (unlocked) {
                lore.add(parseLore(messages.getMessage("GUI_STATUS_UNLOCKED",
                        "<green>✓ 已达成！</green>")));

                // 达成时间
                if (status.achieveTime() > 0) {
                    String timeStr = timeFormat.format(new Date(status.achieveTime()));
                    lore.add(parseLore(
                            messages.guiUnlockedTime().replace("{time}", timeStr)));
                }
            } else if (!accessible) {
                lore.add(parseLore(messages.getMessage("GUI_STATUS_NO_PERMISSION",
                        "<red>✗ 你没有权限达成该成就！</red>")));
            } else {
                lore.add(parseLore(messages.getMessage("GUI_STATUS_LOCKED",
                        "<red>✗ 未达成</red>")));

                // auto=false → 提示点击尝试完成
                if (!readOnly && !ach.auto()) {
                    lore.add(Component.empty());
                    lore.add(parseLore(messages.getMessage("GUI_CLICK_TO_COMPLETE",
                            "<yellow>→ 点击尝试完成成就</yellow>")));
                }
            }

            meta.lore(lore);
            markAsGuiItem(meta);
        });

        return item;
    }

    // ── 混合节点物品 ──────────────────────────────────────

    /**
     * 生成混合节点（MIXED）的展示物品——兼具成就和分类的显示。
     * <p>
     * Lore 格式（在成就格式基础上增加子分类导航提示）：
     * <pre>
     *   显示名称
     *   （空行）
     *   描述行（gray）
     *   ...
     *   （空行）
     *   要求: [进度条] ...
     *   （空行）
     *   状态: 已达成! / 未达成
     *   [已完成时] 左键打开子分类
     *   [未完成时] 左键尝试完成 | 右键打开子分类
     * </pre>
     * </p>
     *
     * @param mixed              混合节点
     * @param data               玩家成就数据
     * @param resolvedConditions 已解析的条件列表
     * @param accessible         玩家是否有权限
     * @param viewer             查看的玩家
     * @param done               已达成数（含自身+子节点）
     * @param total              成就总数
     * @return 混合节点展示物品
     */
    public ItemStack forMixedNode(MixedNode mixed, PlayerAchievementData data,
                                   List<ResolvedCondition> resolvedConditions,
                                   boolean accessible, Player viewer,
                                   int done, int total) {
        return forMixedNode(mixed, data, resolvedConditions, accessible, viewer, done, total, false);
    }

    public ItemStack forMixedNode(MixedNode mixed, PlayerAchievementData data,
                                   List<ResolvedCondition> resolvedConditions,
                                   boolean accessible, Player viewer,
                                   int done, int total, boolean readOnly) {
        ItemStack item = createBaseItem(mixed.material(), mixed.hdbId());
        if (item == null || item.getType().isAir()) {
            item = new ItemStack(mixed.material() != null ? mixed.material() : Material.STONE);
        }

        boolean unlocked = data != null && data.isUnlocked(mixed.nodePath());
        boolean completed = total > 0 && done >= total;
        PlayerAchievementData.AchievementStatus status = data != null
                ? data.getStatus(mixed.nodePath())
                : PlayerAchievementData.AchievementStatus.LOCKED;

        item.editMeta(meta -> {
            applyCommonMeta(meta, mixed.flags());

            // 显示名称（关闭默认斜体）
            Component displayName = MiniMessageUtil.parse("<!italic>" + mixed.display(), miniMessage);
            meta.displayName(displayName);

            applyGlint(meta, unlocked || completed);

            // Lore 构建
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());

            // 描述行
            List<String> descriptions = mixed.descriptions();
            if (descriptions != null && !descriptions.isEmpty()) {
                for (String desc : descriptions) {
                    if (desc != null && !desc.isBlank()) {
                        lore.add(MiniMessageUtil.parseGray(resolvePapi(viewer, desc), miniMessage));
                    }
                }
                lore.add(Component.empty());
            }

            // 条件区域（原版 MIXED 节点仅显示介绍和状态，不显示条件）
            boolean isVanilla = mixed.nodePath().startsWith(PathUtil.VANILLA_ROOT + ".");
            if (!isVanilla) {
                lore.add(parseLore(messages.guiRequirementsHeader()));

                if (mixed.conditionGroup().isEmpty() ||
                        (resolvedConditions == null || resolvedConditions.isEmpty())) {
                    lore.add(parseLore(messages.guiNoConditions()));
                } else {
                    for (ResolvedCondition rc : resolvedConditions) {
                        lore.add(buildConditionLine(rc));
                    }
                }
                lore.add(Component.empty());
            }

            // 状态行
            if (unlocked) {
                lore.add(parseLore(messages.getMessage("GUI_STATUS_UNLOCKED",
                        "<green>✓ 已达成！</green>")));

                if (status.achieveTime() > 0) {
                    String timeStr = timeFormat.format(new Date(status.achieveTime()));
                    lore.add(parseLore(
                            messages.guiUnlockedTime().replace("{time}", timeStr)));
                }
            } else if (!accessible) {
                lore.add(parseLore(messages.getMessage("GUI_STATUS_NO_PERMISSION",
                        "<red>✗ 你没有权限达成该成就！</red>")));
            } else {
                lore.add(parseLore(messages.getMessage("GUI_STATUS_LOCKED",
                        "<red>✗ 未达成</red>")));
                // 导航提示移至下方 MIXED 专用区域，此处不重复显示
            }

            lore.add(Component.empty());

            // 子分类进度条
            if (total > 0) {
                lore.add(parseLore(messages.getMessage("GUI_MIXED_PROGRESS",
                        "<gray>分支进度: <green>{done}</green><gray>/</gray><white>{total}</white></gray>")
                        .replace("{done}", String.valueOf(done))
                        .replace("{total}", String.valueOf(total))));
                lore.add(renderProgressBar(done, total));
                lore.add(Component.empty());
            }

            // MIXED 节点固定交互: 普通菜单左键尝试完成自身成就, 右键打开子分类。
            // check 查看菜单是只读上下文, 只保留右键打开子分类提示。
            lore.add(parseLore(messages.guiMixedRightClick()));
            if (!readOnly && !mixed.auto()) {
                lore.add(parseLore(messages.getMessage("GUI_CLICK_TO_COMPLETE",
                        "<yellow>→ 左键尝试完成成就</yellow>")));
            }

            meta.lore(lore);
            markAsGuiItem(meta);
        });

        return item;
    }

    private void applyGlint(org.bukkit.inventory.meta.ItemMeta meta, boolean enabled) {
        meta.setEnchantmentGlintOverride(enabled);
        if (enabled) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
    }

    // ── 周圈玻璃板 & 填充物品 ─────────────────────────────

    /**
     * 创建周圈玻璃板（无名字，无 Lore，无 tooltip 多余信息）。
     * <p>
     * 物品默认隐藏属性、附魔和牢不可破标志，避免显示多余 tooltip。
     * </p>
     *
     * @param material 材质（从 config.yml {@code gui.border-material} 读取）
     * @return 周圈玻璃板
     */
    public ItemStack borderPane(Material material) {
        if (material == null || !material.isItem()) {
            material = Material.GRAY_STAINED_GLASS_PANE;
        }
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.empty());
            applyCommonMeta(meta, Set.of());
            markAsGuiItem(meta);
        });
        return item;
    }

    /**
     * 创建填充物品（action bar 填充用，无名字无 Lore）。
     *
     * @param material 材质
     * @return 填充物品
     */
    public ItemStack fillerPane(Material material) {
        if (material == null || !material.isItem()) {
            material = Material.GRAY_STAINED_GLASS_PANE;
        }
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.empty());
            applyCommonMeta(meta, Set.of());
            markAsGuiItem(meta);
        });
        return item;
    }

    // ── 导航按钮 ──────────────────────────────────────────

    /**
     * 创建"上一页"按钮。
     *
     * @param lore MiniMessage 格式的提示文本
     * @return 上一页按钮
     */
    public ItemStack prevButton(String lore) {
        Material mat = readActionBarMaterial("prev", Material.ARROW);
        return buildButton(mat, messages.btnPrevPage(), lore);
    }

    public ItemStack prevButton(String name, String lore) {
        Material mat = readActionBarMaterial("prev", Material.ARROW);
        return buildButton(mat, name, lore);
    }

    public ItemStack nextButton(String lore) {
        Material mat = readActionBarMaterial("next", Material.ARROW);
        return buildButton(mat, messages.btnNextPage(), lore);
    }

    public ItemStack nextButton(String name, String lore) {
        Material mat = readActionBarMaterial("next", Material.ARROW);
        return buildButton(mat, name, lore);
    }

    public ItemStack closeButton(String lore) {
        Material mat = readActionBarMaterial("close", Material.BARRIER);
        return buildButton(mat, messages.btnClose(), lore);
    }

    public ItemStack backButton(String lore) {
        Material mat = readActionBarMaterial("back", Material.OAK_DOOR);
        return buildButton(mat, messages.btnBack(), lore);
    }

    public ItemStack backButton(String name, String lore) {
        Material mat = readActionBarMaterial("back", Material.OAK_DOOR);
        return buildButton(mat, name, lore);
    }

    /**
     * 创建 Stats 按钮（显示当前分类名 + 进度条）。
     *
     * @param mat   材质
     * @param title MiniMessage 格式的标题
     * @param done  已完成数
     * @param total 总数
     * @return Stats 按钮（含进度条 lore）
     */
    public ItemStack statsButton(Material mat, String title, int done, int total) {
        return statsButton(mat, title, done, total, messages.guiActionStatsLore());
    }

    public ItemStack statsButton(Material mat, String title, int done, int total, String loreTemplate) {
        if (mat == null || !mat.isItem()) {
            mat = Material.BOOK;
        }
        ItemStack item = new ItemStack(mat);
        item.editMeta(meta -> {
            meta.displayName(MiniMessageUtil.parse("<!italic>" + title, miniMessage));
            List<Component> lore = new ArrayList<>();
            if (loreTemplate == null || loreTemplate.isBlank()) {
                lore.add(renderProgressBar(done, total));
            } else {
                double percent = total > 0 ? 100.0 * done / total : 0.0;
                for (String line : splitLoreLines(loreTemplate)) {
                    if (line.equals("{progress_bar}")) {
                        lore.add(renderProgressBar(done, total));
                        continue;
                    }
                    String resolved = line
                            .replace("{done}", String.valueOf(done))
                            .replace("{total}", String.valueOf(total))
                            .replace("{percent}", String.format("%.2f", percent));
                    lore.add(parseLore(resolved));
                }
            }
            applyCommonMeta(meta, Set.of());
            meta.lore(lore);
            markAsGuiItem(meta);
        });
        return item;
    }

    public ItemStack emptyStateItem(Material mat, String name, String loreText) {
        if (mat == null || !mat.isItem()) {
            mat = Material.PAPER;
        }
        ItemStack item = new ItemStack(mat);
        item.editMeta(meta -> {
            meta.displayName(MiniMessageUtil.parse("<!italic>" + name, miniMessage));
            List<Component> lore = new ArrayList<>();
            for (String line : splitLoreLines(loreText)) {
                lore.add(parseLore(line));
            }
            applyCommonMeta(meta, Set.of());
            meta.lore(lore);
            markAsGuiItem(meta);
        });
        return item;
    }

    // ── 内部工具方法 ──────────────────────────────────────

    /**
     * 根据 HDB 设置和材质创建基础 ItemStack。
     *
     * @param material 成就/分类的材质
     * @param hdbId    HeadDatabase ID（-1 表示不用）
     * @return 基础物品（可能为 HDB 头颅或普通物品）
     */
    private ItemStack createBaseItem(Material material, int hdbId) {
        if (hdbId >= 0 && hdbHook != null && hdbHook.isEnabled()) {
            return hdbHook.getHead(hdbId);
        }
        if (material == null || !material.isItem()) {
            return new ItemStack(Material.STONE);
        }
        return new ItemStack(material);
    }

    /**
     * 构建通用按钮物品。
     */
    private ItemStack buildButton(Material mat, String fallbackName, String loreText) {
        if (mat == null || !mat.isItem()) {
            mat = Material.STONE;
        }
        ItemStack item = new ItemStack(mat);
        item.editMeta(meta -> {
            if (fallbackName != null && !fallbackName.isBlank()) {
                meta.displayName(MiniMessageUtil.parse("<!italic>" + fallbackName, miniMessage));
            } else {
                meta.displayName(Component.empty());
            }
            if (loreText != null && !loreText.isBlank()) {
                List<Component> lore = new ArrayList<>();
                for (String line : splitLoreLines(loreText)) {
                    lore.add(parseLore(line));
                }
                meta.lore(lore);
            }
            applyCommonMeta(meta, Set.of());
            markAsGuiItem(meta);
        });
        return item;
    }

    /**
     * 应用通用 ItemMeta 设置：隐藏属性、牢不可破标志。
     */
    private void applyCommonMeta(ItemMeta meta, Set<ItemFlag> extraFlags) {
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_UNBREAKABLE
        );
        meta.setUnbreakable(true);

        // 应用节点自身的 flags
        if (extraFlags != null && !extraFlags.isEmpty()) {
            for (ItemFlag flag : extraFlags) {
                meta.addItemFlags(flag);
            }
        }
    }

    /**
     * 通过 PersistentDataContainer 标记物品为本插件 GUI 物品。
     * <p>
     * 键：{@code baka_gui}，值：{@code "1"}。
     * 此标记用于事件处理中识别本插件的菜单物品，防止被漏斗移动或误删。
     * </p>
     */
    private void markAsGuiItem(ItemMeta meta) {
        if (pdcKey == null) {
            BakaAchievements instance = BakaAchievements.getInstance();
            if (instance != null) {
                pdcKey = new NamespacedKey(instance, PDC_KEY);
            } else {
                // 降级：使用字符串构造（已弃用但保证兼容）
                pdcKey = NamespacedKey.minecraft(PDC_KEY);
            }
        }
        meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, PDC_VALUE);
    }

    /**
     * 构建单条条件的 Lore 行（包含进度条、百分比、显示文本）。
     * <p>
     * 进度条颜色根据百分比自动选择：
     * </p>
     * <table>
     *   <tr><th>进度</th><th>颜色</th></tr>
     *   <tr><td>&lt; 25%</td><td>红色</td></tr>
     *   <tr><td>25% ~ 74.99%</td><td>黄色</td></tr>
     *   <tr><td>75% ~ 99.99%</td><td>绿色</td></tr>
     *   <tr><td>100%（已通过）</td><td>金色</td></tr>
     * </table>
     *
     * @param rc 已解析条件
     * @return 条件行的 Adventure Component
     */
    private Component buildConditionLine(ResolvedCondition rc) {
        double progress = Math.min(1.0, Math.max(0.0, rc.progress()));
        ProgressBarUtil.BarStyle barStyle = getBarStyle();
        int totalLen = barStyle.length();
        int filledLen = (int) Math.round(progress * totalLen);

        // ── 分段着色进度条 ──
        Component barPart = renderProgressBar(filledLen, totalLen);

        // ── 显示文本（独立 MiniMessage 解析，不受外层颜色限制）──
        String displayText = rc.condition().display();
        if (displayText == null || displayText.isBlank()) {
            displayText = rc.currentStr() + " / " + rc.targetStr();
        } else {
            displayText = displayText
                    .replace("{current}", rc.currentStr())
                    .replace("{target}", rc.targetStr());
        }

        Component displayPart;
        try {
            // 默认白色 + 前方留一个空格间隙
            displayPart = MiniMessageUtil.parse("<!italic><white> " + displayText + "</white>", miniMessage);
        } catch (Exception e) {
            // 若 displayText 含非 MiniMessage 的裸 < > 字符，降级为纯文本
            displayPart = Component.text(" " + displayText)
                    .color(net.kyori.adventure.text.format.NamedTextColor.WHITE)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        }

        return barPart.append(displayPart);
    }

    /**
     * 从 ConfigManager 获取进度条样式配置，带空值守卫。
     *
     * @return BarStyle 实例（若插件未初始化则返回默认样式）
     */
    /**
     * 解析 MiniMessage 并关闭默认斜体。
     */
    private Component parseLore(String miniMessageStr) {
        return MiniMessageUtil.parse("<!italic>" + miniMessageStr, miniMessage);
    }

    private List<String> splitLoreLines(String loreText) {
        if (loreText == null || loreText.isBlank()) {
            return List.of();
        }
        String normalized = loreText.replace("\\n", "\n");
        String[] rawLines = normalized.contains("\n")
                ? normalized.split("\\R", -1)
                : normalized.split("\\|", -1);
        List<String> lines = new ArrayList<>();
        for (String line : rawLines) {
            if (line == null) continue;
            lines.add(line);
        }
        return lines;
    }

    private ProgressBarUtil.BarStyle getBarStyle() {
        BakaAchievements instance = BakaAchievements.getInstance();
        if (instance != null && instance.getConfigManager() != null) {
            return instance.getConfigManager().getProgressBarStyle();
        }
        return ProgressBarUtil.BarStyle.DEFAULT;
    }

    /** 从 ConfigManager 获取进度条颜色方案 */
    private cc.oniacute.plugin.bakaachievements.config.ConfigManager.ProgressBarColors getBarColors() {
        BakaAchievements instance = BakaAchievements.getInstance();
        if (instance != null && instance.getConfigManager() != null) {
            return instance.getConfigManager().getProgressBarColors();
        }
        return cc.oniacute.plugin.bakaachievements.config.ConfigManager.ProgressBarColors.DEFAULT;
    }

    /**
     * 重复字符串中第一个字符 n 次（用于 buildConditionLine 内嵌渲染）。
     */
    private static String repeatChar(String pattern, int count) {
        if (count <= 0 || pattern == null || pattern.isEmpty()) return "";
        char ch = pattern.charAt(0);
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) sb.append(ch);
        return sb.toString();
    }

    /**
     * 渲染分段着色的进度条 Component。
     * <p>
     * 左括号、已完成填充、未完成填充、右括号、百分比各自使用独立的可配颜色，
     * 以 MiniMessage 构建，整体关闭斜体。
     * </p>
     *
     * @param filledLen 已完成长度（0 ~ totalLen）
     * @param totalLen  总长度
     * @return 进度条 Component（含空格 + 百分比）
     */
    private Component renderProgressBar(int filledCount, int totalCount) {
        ProgressBarUtil.BarStyle barStyle = getBarStyle();
        var colors = getBarColors();
        int barLen = barStyle.length();

        // 按比例归一化到配置的条长（始终 barLen 个字符宽）
        double ratio = totalCount > 0 ? (double) filledCount / totalCount : 0.0;
        int filledLen = (int) Math.round(ratio * barLen);
        int emptyLen  = barLen - filledLen;
        String pct = String.format("%.2f%%", ratio * 100.0);

        // 重复字符构建分段
        String filledStr = repeatChar(barStyle.filled(), filledLen);
        String emptyStr  = repeatChar(barStyle.empty(), emptyLen);

        // 分段着色 MiniMessage：<!italic>关斜体 + 各段独立颜色
        String mini = "<!italic>"
                + "<" + colors.leftColor()    + ">" + barStyle.left()  + "</" + colors.leftColor()    + ">"
                + "<" + colors.filledColor()  + ">" + filledStr        + "</" + colors.filledColor()  + ">"
                + "<" + colors.emptyColor()   + ">" + emptyStr         + "</" + colors.emptyColor()   + ">"
                + "<" + colors.rightColor()   + ">" + barStyle.right() + "</" + colors.rightColor()   + ">"
                + " <" + colors.percentColor() + ">" + pct            + "</" + colors.percentColor() + ">";

        try {
            return MiniMessageUtil.parse(mini, miniMessage);
        } catch (Exception e) {
            return Component.text(barStyle.left() + filledStr + emptyStr + barStyle.right() + " " + pct)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        }
    }

    /**
     * 从 config.yml 读取 action bar 按钮材质。
     *
     * @param key        配置键（如 "prev", "next", "close", "back", "stats", "special", "filler"）
     * @param defaultMat 默认材质
     * @return 解析后的材质
     */
    private Material readActionBarMaterial(String key, Material defaultMat) {
        try {
            BakaAchievements instance = BakaAchievements.getInstance();
            if (instance == null || instance.getConfigManager() == null) {
                return defaultMat;
            }
            String path = "gui.action-bar." + key + ".material";
            String matStr = instance.getConfigManager().getConfig().getString(path);
            if (matStr != null && !matStr.isBlank()) {
                Material mat = Material.matchMaterial(matStr);
                if (mat != null && mat.isItem()) {
                    return mat;
                }
            }
        } catch (Exception ignored) {
            // 配置读取失败，降级为默认值
        }
        return defaultMat;
    }

    /**
     * 解析描述文本中的 PAPI 占位符（必须在主线程调用）。
     */
    private String resolvePapi(Player player, String text) {
        if (player == null || !text.contains("%")) return text;
        try {
            if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            }
        } catch (Exception ignored) {}
        return text;
    }
}
