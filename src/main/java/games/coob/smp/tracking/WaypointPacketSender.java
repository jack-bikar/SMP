package games.coob.smp.tracking;

import games.coob.smp.SMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Sends synthetic waypoint packets to players for cross-dimension tracking.
 * Uses reflection to access NMS ClientboundTrackedWaypointPacket.
 */
public final class WaypointPacketSender {

    private static final boolean DEBUG = false;

    // Track active synthetic waypoints per player (tracker UUID -> waypoint source
    // UUID)
    private static final Map<UUID, UUID> ACTIVE_WAYPOINTS = new ConcurrentHashMap<>();

    // Reflection cache
    private static Class<?> packetClass;
    private static Class<?> waypointIconClass;
    private static Class<?> vec3iClass;
    private static Object defaultIcon;
    private static Method addWaypointMethod;
    private static Method updateWaypointMethod;
    private static Method removeWaypointMethod;
    private static Method sendPacketMethod;
    private static boolean initialized = false;
    private static boolean available = false;

    private WaypointPacketSender() {
    }

    /**
     * Initialize reflection classes and methods.
     */
    public static void initialize() {
        if (initialized)
            return;
        initialized = true;

        try {
            debug("Initializing WaypointPacketSender...");

            // Get NMS package version
            String version = Bukkit.getServer().getClass().getPackage().getName();
            debug("Server package: " + version);

            // Try to find the packet class (Paper 1.21+ uses direct NMS access)
            String[] possiblePacketClasses = {
                    "net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket",
                    "net.minecraft.network.packet.s2c.play.WaypointS2CPacket"
            };

            for (String className : possiblePacketClasses) {
                try {
                    packetClass = Class.forName(className);
                    debug("Found packet class: " + className);
                    break;
                } catch (ClassNotFoundException ignored) {
                    debug("Packet class not found: " + className);
                }
            }

            if (packetClass == null) {
                debug("ERROR: Could not find waypoint packet class!");
                return;
            }

            // Find Waypoint$Icon class
            String[] possibleIconClasses = {
                    "net.minecraft.world.waypoints.Waypoint$Icon",
                    "net.minecraft.world.waypoint.Waypoint$Config"
            };

            for (String className : possibleIconClasses) {
                try {
                    waypointIconClass = Class.forName(className);
                    debug("Found icon class: " + className);
                    break;
                } catch (ClassNotFoundException ignored) {
                    debug("Icon class not found: " + className);
                }
            }

            if (waypointIconClass == null) {
                debug("ERROR: Could not find Waypoint$Icon class!");
                return;
            }

            // Get NULL/DEFAULT icon
            try {
                defaultIcon = waypointIconClass.getField("NULL").get(null);
                debug("Found NULL icon field");
            } catch (NoSuchFieldException e) {
                try {
                    defaultIcon = waypointIconClass.getField("DEFAULT").get(null);
                    debug("Found DEFAULT icon field");
                } catch (NoSuchFieldException e2) {
                    // Try to create a new instance
                    defaultIcon = waypointIconClass.getConstructor().newInstance();
                    debug("Created new icon instance");
                }
            }

            // Find Vec3i class
            vec3iClass = Class.forName("net.minecraft.core.Vec3i");
            debug("Found Vec3i class");

            // Find methods on packet class
            for (Method method : packetClass.getMethods()) {
                String name = method.getName();
                Class<?>[] params = method.getParameterTypes();

                if ((name.equals("addWaypointPosition") || name.equals("trackPos") || name.equals("a"))
                        && params.length == 3 && params[0] == UUID.class) {
                    addWaypointMethod = method;
                    debug("Found add waypoint method: " + name);
                }
                if ((name.equals("updateWaypointPosition") || name.equals("updatePos") || name.equals("b"))
                        && params.length == 3 && params[0] == UUID.class) {
                    updateWaypointMethod = method;
                    debug("Found update waypoint method: " + name);
                }
                if ((name.equals("removeWaypoint") || name.equals("untrack") || name.equals("a"))
                        && params.length == 1 && params[0] == UUID.class) {
                    removeWaypointMethod = method;
                    debug("Found remove waypoint method: " + name);
                }
            }

            if (addWaypointMethod == null) {
                debug("ERROR: Could not find addWaypointPosition method!");
                return;
            }

            // Find sendPacket method on player connection
            // Paper provides Player#sendPacket or we need to use CraftPlayer
            try {
                sendPacketMethod = Player.class.getMethod("sendPacket", Object.class);
                debug("Found Player.sendPacket method");
            } catch (NoSuchMethodException e) {
                debug("Player.sendPacket not found, will try CraftPlayer connection");
            }

            available = true;
            debug("WaypointPacketSender initialized successfully!");

        } catch (Exception e) {
            debug("ERROR during initialization: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if waypoint packets are available on this server.
     */
    public static boolean isAvailable() {
        if (!initialized)
            initialize();
        return available;
    }

    /**
     * Send a waypoint at the specified location to the player.
     * Creates or updates the waypoint.
     *
     * @return true if the waypoint was successfully sent, false otherwise
     */
    public static boolean sendWaypoint(Player player, Location location, UUID waypointId) {
        if (!isAvailable()) {
            debug("Waypoint packets not available, skipping sendWaypoint");
            return false;
        }

        try {
            Object vec3i = createVec3i(location);
            if (vec3i == null) {
                debug("Failed to create Vec3i for location: " + location);
                return false;
            }

            Object packet;
            UUID existingWaypoint = ACTIVE_WAYPOINTS.get(player.getUniqueId());

            if (existingWaypoint != null && existingWaypoint.equals(waypointId)) {
                // Update existing waypoint
                if (updateWaypointMethod != null) {
                    packet = updateWaypointMethod.invoke(null, waypointId, defaultIcon, vec3i);
                    debug("Updating waypoint for " + player.getName() + " at " + formatLocation(location));
                } else {
                    // Fallback: remove and re-add
                    removeWaypoint(player, waypointId);
                    packet = addWaypointMethod.invoke(null, waypointId, defaultIcon, vec3i);
                    debug("Re-adding waypoint for " + player.getName() + " at " + formatLocation(location));
                }
            } else {
                // Remove old waypoint if exists
                if (existingWaypoint != null) {
                    removeWaypoint(player, existingWaypoint);
                }
                // Add new waypoint
                packet = addWaypointMethod.invoke(null, waypointId, defaultIcon, vec3i);
                ACTIVE_WAYPOINTS.put(player.getUniqueId(), waypointId);
                debug("Adding new waypoint for " + player.getName() + " at " + formatLocation(location));
            }

            sendPacket(player, packet);
            return true;

        } catch (Exception e) {
            debug("Error sending waypoint: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Remove the waypoint from the player.
     */
    public static void removeWaypoint(Player player, UUID waypointId) {
        if (!isAvailable() || removeWaypointMethod == null) {
            debug("Cannot remove waypoint - not available");
            return;
        }

        try {
            Object packet = removeWaypointMethod.invoke(null, waypointId);
            sendPacket(player, packet);
            ACTIVE_WAYPOINTS.remove(player.getUniqueId());
            debug("Removed waypoint for " + player.getName());
        } catch (Exception e) {
            debug("Error removing waypoint: " + e.getMessage());
        }
    }

    /**
     * Remove any active waypoint for the player.
     */
    public static void clearWaypoint(Player player) {
        UUID waypointId = ACTIVE_WAYPOINTS.get(player.getUniqueId());
        if (waypointId != null) {
            removeWaypoint(player, waypointId);
        }
    }

    /**
     * Check if player has an active synthetic waypoint.
     */
    public static boolean hasActiveWaypoint(Player player) {
        return ACTIVE_WAYPOINTS.containsKey(player.getUniqueId());
    }

    /**
     * Clear all tracked waypoints (for plugin disable).
     */
    public static void clearAll() {
        ACTIVE_WAYPOINTS.clear();
    }

    private static Object createVec3i(Location location) {
        try {
            return vec3iClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        } catch (Exception e) {
            debug("Error creating Vec3i: " + e.getMessage());
            return null;
        }
    }

    private static void sendPacket(Player player, Object packet) {
        try {
            if (sendPacketMethod != null) {
                sendPacketMethod.invoke(player, packet);
            } else {
                // Try using CraftPlayer
                Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
                Object connection = craftPlayer.getClass().getField("connection").get(craftPlayer);

                // Try different send method names
                Method sendMethod = null;
                for (String methodName : new String[] { "send", "sendPacket", "a" }) {
                    try {
                        for (Method m : connection.getClass().getMethods()) {
                            if (m.getName().equals(methodName) && m.getParameterCount() == 1) {
                                sendMethod = m;
                                break;
                            }
                        }
                        if (sendMethod != null)
                            break;
                    } catch (Exception ignored) {
                    }
                }

                if (sendMethod != null) {
                    sendMethod.invoke(connection, packet);
                } else {
                    debug("Could not find send method on connection!");
                }
            }
        } catch (Exception e) {
            debug("Error sending packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String formatLocation(Location loc) {
        if (loc == null)
            return "null";
        return String.format("%d, %d, %d in %s",
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                loc.getWorld() != null ? loc.getWorld().getName() : "null");
    }

    private static void debug(String message) {
        if (DEBUG) {
            SMPPlugin.getInstance().getLogger().log(Level.INFO, "[Waypoint Debug] " + message);
        }
    }
}
