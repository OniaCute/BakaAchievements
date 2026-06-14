package cc.oniacute.plugin.bakaachievements.config;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置管理器——统一管理 4 份 YAML 配置。
 * <p>
 * 提供 config.yml、achievements.yml、messages.yml、disabled.yml
 * 的类型安全访问和热重载支持。
 * </p>
 */
public final class ConfigManager {

    private final BakaAchievements plugin;

    private YamlConfiguration config;
    private YamlConfiguration achievements;
    private YamlConfiguration messages;
    private YamlConfiguration disabled;
    private YamlConfiguration vanilla;

    private final File configFile;
    private final File achievementsFile;
    private final File messagesFile;
    private final File disabledFile;
    private final File vanillaFile;

    public ConfigManager(BakaAchievements plugin) {
        this.plugin = plugin;
        File dataFolder = plugin.getDataFolder();
        this.configFile = new File(dataFolder, "config.yml");
        this.achievementsFile = new File(dataFolder, "achievements.yml");
        this.messagesFile = new File(dataFolder, "messages.yml");
        this.disabledFile = new File(dataFolder, "disabled.yml");
        this.vanillaFile = new File(dataFolder, "vanilla.yml");
    }

    /** 加载全部配置 */
    public void loadAll() {
        config = loadOrDefault(configFile, "config.yml");
        achievements = loadOrDefault(achievementsFile, "achievements.yml");
        messages = loadOrDefault(messagesFile, "messages.yml");
        disabled = YamlConfiguration.loadConfiguration(disabledFile);
        vanilla = loadOrDefault(vanillaFile, "vanilla.yml");
    }

    /** 仅重载 config.yml */
    public void reloadConfig() {
        config = loadOrDefault(configFile, "config.yml");
    }

    /** 仅重载 messages.yml */
    public void reloadMessages() {
        messages = loadOrDefault(messagesFile, "messages.yml");
    }

    /** 仅重载 achievements.yml */
    public void reloadAchievements() {
        achievements = loadOrDefault(achievementsFile, "achievements.yml");
    }

    /** 仅重载 vanilla.yml */
    public void reloadVanilla() {
        vanilla = loadOrDefault(vanillaFile, "vanilla.yml");
    }

    /** 保存 disabled.yml */
    public void saveDisabled(YamlConfiguration data) {
        try {
            data.save(disabledFile);
            this.disabled = data;
        } catch (Exception e) {
            plugin.getLogger().severe("无法保存 disabled.yml: " + e.getMessage());
        }
    }

    private YamlConfiguration loadOrDefault(File file, String resource) {
        if (!file.exists()) {
            plugin.saveResource(resource, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    // ── config.yml 原始访问 ────────────────────────────

    /** 获取 config.yml 的原始 YamlConfiguration（供 ActionBarRenderer 等读取子节） */
    public YamlConfiguration getConfig() {
        return config;
    }

    // ── config.yml 访问器 ──────────────────────────────

    public boolean isDebug() {
        return config.getBoolean("debug", false);
    }

    public long getScanIntervalTicks() {
        return config.getLong("async.scan-interval-ticks", 600L);
    }

    public int getThreadPoolSize() {
        return config.getInt("async.thread-pool-size", 2);
    }

    public boolean isChatOverrideEnabled() {
        return config.getBoolean("chat.override-vanilla", true);
    }

    public String getChatFormat() {
        return config.getString("chat.format", "<gold>%player_name%</gold> <gray>解锁了成就</gray> <green>%display%</green>");
    }

    public long getFlushIntervalTicks() {
        return config.getLong("storage.flush-interval-ticks", 600L);
    }

    public long getCacheEvictMinutes() {
        return config.getLong("storage.cache-evict-after-offline-minutes", 5L);
    }

    public int getGuiRows() {
        return config.getInt("gui.rows", 6);
    }

    public boolean isGuiFillEmpty() {
        return config.getBoolean("gui.fill-empty", true);
    }

    /**
     * GUI 自动刷新间隔（ticks）。
     * 默认 600 ticks（30 秒）。
     */
    public long getGuiRefreshIntervalTicks() {
        return config.getLong("gui.refresh-interval-ticks", 600L);
    }

    /**
     * GUI 点击音效。
     * 返回 {@code null} 表示禁用（配置为 {@code "NONE"} 或无效值）。
     */
    @SuppressWarnings("deprecation")
    public org.bukkit.Sound getClickSound() {
        String name = config.getString("gui.click-sound", "BLOCK_LEVER_CLICK");
        if (name == null || name.isBlank() || "NONE".equalsIgnoreCase(name)) return null;
        try {
            return org.bukkit.Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 原版成就分类是否排在根菜单第一位。
     * 默认 {@code true}（原版排在前面，符合 INTRODUCE.md 规范）。
     */
    public boolean isVanillaCategoryFirst() {
        return config.getBoolean("gui.vanilla-category-first", true);
    }

    /**
     * 从 config.yml 读取进度条样式配置。
     *
     * @return BarStyle 实例（缺失键自动使用默认值）
     */
    public cc.oniacute.plugin.bakaachievements.util.ProgressBarUtil.BarStyle getProgressBarStyle() {
        String left   = config.getString("gui.progress-bar.left",   "[");
        String filled = config.getString("gui.progress-bar.filled", "|");
        String empty  = config.getString("gui.progress-bar.empty",  "|");
        String right  = config.getString("gui.progress-bar.right",  "]");
        int    length = config.getInt(   "gui.progress-bar.length", 20);
        return new cc.oniacute.plugin.bakaachievements.util.ProgressBarUtil.BarStyle(
                left, filled, empty, right, length);
    }

    /**
     * 进度条各段独立颜色配置。
     *
     * @param leftColor    左括号颜色（MiniMessage 颜色名，如 gold/green/red）
     * @param filledColor  已完成填充颜色
     * @param emptyColor   未完成填充颜色
     * @param rightColor   右括号颜色
     * @param percentColor 百分比文字颜色
     */
    public record ProgressBarColors(
            String leftColor,
            String filledColor,
            String emptyColor,
            String rightColor,
            String percentColor
    ) {
        public static final ProgressBarColors DEFAULT = new ProgressBarColors(
                "gold", "green", "dark_gray", "gold", "gold");
    }

    /**
     * 从 config.yml 读取进度条颜色配置。
     */
    public ProgressBarColors getProgressBarColors() {
        return new ProgressBarColors(
                config.getString("gui.progress-bar.left-color",    "gold"),
                config.getString("gui.progress-bar.filled-color",  "green"),
                config.getString("gui.progress-bar.empty-color",   "dark_gray"),
                config.getString("gui.progress-bar.right-color",   "gold"),
                config.getString("gui.progress-bar.percent-color", "gold")
        );
    }

    // ── share 访问器 ────────────────────────────────────

    /** 是否启用成就分享功能（默认 true）。 */
    public boolean isShareEnabled() {
        return config.getBoolean("share.enabled", true);
    }

    /** 每玩家每条成就的分享冷却秒数（默认 10 秒）。 */
    public int getShareCooldownSeconds() {
        return config.getInt("share.cooldown-seconds", 10);
    }

    /** 每玩家全局分享冷却秒数（默认 3 秒）。 */
    public int getShareGlobalCooldownSeconds() {
        return config.getInt("share.global-cooldown-seconds", 3);
    }

    /** 是否允许分享未解锁的成就（默认 true）。 */
    public boolean isShareAllowLocked() {
        return config.getBoolean("share.allow-locked", true);
    }

    // ── achievements.yml 访问器 ─────────────────────────

    public YamlConfiguration getAchievementsConfig() {
        return achievements;
    }

    // ── messages.yml 访问器 ─────────────────────────────

    public String getMessage(String key) {
        return messages.getString(key, key);
    }

    public String getMessage(String key, String def) {
        return messages.getString(key, def);
    }

    public YamlConfiguration getMessagesConfig() {
        return messages;
    }

    // ── vanilla.yml 访问器 ─────────────────────────────

    /**
     * 原版成就显示覆盖信息。
     *
     * @param display      中文显示名称
     * @param descriptions 中文描述 lore, 支持多行
     */
    public record VanillaOverride(String display, List<String> descriptions) {
        public VanillaOverride {
            descriptions = descriptions == null ? List.of() : List.copyOf(descriptions);
        }

        /**
         * 兼容旧调用方的单行描述读取。
         */
        public String description() {
            return descriptions.isEmpty() ? "" : descriptions.get(0);
        }
    }

    /**
     * 获取 vanilla.yml 中所有原版成就的显示覆盖映射。
     * <p>
     * Key 为原版 advancement 的完整 key（如 {@code minecraft:story/mine_stone}），
     * Value 为覆盖的显示名称与描述。
     * 若 vanilla.yml 不存在或格式异常，返回空 Map。
     * </p>
     *
     * @return 不可变的覆盖映射（线程安全）
     */
    public Map<String, VanillaOverride> getVanillaOverrides() {
        if (vanilla == null) return Collections.emptyMap();

        ConfigurationSection advSection = vanilla.getConfigurationSection("advancements");
        if (advSection == null) return Collections.emptyMap();

        Map<String, VanillaOverride> result = new HashMap<>();
        for (String key : advSection.getKeys(false)) {
            ConfigurationSection entry = advSection.getConfigurationSection(key);
            if (entry == null) continue;

            String display = entry.getString("display");
            List<String> descriptions = readVanillaDescriptions(entry);
            if (display != null || !descriptions.isEmpty()) {
                result.put(key, new VanillaOverride(
                        display != null ? display : "",
                        descriptions));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private List<String> readVanillaDescriptions(ConfigurationSection entry) {
        if (entry.contains("descriptions")) {
            List<String> lines = new ArrayList<>();
            for (String line : entry.getStringList("descriptions")) {
                if (line != null && !line.isBlank()) {
                    lines.add(line);
                }
            }
            return lines;
        }

        String description = entry.getString("description");
        if (description == null || description.isBlank()) {
            return List.of();
        }
        return List.of(description);
    }

    // ── disabled.yml 访问器 ─────────────────────────────

    public YamlConfiguration getDisabledConfig() {
        return disabled;
    }
}
