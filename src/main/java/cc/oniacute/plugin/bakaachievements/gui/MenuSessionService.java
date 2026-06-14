package cc.oniacute.plugin.bakaachievements.gui;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores GUI sessions by viewer UUID.
 */
public final class MenuSessionService {

    private final Map<UUID, MenuSession> sessions = new ConcurrentHashMap<>();

    public void put(UUID viewer, MenuSession session) {
        sessions.put(viewer, session);
    }

    public Optional<MenuSession> get(UUID viewer) {
        return Optional.ofNullable(sessions.get(viewer));
    }

    public void remove(UUID viewer) {
        sessions.remove(viewer);
    }

    public void clear() {
        sessions.clear();
    }
}
