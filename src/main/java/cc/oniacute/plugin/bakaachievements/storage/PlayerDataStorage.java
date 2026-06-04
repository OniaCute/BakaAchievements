package cc.oniacute.plugin.bakaachievements.storage;

import cc.oniacute.plugin.bakaachievements.achievement.PlayerAchievementData;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 玩家数据存储接口。
 * <p>
 * 支持异步读写和内存 LRU 缓存。
 * </p>
 */
public interface PlayerDataStorage {

    /**
     * 加载玩家数据（优先从缓存，否则异步读文件）。
     *
     * @param uuid 玩家 UUID
     * @return 玩家数据 Future
     */
    CompletableFuture<PlayerAchievementData> load(UUID uuid);

    /**
     * 获取已缓存的玩家数据（不触发 IO）。
     *
     * @param uuid 玩家 UUID
     * @return 玩家数据，若未加载则返回 {@code null}
     */
    PlayerAchievementData getCached(UUID uuid);

    /**
     * 保存单个玩家数据。
     *
     * @param uuid 玩家 UUID
     * @param data 玩家数据
     */
    CompletableFuture<Void> save(UUID uuid, PlayerAchievementData data);

    /**
     * 将脏数据批量刷入磁盘。
     */
    CompletableFuture<Void> flushAll();

    /**
     * 从缓存中淘汰离线超过指定时间的玩家数据。
     *
     * @param offlineMinutes 离线分钟数
     */
    void evictStale(long offlineMinutes);
}
