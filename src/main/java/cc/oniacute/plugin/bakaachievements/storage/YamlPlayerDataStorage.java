package cc.oniacute.plugin.bakaachievements.storage;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.PlayerAchievementData;
import cc.oniacute.plugin.bakaachievements.util.AsyncExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YAML 文件实现的玩家数据存储。
 * <p>
 * 每玩家一个 {@code playerdata/<uuid>.yml} 文件，
 * 内存中通过 LRU（近似）缓存管理。
 * </p>
 */
public final class YamlPlayerDataStorage implements PlayerDataStorage {

    private static final long ACCESS_EXPIRE_MS = 300_000; // 5 min

    private final BakaAchievements plugin;
    private final AsyncExecutor asyncExecutor;
    private final File dataDir;

    /** 玩家 UUID → 缓存条目 */
    private final ConcurrentHashMap<UUID, CacheEntry> cache = new ConcurrentHashMap<>();

    /** 缓存条目：包装数据 + 最后访问时间 */
    private static final class CacheEntry {
        final PlayerAchievementData data;
        volatile long lastAccess;

        CacheEntry(PlayerAchievementData data, long lastAccess) {
            this.data = data;
            this.lastAccess = lastAccess;
        }
    }

    public YamlPlayerDataStorage(BakaAchievements plugin, AsyncExecutor asyncExecutor) {
        this.plugin = plugin;
        this.asyncExecutor = asyncExecutor;
        this.dataDir = new File(plugin.getDataFolder(), "playerdata");
        if (!dataDir.exists()) dataDir.mkdirs();
    }

    @Override
    public CompletableFuture<PlayerAchievementData> load(UUID uuid) {
        CacheEntry entry = cache.get(uuid);
        if (entry != null) {
            entry = new CacheEntry(entry.data, System.currentTimeMillis());
            cache.put(uuid, entry);
            return CompletableFuture.completedFuture(entry.data);
        }

        final AsyncExecutor executor = this.asyncExecutor;
        return CompletableFuture.supplyAsync(() -> {
            PlayerAchievementData data = readFromFile(uuid);
            cache.put(uuid, new CacheEntry(data, System.currentTimeMillis()));
            return data;
        }, executor != null ? executor::runAsync : Runnable::run);
    }

    @Override
    public PlayerAchievementData getCached(UUID uuid) {
        CacheEntry entry = cache.get(uuid);
        if (entry != null) {
            entry = new CacheEntry(entry.data, System.currentTimeMillis());
            cache.put(uuid, entry);
            return entry.data;
        }
        return null;
    }

    @Override
    public CompletableFuture<Void> save(UUID uuid, PlayerAchievementData data) {
        final AsyncExecutor executor = this.asyncExecutor;
        return CompletableFuture.runAsync(() -> {
            writeToFile(uuid, data);
            data.markClean();
        }, executor != null ? executor::runAsync : Runnable::run);
    }

    @Override
    public CompletableFuture<Void> flushAll() {
        final AsyncExecutor executor = this.asyncExecutor;
        return CompletableFuture.runAsync(() -> {
            for (Map.Entry<UUID, CacheEntry> entry : cache.entrySet()) {
                PlayerAchievementData d = entry.getValue().data;
                if (d.isDirty()) {
                    writeToFile(entry.getKey(), d);
                    d.markClean();
                }
            }
        }, executor != null ? executor::runAsync : Runnable::run);
    }

    @Override
    public void evictStale(long offlineMinutes) {
        long cutoff = System.currentTimeMillis() - offlineMinutes * 60_000L;
        cache.entrySet().removeIf(entry -> {
            if (entry.getValue().lastAccess < cutoff) {
                // 离线玩家：脏数据先写盘
                PlayerAchievementData d = entry.getValue().data;
                if (d.isDirty()) {
                    writeToFile(entry.getKey(), d);
                    d.markClean();
                }
                return true;
            }
            return false;
        });
    }

    // ── 文件 IO ────────────────────────────────────────────

    private PlayerAchievementData readFromFile(UUID uuid) {
        File file = getFile(uuid);
        if (!file.exists()) return new PlayerAchievementData();

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        PlayerAchievementData data = new PlayerAchievementData();

        ConfigurationSection section = yml.getConfigurationSection("achievements");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                boolean unlocked = section.getBoolean(key + ".unlocked", false);
                long achieveTime = section.getLong(key + ".achieveTime", -1L);
                data.setStatus(key, new PlayerAchievementData.AchievementStatus(unlocked, achieveTime));
            }
        }
        data.markClean();
        return data;
    }

    private void writeToFile(UUID uuid, PlayerAchievementData data) {
        File file = getFile(uuid);
        YamlConfiguration yml = new YamlConfiguration();

        for (Map.Entry<String, PlayerAchievementData.AchievementStatus> entry : data.getAll().entrySet()) {
            String path = "achievements." + entry.getKey();
            yml.set(path + ".status", entry.getValue().unlocked());
            if (entry.getValue().achieveTime() > 0) {
                yml.set(path + ".achieveTime", entry.getValue().achieveTime());
            }
        }

        try {
            yml.save(file);
        } catch (Exception e) {
            plugin.getLogger().severe("无法保存玩家数据: " + uuid + " — " + e.getMessage());
        }
    }

    private File getFile(UUID uuid) {
        return new File(dataDir, uuid.toString() + ".yml");
    }
}
