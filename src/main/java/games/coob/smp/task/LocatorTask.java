package games.coob.smp.task;

import games.coob.smp.PlayerCache;
import games.coob.smp.SMPPlugin;
import games.coob.smp.settings.Settings;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.tracking.PortalCache;
import games.coob.smp.tracking.TrackingRegistry;
import games.coob.smp.tracking.WaypointPacketSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Periodic task that updates the locator bar for active trackers only.
 * Runs every second (20 ticks) and only processes players in the
 * TrackingRegistry.
 */
public final class LocatorTask extends BukkitRunnable {

	private static final boolean DEBUG = true;

	// UUID namespace for synthetic portal waypoints
	private static final UUID PORTAL_WAYPOINT_NAMESPACE = UUID.fromString("11111111-1111-1111-1111-111111111111");

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
			hideLocatorBar(tracker);
			return;
		}

		PlayerCache cache = PlayerCache.from(tracker);
		String trackingType = cache.getTrackingLocation();

		if (trackingType == null) {
			hideLocatorBar(tracker);
			TrackingRegistry.stopTracking(tracker.getUniqueId());
			return;
		}

		switch (trackingType) {
			case "Player" -> updatePlayerTracking(tracker, cache);
			case "Death" -> updateDeathTracking(tracker, cache);
			default -> hideLocatorBar(tracker);
		}
	}

	private void updatePlayerTracking(Player tracker, PlayerCache cache) {
		Player target = cache.getTargetByUUID() != null ? Bukkit.getPlayer(cache.getTargetByUUID()) : null;

		debug("updatePlayerTracking: tracker=" + tracker.getName() +
				", targetUUID=" + cache.getTargetByUUID() +
				", target=" + (target != null ? target.getName() : "null"));

		if (target == null || !target.isOnline()) {
			debug("  Target is null or offline, stopping tracking");
			stopTracking(tracker, cache);
			return;
		}

		boolean sameDimension = target.getWorld().equals(tracker.getWorld());
		debug("  Tracker world: " + tracker.getWorld().getName() +
				" (" + tracker.getWorld().getEnvironment() + ")");
		debug("  Target world: " + target.getWorld().getName() +
				" (" + target.getWorld().getEnvironment() + ")");
		debug("  Same dimension: " + sameDimension);

		if (sameDimension) {
			// Same dimension: track the player directly via locator bar
			cache.setCachedPortalTarget(null);
			WaypointPacketSender.clearWaypoint(tracker); // Remove any synthetic waypoint
			LocatorBarManager.enableTransmit(target);
			LocatorBarManager.enableReceive(tracker);
			LocatorBarManager.setTarget(tracker, target);
			debug("  Same dimension - using locator bar to track player directly");
		} else {
			// Different dimension: try to send synthetic waypoint packet for portal
			// location
			Location portalTarget = getOrCalculatePortalTarget(tracker, cache, target.getWorld().getEnvironment());
			debug("  Different dimension - portal target: " + formatLocation(portalTarget));

			Location targetLocation = portalTarget != null ? portalTarget
					: getFallbackLocation(tracker, target.getWorld().getEnvironment());

			if (portalTarget == null) {
				debug("  No portal found, using fallback: " + formatLocation(targetLocation));
			}

			// Try to send synthetic waypoint packet
			UUID waypointId = generateWaypointId(tracker.getUniqueId());
			boolean waypointSent = WaypointPacketSender.sendWaypoint(tracker, targetLocation, waypointId);

			if (waypointSent) {
				// Waypoint packet sent successfully - enable locator bar
				LocatorBarManager.enableReceive(tracker);
				debug("  Synthetic waypoint packet sent successfully");
			} else {
				// Waypoint packets not available - disable locator bar (only show action bar)
				LocatorBarManager.disableReceive(tracker);
				debug("  Waypoint packets not available, using action bar only");
			}

			// Always set compass target for compass items and send action bar feedback
			LocatorBarManager.setTarget(tracker, targetLocation);
			sendCrossDimensionFeedback(tracker, target.getName(), targetLocation, target.getWorld().getEnvironment());
		}
	}

	private void updateDeathTracking(Player tracker, PlayerCache cache) {
		Location deathLocation = cache.getDeathLocation();

		debug("updateDeathTracking: tracker=" + tracker.getName() +
				", deathLocation=" + formatLocation(deathLocation));

		if (deathLocation == null || deathLocation.getWorld() == null) {
			debug("  Death location is null or has null world, stopping tracking");
			stopTracking(tracker, cache);
			return;
		}

		boolean sameDimension = deathLocation.getWorld().equals(tracker.getWorld());
		debug("  Same dimension: " + sameDimension);

		if (sameDimension) {
			// Same dimension: try to send synthetic waypoint for death location
			UUID waypointId = generateWaypointId(tracker.getUniqueId());
			boolean waypointSent = WaypointPacketSender.sendWaypoint(tracker, deathLocation, waypointId);

			if (waypointSent) {
				LocatorBarManager.enableReceive(tracker);
				debug("  Synthetic waypoint for death location sent successfully");
			} else {
				LocatorBarManager.disableReceive(tracker);
				debug("  Waypoint packets not available, using action bar only");
			}

			LocatorBarManager.setTarget(tracker, deathLocation);
			sendDeathLocationFeedback(tracker, deathLocation);
		} else {
			// Different dimension: guide to portal
			Location portalTarget = getOrCalculatePortalTarget(tracker, cache,
					deathLocation.getWorld().getEnvironment());
			debug("  Different dimension - portal target: " + formatLocation(portalTarget));

			Location targetLocation = portalTarget != null ? portalTarget
					: getFallbackLocation(tracker, deathLocation.getWorld().getEnvironment());

			if (portalTarget == null) {
				debug("  No portal found, using fallback: " + formatLocation(targetLocation));
			}

			UUID waypointId = generateWaypointId(tracker.getUniqueId());
			boolean waypointSent = WaypointPacketSender.sendWaypoint(tracker, targetLocation, waypointId);

			if (waypointSent) {
				LocatorBarManager.enableReceive(tracker);
				debug("  Synthetic waypoint for portal sent successfully");
			} else {
				LocatorBarManager.disableReceive(tracker);
				debug("  Waypoint packets not available, using action bar only");
			}

			LocatorBarManager.setTarget(tracker, targetLocation);
			sendCrossDimensionFeedback(tracker, "Death Location", targetLocation,
					deathLocation.getWorld().getEnvironment());
		}
	}

	/**
	 * Get the cached portal target, or calculate it if not cached/invalid.
	 */
	private Location getOrCalculatePortalTarget(Player tracker, PlayerCache cache, World.Environment targetDimension) {
		Location cached = cache.getCachedPortalTarget();

		// Check if cached portal is still valid (in tracker's current world)
		if (cached != null && cached.getWorld() != null && cached.getWorld().equals(tracker.getWorld())) {
			return cached;
		}

		// Calculate new portal target
		Location portalTarget = findPortalToDimension(tracker, cache, targetDimension);
		cache.setCachedPortalTarget(portalTarget);
		return portalTarget;
	}

	/**
	 * Find the best portal to reach the target dimension.
	 */
	private Location findPortalToDimension(Player tracker, PlayerCache trackerCache,
			World.Environment targetDimension) {
		World trackerWorld = tracker.getWorld();
		Location trackerLoc = tracker.getLocation();
		World.Environment currentEnv = trackerWorld.getEnvironment();

		// First check player's stored portal locations (most efficient)
		Location storedPortal = getStoredPortal(trackerCache, currentEnv, targetDimension);
		if (isValidPortal(storedPortal, trackerWorld)) {
			return storedPortal;
		}

		// If tracking a player, check their stored portals too
		if ("Player".equals(trackerCache.getTrackingLocation()) && trackerCache.getTargetByUUID() != null) {
			Player target = Bukkit.getPlayer(trackerCache.getTargetByUUID());
			if (target != null && target.isOnline()) {
				PlayerCache targetCache = PlayerCache.from(target);
				Location targetPortal = getStoredPortal(targetCache, currentEnv, targetDimension);
				if (isValidPortal(targetPortal, trackerWorld)) {
					// Return the nearest stored portal
					if (storedPortal == null) {
						return targetPortal;
					}
					return trackerLoc.distanceSquared(storedPortal) <= trackerLoc.distanceSquared(targetPortal)
							? storedPortal
							: targetPortal;
				}
			}
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

	/**
	 * Get a fallback location when no portal is found.
	 */
	private Location getFallbackLocation(Player tracker, World.Environment targetDimension) {
		World trackerWorld = tracker.getWorld();

		// If target is in overworld and tracker is in nether/end, point to 0,64,0 area
		if (targetDimension == World.Environment.NORMAL && trackerWorld.getEnvironment() != World.Environment.NORMAL) {
			return new Location(trackerWorld, 0.5, 64, 0.5);
		}

		// Otherwise point to world spawn
		return trackerWorld.getSpawnLocation();
	}

	/**
	 * Send action bar feedback for cross-dimension tracking.
	 * The locator bar only shows player waypoints, so we use action bar for portal
	 * guidance.
	 */
	private void sendCrossDimensionFeedback(Player tracker, String targetName, Location portalLocation,
			World.Environment targetDimension) {
		int distance = (int) tracker.getLocation().distance(portalLocation);
		String direction = getCardinalDirection(tracker, portalLocation);
		String dimensionName = getDimensionName(targetDimension);

		Component message = Component.text(targetName + " is in the " + dimensionName + " ", NamedTextColor.GRAY)
				.append(Component.text("| ", NamedTextColor.DARK_GRAY))
				.append(Component.text("Portal: " + distance + "m " + direction, NamedTextColor.GOLD));

		tracker.sendActionBar(message);
	}

	/**
	 * Send action bar feedback for death location tracking (same dimension).
	 */
	private void sendDeathLocationFeedback(Player tracker, Location deathLocation) {
		int distance = (int) tracker.getLocation().distance(deathLocation);
		String direction = getCardinalDirection(tracker, deathLocation);

		Component message = Component.text("Death Location: ", NamedTextColor.RED)
				.append(Component.text(distance + "m " + direction, NamedTextColor.GOLD));

		tracker.sendActionBar(message);
	}

	/**
	 * Get the cardinal direction from the tracker to the target location.
	 */
	private String getCardinalDirection(Player tracker, Location target) {
		Location trackerLoc = tracker.getLocation();
		double dx = target.getX() - trackerLoc.getX();
		double dz = target.getZ() - trackerLoc.getZ();

		double angle = Math.toDegrees(Math.atan2(-dx, dz));
		if (angle < 0)
			angle += 360;

		if (angle >= 337.5 || angle < 22.5)
			return "N";
		if (angle >= 22.5 && angle < 67.5)
			return "NE";
		if (angle >= 67.5 && angle < 112.5)
			return "E";
		if (angle >= 112.5 && angle < 157.5)
			return "SE";
		if (angle >= 157.5 && angle < 202.5)
			return "S";
		if (angle >= 202.5 && angle < 247.5)
			return "SW";
		if (angle >= 247.5 && angle < 292.5)
			return "W";
		return "NW";
	}

	/**
	 * Get a user-friendly name for the dimension.
	 */
	private String getDimensionName(World.Environment env) {
		return switch (env) {
			case NORMAL -> "Overworld";
			case NETHER -> "Nether";
			case THE_END -> "End";
			default -> "Unknown";
		};
	}

	private void stopTracking(Player tracker, PlayerCache cache) {
		debug("stopTracking: " + tracker.getName());
		cache.setTrackingLocation(null);
		cache.setTargetByUUID(null);
		cache.setCachedPortalTarget(null);
		WaypointPacketSender.clearWaypoint(tracker);
		hideLocatorBar(tracker);
		TrackingRegistry.stopTracking(tracker.getUniqueId());
	}

	private void hideLocatorBar(Player player) {
		LocatorBarManager.disableReceive(player);
		LocatorBarManager.clearTarget(player);
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
	 * Generate a unique waypoint ID for a tracker.
	 * Uses XOR with a namespace to ensure uniqueness.
	 */
	private UUID generateWaypointId(UUID trackerUUID) {
		return new UUID(
				PORTAL_WAYPOINT_NAMESPACE.getMostSignificantBits() ^ trackerUUID.getMostSignificantBits(),
				PORTAL_WAYPOINT_NAMESPACE.getLeastSignificantBits() ^ trackerUUID.getLeastSignificantBits());
	}

	private String formatLocation(Location loc) {
		if (loc == null)
			return "null";
		return String.format("%d, %d, %d in %s",
				loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
				loc.getWorld() != null ? loc.getWorld().getName() : "null");
	}

	private void debug(String message) {
		if (DEBUG) {
			SMPPlugin.getInstance().getLogger().log(Level.INFO, "[LocatorTask Debug] " + message);
		}
	}
}
