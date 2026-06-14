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
import java.util.concurrent.Executor;

/**
 * 基于 YAML 文件的玩家数据存储实现（v3 格式）。
 *
 * <h3>存储格式（v3）</h3>
 * <pre>{@code
 * version: 3
 * preferences:
 *   tips-self: true
 *   tips-others: true
 * achievements:
 *   test.cus_1:
 *     u: true
 *     t: 1717459200000
 * }</pre>
 *
 * <h3>兼容性策略</h3>
 * <ul>
 *   <li>v3（当前）：正常读取。</li>
 *   <li>v2：自动迁移——旧键 {@code preferences.receive-share-tips} 映射到
 *       {@code tipsOthers}，{@code tipsSelf} 保持默认 true。</li>
 *   <li>其他/缺失 version：记录 SEVERE 后返回空数据（不迁移）。</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * {@link #cache} 使用 {@link ConcurrentHashMap} 保证并发访问安全；
 * 缓存条目内的 {@code lastAccess} 声明为 {@code volatile}，保证多线程可见；
 * 文件 IO 均通过 {@link AsyncExecutor} 在工作线程执行。
 */
public final class YamlPlayerDataStorage implements PlayerDataStorage {

    /** 当前支持的存储格式版本号 */
    private static final int CURRENT_VERSION = 3;
    /** 上一个可迁移版本 */
    private static final int LEGACY_VERSION_V2 = 2;

    // ── 依赖 ────────────────────────────────────────────────

    private final BakaAchievements plugin;
    private final AsyncExecutor asyncExecutor;
    /** playerdata/ 目录 */
    private final File dataDir;

    // ── 缓存 ────────────────────────────────────────────────

    /**
     * 玩家 UUID → 缓存条目。
     * ConcurrentHashMap 保证 put/get/remove 的线程安全。
     */
    private final ConcurrentHashMap<UUID, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 缓存条目：持有数据对象和最后访问时间戳。
     * {@code lastAccess} 声明为 {@code volatile}，确保跨线程可见。
     */
    private static final class CacheEntry {
        final PlayerAchievementData data;
        volatile long lastAccess;

        CacheEntry(PlayerAchievementData data, long lastAccess) {
            this.data = data;
            this.lastAccess = lastAccess;
        }
    }

    // ── 构造 ────────────────────────────────────────────────

    public YamlPlayerDataStorage(BakaAchievements plugin, AsyncExecutor asyncExecutor) {
        this.plugin = plugin;
        this.asyncExecutor = asyncExecutor;
        this.dataDir = new File(plugin.getDataFolder(), "playerdata");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            plugin.getLogger().severe("无法创建 playerdata 目录：" + dataDir.getAbsolutePath());
        }
    }

    // ── PlayerDataStorage 接口实现 ───────────────────────────

    /**
     * 加载玩家数据。
     * <ol>
     *   <li>优先命中缓存：刷新 lastAccess 后直接返回（同步）。</li>
     *   <li>缓存未命中：异步读文件，结果写入缓存后返回。</li>
     *   <li>文件不存在：返回空的 {@link PlayerAchievementData}。</li>
     * </ol>
     */
    @Override
    public CompletableFuture<PlayerAchievementData> load(UUID uuid) {
        CacheEntry entry = cache.get(uuid);
        if (entry != null) {
            // 缓存命中：原子更新 lastAccess，同步返回
            entry.lastAccess = System.currentTimeMillis();
            return CompletableFuture.completedFuture(entry.data);
        }

        // 缓存未命中：提交异步 IO
        return CompletableFuture.supplyAsync(() -> {
            // 防止并发双重加载：再次检查缓存
            CacheEntry existing = cache.get(uuid);
            if (existing != null) {
                existing.lastAccess = System.currentTimeMillis();
                return existing.data;
            }
            PlayerAchievementData data = readFromFile(uuid);
            cache.put(uuid, new CacheEntry(data, System.currentTimeMillis()));
            return data;
        }, resolveExecutor());
    }

    /**
     * 判断该玩家是否已经被本插件写入过 playerdata 文件。
     * 用于首次加入时的兼容同步, 不会触发加载或创建文件。
     */
    public boolean hasDataFile(UUID uuid) {
        return getFile(uuid).exists();
    }

    /**
     * 获取已缓存的玩家数据，同时刷新 lastAccess。
     * 若未加载返回 {@code null}，不触发任何 IO。
     */
    @Override
    public PlayerAchievementData getCached(UUID uuid) {
        CacheEntry entry = cache.get(uuid);
        if (entry != null) {
            entry.lastAccess = System.currentTimeMillis();
            return entry.data;
        }
        return null;
    }

    /**
     * 异步保存单个玩家数据，写盘成功后清除脏标记。
     */
    @Override
    public CompletableFuture<Void> save(UUID uuid, PlayerAchievementData data) {
        return CompletableFuture.runAsync(() -> {
            writeToFile(uuid, data);
            data.markClean();
        }, resolveExecutor());
    }

    /**
     * 异步批量刷盘：仅处理标记为脏的缓存条目。
     */
    @Override
    public CompletableFuture<Void> flushAll() {
        return CompletableFuture.runAsync(() -> {
            for (Map.Entry<UUID, CacheEntry> e : cache.entrySet()) {
                PlayerAchievementData d = e.getValue().data;
                if (d.isDirty()) {
                    writeToFile(e.getKey(), d);
                    d.markClean();
                }
            }
        }, resolveExecutor());
    }

    /**
     * 淘汰缓存中离线超过 {@code offlineMinutes} 分钟的条目。
     * 淘汰前若数据为脏则同步写盘（在调用线程执行，应避免在主线程调用）。
     *
     * @param offlineMinutes 判定过期的离线分钟数
     */
    @Override
    public void evictStale(long offlineMinutes) {
        long cutoffMs = System.currentTimeMillis() - offlineMinutes * 60_000L;
        cache.entrySet().removeIf(e -> {
            if (e.getValue().lastAccess < cutoffMs) {
                PlayerAchievementData d = e.getValue().data;
                if (d.isDirty()) {
                    writeToFile(e.getKey(), d);
                    d.markClean();
                }
                return true; // 淘汰
            }
            return false;
        });
    }

    // ── 文件 IO ────────────────────────────────────────────

    /**
     * 从文件读取玩家数据（v2 格式）。
     * <p>
     * 若文件不存在，返回空数据；若版本号不匹配，记录 SEVERE 后返回空数据。
     * </p>
     */
    private PlayerAchievementData readFromFile(UUID uuid) {
        File file = getFile(uuid);
        if (!file.exists()) {
            return new PlayerAchievementData();
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        // 版本校验与迁移
        int fileVersion = yml.getInt("version", -1);
        PlayerAchievementData data = new PlayerAchievementData();

        if (fileVersion == CURRENT_VERSION) {
            // v3：正常读取偏好
            data.setTipsSelf(yml.getBoolean("preferences.tips-self", true));
            data.setTipsOthers(yml.getBoolean("preferences.tips-others", true));

        } else if (fileVersion == LEGACY_VERSION_V2) {
            // v2 → v3 迁移：旧键 receive-share-tips 映射到 tipsOthers
            plugin.getLogger().info(
                    "[BakaAchievements] 迁移玩家数据 v2 → v3: " + file.getName());
            boolean oldReceive = yml.getBoolean("preferences.receive-share-tips", true);
            data.setTipsSelf(true);          // self tips 默认开
            data.setTipsOthers(oldReceive);  // 继承旧值

        } else {
            plugin.getLogger().severe(
                    "[BakaAchievements] 检测到不支持的数据格式（version=" + fileVersion
                    + "），将重置为新格式。请提前备份 playerdata/ 目录！（文件: " + file.getName() + "）"
            );
            return new PlayerAchievementData();
        }

        // 读取成就状态
        ConfigurationSection achievements = yml.getConfigurationSection("achievements");
        if (achievements != null) {
            for (String key : achievements.getKeys(false)) {
                boolean unlocked = achievements.getBoolean(key + ".u", false);
                long achieveTime = achievements.getLong(key + ".t", -1L);
                data.setStatus(key, new PlayerAchievementData.AchievementStatus(unlocked, achieveTime));
            }
        }

        // 文件加载完成后清除因 setter 产生的脏标记
        data.markClean();
        return data;
    }

    /**
     * 将玩家数据以 v2 格式写入文件。
     * <p>
     * 字段映射：
     * <ul>
     *   <li>{@code u} — unlocked（boolean）</li>
     *   <li>{@code t} — achieveTime（long，仅 > 0 时写入）</li>
     * </ul>
     * </p>
     */
    private void writeToFile(UUID uuid, PlayerAchievementData data) {
        File file = getFile(uuid);
        YamlConfiguration yml = new YamlConfiguration();

        // 版本号
        yml.set("version", CURRENT_VERSION);

        // 偏好设置（v3 格式）
        yml.set("preferences.tips-self", data.isTipsSelf());
        yml.set("preferences.tips-others", data.isTipsOthers());

        // 成就状态
        for (Map.Entry<String, PlayerAchievementData.AchievementStatus> e : data.getAll().entrySet()) {
            String base = "achievements." + e.getKey();
            PlayerAchievementData.AchievementStatus status = e.getValue();
            yml.set(base + ".u", status.unlocked());
            if (status.achieveTime() > 0) {
                yml.set(base + ".t", status.achieveTime());
            }
        }

        try {
            yml.save(file);
        } catch (Exception ex) {
            plugin.getLogger().severe(
                    "[BakaAchievements] 无法保存玩家数据: " + uuid + " — " + ex.getMessage()
            );
        }
    }

    // ── 工具方法 ──────────────────────────────────────────────

    /** 返回玩家数据文件路径：{@code playerdata/<uuid>.yml} */
    private File getFile(UUID uuid) {
        return new File(dataDir, uuid.toString() + ".yml");
    }

    /**
     * 解析异步执行器。
     * 若 asyncExecutor 不可用则回退到当前线程（同步执行，附日志警告）。
     */
    private Executor resolveExecutor() {
        if (asyncExecutor != null) {
            return asyncExecutor::runAsync;
        }
        plugin.getLogger().warning("[BakaAchievements] AsyncExecutor 不可用，IO 操作将在当前线程同步执行。");
        return Runnable::run;
    }
}
