package games.coob.smp.tracking;

import games.coob.smp.SMPPlugin;
import games.coob.smp.settings.Settings;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages the Player Locator Bar visibility and targeting.
 * Uses the waypoint range attributes to control visibility.
 * 
 * Strategy: Set base values ONCE on player join (initializePlayer), then only
 * toggle visibility via AttributeModifiers. This avoids repeated base value
 * broadcasts that can cause issues.
 */
public final class LocatorBarManager {

    private static final boolean DEBUG = false;
    private static final double WORLD_MAX = 6.0e7;

    private static final Attribute WAYPOINT_RECEIVE_RANGE = Attribute.WAYPOINT_RECEIVE_RANGE;
    private static final Attribute WAYPOINT_TRANSMIT_RANGE = Attribute.WAYPOINT_TRANSMIT_RANGE;

    // Modifier keys for disabling receive/transmit (lazy-initialized)
    private static NamespacedKey disableReceiveKey;
    private static NamespacedKey disableTransmitKey;

    // Track which players have been initialized (base values set)
    private static final Set<UUID> initialized = ConcurrentHashMap.newKeySet();
    // Skip redundant enable/disable calls (LocatorTask runs every ~2s)
    private static final Set<UUID> receiveEnabled = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> transmitEnabled = ConcurrentHashMap.newKeySet();

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
     * Vanilla locator bar mode: removes the modifiers the custom tracking mode may
     * have left on the player (attribute modifiers are saved with player data),
     * then shows or hides the bar depending on the allowed dimensions.
     */
    public static void applyVanillaMode(Player player, boolean barAllowedHere) {
        if (player == null)
            return;
        removeDisableModifier(player, WAYPOINT_TRANSMIT_RANGE, getDisableTransmitKey());
        if (barAllowedHere) {
            removeDisableModifier(player, WAYPOINT_RECEIVE_RANGE, getDisableReceiveKey());
        } else {
            addDisableModifier(player, WAYPOINT_RECEIVE_RANGE, getDisableReceiveKey());
        }
    }

    /**
     * Whether the locator bar is allowed in this world (Allowed_Environements setting).
     */
    public static boolean isAllowedIn(World world) {
        // Asked every few ticks: parsed again only when the setting changes (/smp reload)
        String setting = Settings.LocatorSection.ALLOWED_ENVIRONEMENTS;
        if (!setting.equals(parsedSetting)) {
            allowedEnvironments = parseEnvironments(setting);
            parsedSetting = setting;
        }
        return allowedEnvironments.contains(world.getEnvironment());
    }

    private static String parsedSetting;
    private static java.util.Set<World.Environment> allowedEnvironments = java.util.EnumSet.noneOf(World.Environment.class);

    /** One value or several separated by commas, e.g. "normal, nether". */
    private static java.util.Set<World.Environment> parseEnvironments(String setting) {
        java.util.Set<World.Environment> allowed = java.util.EnumSet.noneOf(World.Environment.class);
        for (String value : setting.toLowerCase(Locale.ROOT).split(",")) {
            switch (value.trim()) {
                case "all" -> allowed.addAll(java.util.EnumSet.allOf(World.Environment.class));
                case "normal", "overworld" -> allowed.add(World.Environment.NORMAL);
                case "nether" -> allowed.add(World.Environment.NETHER);
                case "the end", "the_end", "end" -> allowed.add(World.Environment.THE_END);
                default -> {
                }
            }
        }
        return allowed;
    }

    /**
     * Current waypoint range of a player's attribute (0 when disabled).
     */
    public static double getReceiveRange(Player player) {
        AttributeInstance instance = player.getAttribute(WAYPOINT_RECEIVE_RANGE);
        return instance != null ? instance.getValue() : 0;
    }

    public static double getTransmitRange(Player player) {
        AttributeInstance instance = player.getAttribute(WAYPOINT_TRANSMIT_RANGE);
        return instance != null ? instance.getValue() : 0;
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
            debug("addDisableModifier: No attribute instance for " + attribute.getKey());
            return;
        }

        AttributeModifier existing = instance.getModifier(key);
        if (existing != null) return; // Already disabled (idempotent)

        // Add modifier: MULTIPLY_SCALAR_1 with -1 makes effective value = base * 0 = 0
        AttributeModifier modifier = new AttributeModifier(
                key,
                -1.0, // amount: -1 means multiply by (1 + -1) = 0
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        // Transient: never saved with the player, so it can't outlive the plugin or this session
        instance.addTransientModifier(modifier);
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
            debug("removeDisableModifier: No attribute instance for " + attribute.getKey());
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
