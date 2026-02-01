package games.coob.smp.tracking;

import games.coob.smp.SMPPlugin;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages the Player Locator Bar visibility and targeting.
 * Uses waypoint attributes (1.21.5+) to control visibility.
 * 
 * Strategy: Set base values ONCE on player join (initializePlayer), then only
 * toggle visibility via AttributeModifiers. This avoids repeated base value
 * broadcasts that can cause issues.
 */
public final class LocatorBarManager {

    private static final boolean DEBUG = false;
    private static final double WORLD_MAX = 6.0e7;

    private static final Attribute WAYPOINT_RECEIVE_RANGE;
    private static final Attribute WAYPOINT_TRANSMIT_RANGE;

    // Modifier keys for disabling receive/transmit (lazy-initialized)
    private static NamespacedKey disableReceiveKey;
    private static NamespacedKey disableTransmitKey;

    // Track which players have been initialized (base values set)
    private static final Set<UUID> initialized = ConcurrentHashMap.newKeySet();
    // Skip redundant enable/disable calls (LocatorTask runs every ~2s)
    private static final Set<UUID> receiveEnabled = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> transmitEnabled = ConcurrentHashMap.newKeySet();

    static {
        WAYPOINT_RECEIVE_RANGE = resolveAttribute("WAYPOINT_RECEIVE_RANGE");
        WAYPOINT_TRANSMIT_RANGE = resolveAttribute("WAYPOINT_TRANSMIT_RANGE");
    }

    private static NamespacedKey getDisableReceiveKey() {
        if (disableReceiveKey == null) {
            disableReceiveKey = new NamespacedKey(SMPPlugin.getInstance(), "disable_receive");
        }
        return disableReceiveKey;
    }

    private static NamespacedKey getDisableTransmitKey() {
        if (disableTransmitKey == null) {
            disableTransmitKey = new NamespacedKey(SMPPlugin.getInstance(), "disable_transmit");
        }
        return disableTransmitKey;
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
    // Initialization (call once on player join)
    // -------------------------------------------------------------------------

    /**
     * Initialize waypoint attributes for a player. Call once on join.
     * Sets base values to WORLD_MAX so modifiers can toggle visibility.
     */
    public static void initializePlayer(Player player) {
        if (player == null)
            return;
        if (!initialized.add(player.getUniqueId())) {
            debug("initializePlayer: " + player.getName() + " already initialized");
            return;
        }

        // Set base values once - this broadcasts but only happens on join
        if (WAYPOINT_RECEIVE_RANGE != null) {
            setBaseValue(player, WAYPOINT_RECEIVE_RANGE, WORLD_MAX);
            debug("initializePlayer: " + player.getName() + " receive base set to " + WORLD_MAX);
        }
        if (WAYPOINT_TRANSMIT_RANGE != null) {
            setBaseValue(player, WAYPOINT_TRANSMIT_RANGE, WORLD_MAX);
            debug("initializePlayer: " + player.getName() + " transmit base set to " + WORLD_MAX);
        }
    }

    // -------------------------------------------------------------------------
    // Receive (can this player SEE the locator bar?)
    // -------------------------------------------------------------------------

    /**
     * Enable the locator bar for this player (allow receiving waypoints).
     * Removes the disable modifier, restoring the base value.
     */
    public static void enableReceive(Player player) {
        if (player == null || WAYPOINT_RECEIVE_RANGE == null) return;
        if (!receiveEnabled.add(player.getUniqueId())) return; // Already enabled, skip
        removeDisableModifier(player, WAYPOINT_RECEIVE_RANGE, getDisableReceiveKey());
        debug("enableReceive: " + player.getName());
    }

    /**
     * Disable the locator bar for this player (hide the bar).
     * Adds a modifier that multiplies the effective value by 0.
     */
    public static void disableReceive(Player player) {
        if (player == null || WAYPOINT_RECEIVE_RANGE == null) return;
        receiveEnabled.remove(player.getUniqueId());
        addDisableModifier(player, WAYPOINT_RECEIVE_RANGE, getDisableReceiveKey());
        debug("disableReceive: " + player.getName());
    }

    /**
     * Disable the locator bar for this player using direct Bukkit API.
     * Same as disableReceive() now that we use modifiers.
     */
    public static void disableReceiveDirect(Player player) {
        disableReceive(player);
    }

    // -------------------------------------------------------------------------
    // Transmit (can OTHER players see THIS player as a waypoint?)
    // -------------------------------------------------------------------------

    /**
     * Enable waypoint transmission (this player becomes visible on others' bars).
     * Removes the disable modifier, restoring the base value.
     */
    public static void enableTransmit(Player player) {
        if (player == null || WAYPOINT_TRANSMIT_RANGE == null) return;
        if (!transmitEnabled.add(player.getUniqueId())) return; // Already enabled, skip
        removeDisableModifier(player, WAYPOINT_TRANSMIT_RANGE, getDisableTransmitKey());
        debug("enableTransmit: " + player.getName());
    }

    /**
     * Disable waypoint transmission (this player is hidden from others' bars).
     * Adds a modifier that multiplies the effective value by 0.
     */
    public static void disableTransmit(Player player) {
        if (player == null || WAYPOINT_TRANSMIT_RANGE == null) return;
        transmitEnabled.remove(player.getUniqueId());
        addDisableModifier(player, WAYPOINT_TRANSMIT_RANGE, getDisableTransmitKey());
        debug("disableTransmit: " + player.getName());
    }

    /**
     * Clean up tracking state for a player (call on quit).
     */
    public static void cleanupPlayer(UUID playerUUID) {
        initialized.remove(playerUUID);
        receiveEnabled.remove(playerUUID);
        transmitEnabled.remove(playerUUID);
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
    // Internal - Modifier-based approach (per-player, no global broadcast)
    // -------------------------------------------------------------------------

    /** Set base attribute value (used when enabling). */
    private static void setBaseValue(Player player, Attribute attribute, double value) {
        if (player == null || attribute == null)
            return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /**
     * Add a modifier that effectively sets the attribute to 0.
     * Uses MULTIPLY_SCALAR_1 with -1, which makes effective = base * (1 + -1) = 0.
     */
    private static void addDisableModifier(Player player, Attribute attribute, NamespacedKey key) {
        if (player == null || attribute == null)
            return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            debug("addDisableModifier: No attribute instance for " + attribute.name());
            return;
        }

        AttributeModifier existing = instance.getModifier(key);
        if (existing != null) return; // Already disabled (idempotent)

        // Add modifier: MULTIPLY_SCALAR_1 with -1 makes effective value = base * 0 = 0
        AttributeModifier modifier = new AttributeModifier(
                key,
                -1.0, // amount: -1 means multiply by (1 + -1) = 0
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        instance.addModifier(modifier);
        debug("addDisableModifier: Added for " + player.getName() + ", effective value now: " + instance.getValue());
    }

    /**
     * Remove the disable modifier, restoring the base value.
     */
    private static void removeDisableModifier(Player player, Attribute attribute, NamespacedKey key) {
        if (player == null || attribute == null)
            return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            debug("removeDisableModifier: No attribute instance for " + attribute.name());
            return;
        }

        AttributeModifier existing = instance.getModifier(key);
        if (existing != null) {
            instance.removeModifier(existing);
            debug("removeDisableModifier: Removed for " + player.getName() + ", effective value now: "
                    + instance.getValue());
        }
        // No modifier = already enabled (normal when LocatorTask calls enable every run)
    }

    private static void debug(String message) {
        if (DEBUG) {
            SMPPlugin.getInstance().getLogger().log(Level.INFO, "[LocatorBarManager Debug] " + message);
        }
    }
}
