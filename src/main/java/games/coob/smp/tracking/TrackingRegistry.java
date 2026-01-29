package games.coob.smp.tracking;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of players who are actively tracking something.
 * Only these players need per-tick locator bar updates.
 */
public final class TrackingRegistry {

    private static final Set<UUID> ACTIVE_TRACKERS = ConcurrentHashMap.newKeySet();

    private TrackingRegistry() {
    }

    /**
     * Register a player as actively tracking something.
     */
    public static void startTracking(UUID playerUUID) {
        if (playerUUID != null) {
            ACTIVE_TRACKERS.add(playerUUID);
        }
    }

    /**
     * Unregister a player from active tracking.
     */
    public static void stopTracking(UUID playerUUID) {
        if (playerUUID != null) {
            ACTIVE_TRACKERS.remove(playerUUID);
        }
    }

    /**
     * Check if a player is actively tracking something.
     */
    public static boolean isTracking(UUID playerUUID) {
        return playerUUID != null && ACTIVE_TRACKERS.contains(playerUUID);
    }

    /**
     * Get all active tracker UUIDs (unmodifiable view).
     */
    public static Set<UUID> getActiveTrackers() {
        return Collections.unmodifiableSet(ACTIVE_TRACKERS);
    }

    /**
     * Get online players who are actively tracking.
     * Returns only players who are both in the registry AND online.
     */
    public static Iterable<Player> getOnlineTrackers() {
        return () -> ACTIVE_TRACKERS.stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline())
                .iterator();
    }

    /**
     * Clear all tracking data (e.g., on plugin disable).
     */
    public static void clear() {
        ACTIVE_TRACKERS.clear();
    }
}
