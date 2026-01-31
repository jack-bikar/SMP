package games.coob.smp.tracking;

import games.coob.smp.SMPPlugin;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages the Player Locator Bar visibility and targeting.
 * Uses waypoint attributes (1.21.5+) to control visibility.
 * 
 * Note: The Bukkit attribute API may cause the locator bar to disappear for all
 * players when setting WAYPOINT_RECEIVE_RANGE to 0. We work around this by
 * sending the attribute sync packet only to the specific player.
 */
public final class LocatorBarManager {

    private static final boolean DEBUG = true; // Enable for troubleshooting
    private static final double WORLD_MAX = 6.0e7;
    private static final double NONE = 0.0;

    private static final Attribute WAYPOINT_RECEIVE_RANGE;
    private static final Attribute WAYPOINT_TRANSMIT_RANGE;

    // Track who already has receive/transmit enabled to avoid redundant updates
    private static final Set<UUID> receiveEnabled = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> transmitEnabled = ConcurrentHashMap.newKeySet();

    // Reflection cache
    private static boolean reflectionInitialized = false;
    private static Method getHandleMethod;
    private static Method getAttributesMethod;
    private static Method syncAttributesMethod;
    private static Field connectionField;
    private static Method sendPacketMethod;

    static {
        WAYPOINT_RECEIVE_RANGE = resolveAttribute("WAYPOINT_RECEIVE_RANGE");
        WAYPOINT_TRANSMIT_RANGE = resolveAttribute("WAYPOINT_TRANSMIT_RANGE");

        if (DEBUG) {
            SMPPlugin.getInstance().getLogger().log(Level.INFO,
                    "[LocatorBarManager] WAYPOINT_RECEIVE_RANGE: " + WAYPOINT_RECEIVE_RANGE);
            SMPPlugin.getInstance().getLogger().log(Level.INFO,
                    "[LocatorBarManager] WAYPOINT_TRANSMIT_RANGE: " + WAYPOINT_TRANSMIT_RANGE);
        }
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

    private static void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;

        try {
            // Get CraftPlayer.getHandle()
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            getHandleMethod = craftPlayerClass.getMethod("getHandle");

            // Get ServerPlayer class
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            connectionField = serverPlayerClass.getField("connection");

            // Find getAttributes() method on LivingEntity (parent of ServerPlayer)
            Class<?> livingEntityClass = Class.forName("net.minecraft.world.entity.LivingEntity");
            getAttributesMethod = livingEntityClass.getMethod("getAttributes");

            // Find send method on connection
            Class<?> connectionClass = connectionField.getType();
            for (Method m : connectionClass.getMethods()) {
                if (m.getName().equals("send") && m.getParameterCount() == 1) {
                    sendPacketMethod = m;
                    break;
                }
            }

            debug("Reflection initialized successfully. Send method: " + (sendPacketMethod != null));

        } catch (Exception e) {
            debug("Failed to initialize reflection: " + e.getMessage());
            if (DEBUG) e.printStackTrace();
        }
    }

    private LocatorBarManager() {
    }

    // -------------------------------------------------------------------------
    // Receive (can this player SEE the locator bar?)
    // -------------------------------------------------------------------------

    /**
     * Enable the locator bar for this player (allow receiving waypoints).
     * Skips redundant updates if already enabled.
     */
    public static void enableReceive(Player player) {
        if (player == null) return;
        if (receiveEnabled.add(player.getUniqueId())) {
            setAttribute(player, WAYPOINT_RECEIVE_RANGE, WORLD_MAX);
        }
    }

    /**
     * Disable the locator bar for this player (hide the bar).
     * Uses packet-based update to avoid affecting other players.
     */
    public static void disableReceive(Player player) {
        if (player == null) return;
        receiveEnabled.remove(player.getUniqueId());
        setAttributeViaPacket(player, WAYPOINT_RECEIVE_RANGE, NONE);
    }

    /**
     * Disable the locator bar for this player using direct Bukkit API.
     * WARNING: May cause global bug. Use disableReceive() instead.
     */
    public static void disableReceiveDirect(Player player) {
        if (player == null) return;
        receiveEnabled.remove(player.getUniqueId());
        setAttribute(player, WAYPOINT_RECEIVE_RANGE, NONE);
    }

    // -------------------------------------------------------------------------
    // Transmit (can OTHER players see THIS player as a waypoint?)
    // -------------------------------------------------------------------------

    /**
     * Enable waypoint transmission (this player becomes visible on others' bars).
     * Skips redundant updates if already enabled.
     */
    public static void enableTransmit(Player player) {
        if (player == null) return;
        if (transmitEnabled.add(player.getUniqueId())) {
            setAttribute(player, WAYPOINT_TRANSMIT_RANGE, WORLD_MAX);
        }
    }

    /**
     * Disable waypoint transmission (this player is hidden from others' bars).
     */
    public static void disableTransmit(Player player) {
        if (player == null) return;
        transmitEnabled.remove(player.getUniqueId());
        setAttribute(player, WAYPOINT_TRANSMIT_RANGE, NONE);
    }

    /**
     * Clean up tracking state for a player (call on quit).
     */
    public static void cleanupPlayer(UUID playerUUID) {
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
    // Internal
    // -------------------------------------------------------------------------

    /**
     * Set attribute via Bukkit API (used for enabling, may broadcast to others).
     */
    private static void setAttribute(Player player, Attribute attribute, double value) {
        if (player == null || attribute == null) {
            debug("setAttribute: player or attribute is null!");
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            double oldValue = instance.getBaseValue();
            instance.setBaseValue(value);
            debug("setAttribute: " + player.getName() + " " + attribute.name() +
                    " changed from " + oldValue + " to " + value);
        } else {
            debug("setAttribute: " + player.getName() + " has no attribute instance for " + attribute.name());
        }
    }

    /**
     * Set attribute and sync to only this specific player.
     * This avoids the bug where using the Bukkit API broadcasts to all players.
     */
    private static void setAttributeViaPacket(Player player, Attribute attribute, double value) {
        if (player == null || attribute == null) return;

        initReflection();

        // Update the attribute value server-side
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            debug("setAttributeViaPacket: No attribute instance for " + attribute.name());
            return;
        }

        double oldValue = instance.getBaseValue();
        instance.setBaseValue(value);
        debug("setAttributeViaPacket: " + player.getName() + " " + attribute.name() +
                " changed from " + oldValue + " to " + value);

        // Try to send sync packet to only this player
        if (getHandleMethod != null && sendPacketMethod != null) {
            try {
                syncAttributesToPlayer(player);
                debug("setAttributeViaPacket: Synced attributes to " + player.getName());
            } catch (Exception e) {
                debug("setAttributeViaPacket: Failed to sync via reflection: " + e.getMessage());
                if (DEBUG) e.printStackTrace();
            }
        }
    }

    /**
     * Sync the player's attributes by sending the update packet directly to them.
     * This uses the server's built-in sync mechanism but targets only this player.
     */
    private static void syncAttributesToPlayer(Player player) throws Exception {
        Object serverPlayer = getHandleMethod.invoke(player);
        Object connection = connectionField.get(serverPlayer);
        Object attributeMap = getAttributesMethod.invoke(serverPlayer);

        // Get dirty attributes and create sync packet
        // AttributeMap has getDirtyAttributes() which returns attributes that need syncing
        Method getDirtyMethod = null;
        for (Method m : attributeMap.getClass().getMethods()) {
            if (m.getName().equals("getDirtyAttributes") || m.getName().equals("getSyncableAttributes")) {
                getDirtyMethod = m;
                break;
            }
        }

        if (getDirtyMethod != null) {
            Object dirtyAttrs = getDirtyMethod.invoke(attributeMap);
            if (dirtyAttrs != null && dirtyAttrs instanceof java.util.Collection<?> collection && !collection.isEmpty()) {
                // Create ClientboundUpdateAttributesPacket
                Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket");
                for (var constructor : packetClass.getConstructors()) {
                    if (constructor.getParameterCount() == 2) {
                        Object packet = constructor.newInstance(player.getEntityId(), dirtyAttrs);
                        sendPacketMethod.invoke(connection, packet);
                        debug("syncAttributesToPlayer: Sent update packet for " + collection.size() + " attributes");
                        return;
                    }
                }
            }
        }

        // Fallback: Try to trigger a full attribute sync
        // Some servers have a method to force sync
        for (Method m : serverPlayer.getClass().getMethods()) {
            if (m.getName().contains("syncAttributes") || m.getName().contains("resendAttributes")) {
                m.invoke(serverPlayer);
                debug("syncAttributesToPlayer: Used " + m.getName() + " fallback");
                return;
            }
        }

        debug("syncAttributesToPlayer: No sync method found, attribute may not update on client");
    }

    private static void debug(String message) {
        if (DEBUG) {
            SMPPlugin.getInstance().getLogger().log(Level.INFO, "[LocatorBarManager Debug] " + message);
        }
    }
}
