package games.coob.smp.task;

import games.coob.smp.PlayerCache;
import games.coob.smp.settings.Settings;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.tracking.PortalCache;
import games.coob.smp.tracking.TrackingRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Periodic task that updates the locator bar for active trackers only.
 * Runs every second (20 ticks) and only processes players in the
 * TrackingRegistry.
 */
public final class LocatorTask extends BukkitRunnable {

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

		if (target == null || !target.isOnline()) {
			stopTracking(tracker, cache);
			return;
		}

		if (target.getWorld().equals(tracker.getWorld())) {
			// Same dimension: track the player directly via locator bar
			cache.setCachedPortalTarget(null);
			LocatorBarManager.enableTransmit(target);
			LocatorBarManager.enableReceive(tracker);
			LocatorBarManager.setTarget(tracker, target);
		} else {
			// Different dimension: The locator bar can only show player waypoints, not
			// locations.
			// When target is in another dimension, they're not visible as a waypoint.
			// We use action bar to guide the player to the nearest portal.
			LocatorBarManager.disableReceive(tracker);

			Location portalTarget = getOrCalculatePortalTarget(tracker, cache, target.getWorld().getEnvironment());
			if (portalTarget != null) {
				// Still set compass target for players using compass items
				LocatorBarManager.setTarget(tracker, portalTarget);
				sendCrossDimensionFeedback(tracker, target.getName(), portalTarget, target.getWorld().getEnvironment());
			} else {
				Location fallback = getFallbackLocation(tracker, target.getWorld().getEnvironment());
				LocatorBarManager.setTarget(tracker, fallback);
				sendCrossDimensionFeedback(tracker, target.getName(), fallback, target.getWorld().getEnvironment());
			}
		}
	}

	private void updateDeathTracking(Player tracker, PlayerCache cache) {
		Location deathLocation = cache.getDeathLocation();

		if (deathLocation == null || deathLocation.getWorld() == null) {
			stopTracking(tracker, cache);
			return;
		}

		if (deathLocation.getWorld().equals(tracker.getWorld())) {
			// Same dimension: The locator bar cannot point to locations directly.
			// Show action bar with direction/distance to death location.
			LocatorBarManager.disableReceive(tracker);
			LocatorBarManager.setTarget(tracker, deathLocation);
			sendDeathLocationFeedback(tracker, deathLocation);
		} else {
			// Different dimension: guide to portal
			LocatorBarManager.disableReceive(tracker);

			Location portalTarget = getOrCalculatePortalTarget(tracker, cache,
					deathLocation.getWorld().getEnvironment());
			if (portalTarget != null) {
				LocatorBarManager.setTarget(tracker, portalTarget);
				sendCrossDimensionFeedback(tracker, "Death Location", portalTarget,
						deathLocation.getWorld().getEnvironment());
			} else {
				Location fallback = getFallbackLocation(tracker, deathLocation.getWorld().getEnvironment());
				LocatorBarManager.setTarget(tracker, fallback);
				sendCrossDimensionFeedback(tracker, "Death Location", fallback,
						deathLocation.getWorld().getEnvironment());
			}
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
		cache.setTrackingLocation(null);
		cache.setTargetByUUID(null);
		cache.setCachedPortalTarget(null);
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
}
