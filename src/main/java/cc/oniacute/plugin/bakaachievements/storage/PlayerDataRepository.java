package cc.oniacute.plugin.bakaachievements.storage;

import cc.oniacute.plugin.bakaachievements.achievement.PlayerAchievementData;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Compatibility-preserving repository facade over player achievement data.
 */
public final class PlayerDataRepository {

    private final PlayerDataStorage storage;

    public PlayerDataRepository(PlayerDataStorage storage) {
        this.storage = storage;
    }

    public CompletableFuture<PlayerAchievementData> load(UUID uuid) {
        return storage.load(uuid);
    }

    public PlayerAchievementData cached(UUID uuid) {
        return storage.getCached(uuid);
    }

    public CompletableFuture<Void> save(UUID uuid, PlayerAchievementData data) {
        return storage.save(uuid, data);
    }

    public CompletableFuture<Void> flushAll() {
        return storage.flushAll();
    }

    public void evictStale(long offlineMinutes) {
        storage.evictStale(offlineMinutes);
    }

    public PlayerDataStorage storage() {
        return storage;
    }
}
