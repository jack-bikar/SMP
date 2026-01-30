package games.coob.smp.tracking;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Represents a tracked target (player or death location).
 */
public class TrackedTarget {

    private final UUID targetUUID; // null for death location
    private final String type; // "Player" or "Death"
    private MarkerColor color;
    private Location cachedPortalTarget;

    private TrackedTarget(UUID targetUUID, String type, MarkerColor color) {
        this.targetUUID = targetUUID;
        this.type = type;
        this.color = color;
    }

    /**
     * Create a player tracking target.
     */
    public static TrackedTarget player(UUID playerUUID, MarkerColor color) {
        return new TrackedTarget(playerUUID, "Player", color);
    }

    /**
     * Create a death location tracking target.
     */
    public static TrackedTarget death() {
        return new TrackedTarget(null, "Death", MarkerColor.DARK_RED);
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public String getType() {
        return type;
    }

    public boolean isPlayer() {
        return "Player".equals(type);
    }

    public boolean isDeath() {
        return "Death".equals(type);
    }

    public MarkerColor getColor() {
        return color;
    }

    public void setColor(MarkerColor color) {
        // Death location always stays dark red
        if (!isDeath()) {
            this.color = color;
        }
    }

    public Location getCachedPortalTarget() {
        return cachedPortalTarget;
    }

    public void setCachedPortalTarget(Location cachedPortalTarget) {
        this.cachedPortalTarget = cachedPortalTarget;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TrackedTarget other)) return false;
        if (isDeath() && other.isDeath()) return true;
        if (targetUUID == null || other.targetUUID == null) return false;
        return targetUUID.equals(other.targetUUID);
    }

    @Override
    public int hashCode() {
        if (isDeath()) return "Death".hashCode();
        return targetUUID != null ? targetUUID.hashCode() : 0;
    }
}
