package games.coob.smp.tracking;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

/**
 * Manages the Player Locator Bar visibility and targeting.
 * Uses waypoint attributes (1.21.5+) to control visibility.
 */
public final class LocatorBarManager {

    private static final double WORLD_MAX = 6.0e7;
    private static final double NONE = 0.0;

    private static final Attribute WAYPOINT_RECEIVE_RANGE;
    private static final Attribute WAYPOINT_TRANSMIT_RANGE;

    static {
        WAYPOINT_RECEIVE_RANGE = resolveAttribute("WAYPOINT_RECEIVE_RANGE");
        WAYPOINT_TRANSMIT_RANGE = resolveAttribute("WAYPOINT_TRANSMIT_RANGE");
    }

    private static Attribute resolveAttribute(String name) {
        try {
            Field field = Attribute.class.getField(name);
            return (Attribute) field.get(null);
        } catch (NoSuchFieldException e) {
            try {
                return Attribute.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private LocatorBarManager() {
    }

    // -------------------------------------------------------------------------
    // Receive (can this player SEE the locator bar?)
    // -------------------------------------------------------------------------

    /**
     * Enable the locator bar for this player (allow receiving waypoints).
     */
    public static void enableReceive(Player player) {
        setAttribute(player, WAYPOINT_RECEIVE_RANGE, WORLD_MAX);
    }

    /**
     * Disable the locator bar for this player (hide the bar).
     */
    public static void disableReceive(Player player) {
        setAttribute(player, WAYPOINT_RECEIVE_RANGE, NONE);
    }

    // -------------------------------------------------------------------------
    // Transmit (can OTHER players see THIS player as a waypoint?)
    // -------------------------------------------------------------------------

    /**
     * Enable waypoint transmission (this player becomes visible on others' bars).
     */
    public static void enableTransmit(Player player) {
        setAttribute(player, WAYPOINT_TRANSMIT_RANGE, WORLD_MAX);
    }

    /**
     * Disable waypoint transmission (this player is hidden from others' bars).
     */
    public static void disableTransmit(Player player) {
        setAttribute(player, WAYPOINT_TRANSMIT_RANGE, NONE);
    }

    // -------------------------------------------------------------------------
    // Targeting
    // -------------------------------------------------------------------------

    /**
     * Set the locator bar target location.
     */
    public static void setTarget(Player player, Location location) {
        if (player != null && location != null && location.getWorld() != null) {
            player.setCompassTarget(location);
        }
    }

    /**
     * Point the locator bar to another player's current location.
     */
    public static void setTarget(Player player, Player target) {
        if (player != null && target != null && target.isOnline()) {
            player.setCompassTarget(target.getLocation());
        }
    }

    /**
     * Hide the locator bar by pointing it at the player's own location.
     */
    public static void clearTarget(Player player) {
        if (player != null) {
            player.setCompassTarget(player.getLocation());
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static void setAttribute(Player player, Attribute attribute, double value) {
        if (player == null || attribute == null)
            return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
