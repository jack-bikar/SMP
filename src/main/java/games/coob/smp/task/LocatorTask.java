package games.coob.smp.task;

import games.coob.smp.PlayerCache;
import games.coob.smp.SMPPlugin;
import games.coob.smp.settings.Settings;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.tracking.MarkerColor;
import games.coob.smp.tracking.PortalCache;
import games.coob.smp.tracking.WaypointColorManager;
import games.coob.smp.tracking.TrackedTarget;
import games.coob.smp.tracking.TrackingRegistry;
import games.coob.smp.tracking.WaypointPacketSender;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Periodic task that updates the locator bar for active trackers.
 * Supports multi-tracking (multiple players + death location simultaneously).
 * Uses boss bar to display tracking information.
 */
public final class LocatorTask extends BukkitRunnable {

    private static final boolean DEBUG = false;

    // UUID namespace for synthetic waypoints
    private static final UUID WAYPOINT_NAMESPACE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    // Boss bars per player
    private static final Map<UUID, BossBar> playerBossBars = new HashMap<>();

    @Override
    public void run() {
        if (!Settings.LocatorSection.ENABLE_TRACKING) {
            return;
        }

        for (Player tracker : TrackingRegistry.getOnlineTrackers()) {
            updateTracker(tracker);
        }
    }

    private void updateTracker(Player tracker) {
        if (!isEnvironmentAllowed(tracker)) {
            hideBossBar(tracker);
            return;
        }

        PlayerCache cache = PlayerCache.from(tracker);

        if (!cache.isTracking()) {
            hideBossBar(tracker);
            TrackingRegistry.stopTracking(tracker.getUniqueId());
            return;
        }

        // Enable receiving waypoints
        LocatorBarManager.enableReceive(tracker);

        // Find the target the player is currently looking at (closest to center of view)
        TrackedTarget focusedTarget = getFocusedTarget(tracker, cache);

        // Update all tracked targets
        for (TrackedTarget target : cache.getTrackedTargets()) {
            if (target.isPlayer()) {
                updatePlayerTarget(tracker, cache, target);
            } else if (target.isDeath()) {
                updateDeathTarget(tracker, cache, target);
            }
        }

        // Update boss bar with focused target info
        updateBossBar(tracker, cache, focusedTarget);
    }

    /**
     * Find the target the player is most closely looking at.
     */
    private TrackedTarget getFocusedTarget(Player tracker, PlayerCache cache) {
        Location trackerLoc = tracker.getLocation();
        float trackerYaw = trackerLoc.getYaw();

        TrackedTarget closest = null;
        double closestAngleDiff = Double.MAX_VALUE;

        for (TrackedTarget target : cache.getTrackedTargets()) {
            Location targetLoc = getTargetLocation(tracker, cache, target);
            if (targetLoc == null) continue;

            // Calculate angle to target
            double dx = targetLoc.getX() - trackerLoc.getX();
            double dz = targetLoc.getZ() - trackerLoc.getZ();
            double angleToTarget = Math.toDegrees(Math.atan2(-dx, dz));

            // Normalize angles
            double normalizedTrackerYaw = ((trackerYaw % 360) + 360) % 360;
            double normalizedTargetAngle = ((angleToTarget % 360) + 360) % 360;

            // Calculate angle difference
            double angleDiff = Math.abs(normalizedTrackerYaw - normalizedTargetAngle);
            if (angleDiff > 180) angleDiff = 360 - angleDiff;

            // Consider "focused" if within ~30 degrees of center
            if (angleDiff < 30 && angleDiff < closestAngleDiff) {
                closestAngleDiff = angleDiff;
                closest = target;
            }
        }

        return closest;
    }

    private Location getTargetLocation(Player tracker, PlayerCache cache, TrackedTarget target) {
        if (target.isPlayer()) {
            Player targetPlayer = Bukkit.getPlayer(target.getTargetUUID());
            if (targetPlayer == null || !targetPlayer.isOnline()) return null;

            if (targetPlayer.getWorld().equals(tracker.getWorld())) {
                return targetPlayer.getLocation();
            } else {
                // Cross-dimension: return portal location
                Location portal = getOrCalculatePortalTarget(tracker, target, targetPlayer.getWorld().getEnvironment());
                return portal != null ? portal : getFallbackLocation(tracker, targetPlayer.getWorld().getEnvironment());
            }
        } else if (target.isDeath()) {
            Location deathLoc = cache.getDeathLocation();
            if (deathLoc == null || deathLoc.getWorld() == null) return null;

            if (deathLoc.getWorld().equals(tracker.getWorld())) {
                return deathLoc;
            } else {
                Location portal = getOrCalculatePortalTarget(tracker, target, deathLoc.getWorld().getEnvironment());
                return portal != null ? portal : getFallbackLocation(tracker, deathLoc.getWorld().getEnvironment());
            }
        }
        return null;
    }

    private void updatePlayerTarget(Player tracker, PlayerCache cache, TrackedTarget target) {
        Player targetPlayer = Bukkit.getPlayer(target.getTargetUUID());

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            // Target went offline - remove from tracking (waypoint color cleared on quit)
            cache.removeTrackedPlayer(target.getTargetUUID());
            debug("Player target offline, removed from tracking");
            return;
        }

        boolean sameDimension = targetPlayer.getWorld().equals(tracker.getWorld());

        if (sameDimension) {
            // Same dimension: enable transmit and set waypoint color to match menu
            target.setCachedPortalTarget(null);
            LocatorBarManager.enableTransmit(targetPlayer);
            WaypointColorManager.setPlayerWaypointColor(targetPlayer, target.getColor());
            debug("Same dimension - tracking player directly: " + targetPlayer.getName());
        } else {
            // Different dimension: send synthetic waypoint for portal
            Location portalTarget = getOrCalculatePortalTarget(tracker, target, targetPlayer.getWorld().getEnvironment());
            Location targetLocation = portalTarget != null ? portalTarget
                    : getFallbackLocation(tracker, targetPlayer.getWorld().getEnvironment());

            UUID waypointId = generateWaypointId(tracker.getUniqueId(), target.getTargetUUID());
            WaypointPacketSender.sendWaypoint(tracker, targetLocation, waypointId);
            debug("Cross-dimension - sent waypoint for portal to " + targetPlayer.getName());
        }
    }

    private void updateDeathTarget(Player tracker, PlayerCache cache, TrackedTarget target) {
        Location deathLocation = cache.getDeathLocation();

        if (deathLocation == null || deathLocation.getWorld() == null) {
            cache.stopTrackingDeath();
            debug("Death location null, stopped tracking");
            return;
        }

        boolean sameDimension = deathLocation.getWorld().equals(tracker.getWorld());
        Location targetLocation;

        if (sameDimension) {
            targetLocation = deathLocation;
        } else {
            Location portalTarget = getOrCalculatePortalTarget(tracker, target, deathLocation.getWorld().getEnvironment());
            targetLocation = portalTarget != null ? portalTarget
                    : getFallbackLocation(tracker, deathLocation.getWorld().getEnvironment());
        }

        UUID waypointId = generateWaypointId(tracker.getUniqueId(), UUID.fromString("00000000-0000-0000-0000-000000000001"));
        WaypointPacketSender.sendWaypoint(tracker, targetLocation, waypointId);
        debug("Updated death location waypoint");
    }

    private void updateBossBar(Player tracker, PlayerCache cache, TrackedTarget focusedTarget) {
        BossBar bossBar = playerBossBars.computeIfAbsent(tracker.getUniqueId(),
                k -> BossBar.bossBar(Component.empty(), 1.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS));

        if (focusedTarget != null) {
            // Show info about the focused target
            String targetName;
            int distance;
            String direction;
            TextColor color;

            Location targetLoc = getTargetLocation(tracker, cache, focusedTarget);
            if (targetLoc != null) {
                distance = (int) tracker.getLocation().distance(targetLoc);
                direction = getCardinalDirection(tracker, targetLoc);
            } else {
                distance = 0;
                direction = "?";
            }

            if (focusedTarget.isPlayer()) {
                Player targetPlayer = Bukkit.getPlayer(focusedTarget.getTargetUUID());
                targetName = targetPlayer != null ? targetPlayer.getName() : "Unknown";
                MarkerColor markerColor = focusedTarget.getColor();
                color = TextColor.color(markerColor.getRgb());

                boolean sameDimension = targetPlayer != null && targetPlayer.getWorld().equals(tracker.getWorld());
                if (!sameDimension && targetPlayer != null) {
                    targetName += " &7(" + getDimensionName(targetPlayer.getWorld().getEnvironment()) + ")";
                }
            } else {
                targetName = "Death Location";
                color = TextColor.color(MarkerColor.DARK_RED.getRgb());

                Location deathLoc = cache.getDeathLocation();
                if (deathLoc != null && !deathLoc.getWorld().equals(tracker.getWorld())) {
                    targetName += " &7(" + getDimensionName(deathLoc.getWorld().getEnvironment()) + ")";
                }
            }

            Component name = Component.text(targetName, color)
                    .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(distance + "m " + direction, NamedTextColor.GOLD));

            bossBar.name(name);
            bossBar.color(toBossBarColor(focusedTarget));

            // Progress based on distance (closer = more full, max 500m)
            float progress = Math.max(0.0f, Math.min(1.0f, 1.0f - (distance / 500.0f)));
            bossBar.progress(progress);
        } else {
            // Not looking at any target - show general tracking info
            int trackingCount = cache.getTrackedTargets().size();
            Component name = Component.text("Tracking " + trackingCount + " target" + (trackingCount != 1 ? "s" : ""),
                    NamedTextColor.GRAY);
            bossBar.name(name);
            bossBar.color(BossBar.Color.WHITE);
            bossBar.progress(1.0f);
        }

        tracker.showBossBar(bossBar);
    }

    private BossBar.Color toBossBarColor(TrackedTarget target) {
        if (target.isDeath()) return BossBar.Color.RED;

        MarkerColor color = target.getColor();
        return switch (color) {
            case RED, DARK_RED -> BossBar.Color.RED;
            case ORANGE, YELLOW -> BossBar.Color.YELLOW;
            case LIME, GREEN -> BossBar.Color.GREEN;
            case CYAN, LIGHT_BLUE, BLUE -> BossBar.Color.BLUE;
            case PURPLE, MAGENTA, PINK -> BossBar.Color.PINK;
            default -> BossBar.Color.WHITE;
        };
    }

    private void hideBossBar(Player player) {
        BossBar bossBar = playerBossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
        LocatorBarManager.disableReceive(player);
        LocatorBarManager.clearTarget(player);
    }

    private Location getOrCalculatePortalTarget(Player tracker, TrackedTarget target, World.Environment targetDimension) {
        Location cached = target.getCachedPortalTarget();

        if (cached != null && cached.getWorld() != null && cached.getWorld().equals(tracker.getWorld())) {
            return cached;
        }

        Location portalTarget = findPortalToDimension(tracker, targetDimension);
        target.setCachedPortalTarget(portalTarget);
        return portalTarget;
    }

    private Location findPortalToDimension(Player tracker, World.Environment targetDimension) {
        World trackerWorld = tracker.getWorld();
        Location trackerLoc = tracker.getLocation();
        World.Environment currentEnv = trackerWorld.getEnvironment();

        PlayerCache trackerCache = PlayerCache.from(tracker);

        // Check player's stored portal locations
        Location storedPortal = getStoredPortal(trackerCache, currentEnv, targetDimension);
        if (isValidPortal(storedPortal, trackerWorld)) {
            return storedPortal;
        }

        // Fallback to global PortalCache
        return PortalCache.findNearestToDimension(trackerWorld, trackerLoc, targetDimension);
    }

    private Location getStoredPortal(PlayerCache cache, World.Environment currentEnv, World.Environment targetEnv) {
        if (currentEnv == World.Environment.NORMAL) {
            if (targetEnv == World.Environment.NETHER) {
                return cache.getOverworldNetherPortalLocation();
            } else if (targetEnv == World.Environment.THE_END) {
                return cache.getOverworldEndPortalLocation();
            }
        } else if (currentEnv == World.Environment.NETHER || currentEnv == World.Environment.THE_END) {
            if (targetEnv == World.Environment.NORMAL) {
                return cache.getPortalLocation();
            }
        }
        return null;
    }

    private boolean isValidPortal(Location portal, World expectedWorld) {
        return portal != null && portal.getWorld() != null && portal.getWorld().equals(expectedWorld);
    }

    private Location getFallbackLocation(Player tracker, World.Environment targetDimension) {
        World trackerWorld = tracker.getWorld();

        if (targetDimension == World.Environment.NORMAL && trackerWorld.getEnvironment() != World.Environment.NORMAL) {
            return new Location(trackerWorld, 0.5, 64, 0.5);
        }

        return trackerWorld.getSpawnLocation();
    }

    private String getCardinalDirection(Player tracker, Location target) {
        Location trackerLoc = tracker.getLocation();
        double dx = target.getX() - trackerLoc.getX();
        double dz = target.getZ() - trackerLoc.getZ();

        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        if (angle < 0) angle += 360;

        if (angle >= 337.5 || angle < 22.5) return "N";
        if (angle >= 22.5 && angle < 67.5) return "NE";
        if (angle >= 67.5 && angle < 112.5) return "E";
        if (angle >= 112.5 && angle < 157.5) return "SE";
        if (angle >= 157.5 && angle < 202.5) return "S";
        if (angle >= 202.5 && angle < 247.5) return "SW";
        if (angle >= 247.5 && angle < 292.5) return "W";
        return "NW";
    }

    private String getDimensionName(World.Environment env) {
        return switch (env) {
            case NORMAL -> "Overworld";
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> "Unknown";
        };
    }

    private UUID generateWaypointId(UUID trackerUUID, UUID targetUUID) {
        return new UUID(
                WAYPOINT_NAMESPACE.getMostSignificantBits() ^ trackerUUID.getMostSignificantBits(),
                WAYPOINT_NAMESPACE.getLeastSignificantBits() ^ targetUUID.getLeastSignificantBits());
    }

    private boolean isEnvironmentAllowed(Player player) {
        String allowed = Settings.LocatorSection.ALLOWED_ENVIRONEMENTS.toLowerCase();
        return switch (allowed) {
            case "all" -> true;
            case "normal" -> player.getWorld().getEnvironment() == World.Environment.NORMAL;
            case "nether" -> player.getWorld().getEnvironment() == World.Environment.NETHER;
            case "the end" -> player.getWorld().getEnvironment() == World.Environment.THE_END;
            default -> false;
        };
    }

    /**
     * Clean up boss bar for a player (call on quit).
     */
    public static void cleanupPlayer(UUID playerUUID) {
        BossBar bossBar = playerBossBars.remove(playerUUID);
        if (bossBar != null) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                player.hideBossBar(bossBar);
            }
        }
    }

    /**
     * Clean up all boss bars (call on plugin disable).
     */
    public static void cleanupAll() {
        for (Map.Entry<UUID, BossBar> entry : playerBossBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        playerBossBars.clear();
    }

    private void debug(String message) {
        if (DEBUG) {
            SMPPlugin.getInstance().getLogger().log(Level.INFO, "[LocatorTask Debug] " + message);
        }
    }
}
