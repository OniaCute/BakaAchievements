package cc.oniacute.plugin.bakaachievements.config;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

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

    private final File configFile;
    private final File achievementsFile;
    private final File messagesFile;
    private final File disabledFile;

    public ConfigManager(BakaAchievements plugin) {
        this.plugin = plugin;
        File dataFolder = plugin.getDataFolder();
        this.configFile = new File(dataFolder, "config.yml");
        this.achievementsFile = new File(dataFolder, "achievements.yml");
        this.messagesFile = new File(dataFolder, "messages.yml");
        this.disabledFile = new File(dataFolder, "disabled.yml");
    }

    /** 加载全部配置 */
    public void loadAll() {
        config = loadOrDefault(configFile, "config.yml");
        achievements = loadOrDefault(achievementsFile, "achievements.yml");
        messages = loadOrDefault(messagesFile, "messages.yml");
        disabled = YamlConfiguration.loadConfiguration(disabledFile);
    }

    /** 仅重载 config.yml */
    public void reloadConfig() {
        config = loadOrDefault(configFile, "config.yml");
    }

    /** 仅重载 messages.yml */
    public void reloadMessages() {
        messages = loadOrDefault(messagesFile, "messages.yml");
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

    // ── disabled.yml 访问器 ─────────────────────────────

    public YamlConfiguration getDisabledConfig() {
        return disabled;
    }
}
