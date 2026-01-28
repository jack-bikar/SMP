package games.coob.smp.task;

import games.coob.smp.PlayerCache;
import games.coob.smp.settings.Settings;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.tracking.LocatorBarGamerule;
import games.coob.smp.tracking.PortalFinder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class LocatorTask extends BukkitRunnable {
	@Override
	public void run() {
		// Only run if locator bar is disabled (custom tracking enabled)
		if (Settings.LocatorSection.ENABLE_LOCATOR_BAR) {
			return;
		}

		if (!Settings.LocatorSection.ENABLE_TRACKING) {
			return;
		}

		for (final Player player : Bukkit.getOnlinePlayers()) {
			// Ensure locator bar gamerule is enabled in this world (nether/end too)
			LocatorBarGamerule.ensureEnabled(player.getWorld());

			// Check if tracking is allowed in this environment
			if (!isEnvironmentAllowed(player.getWorld().getEnvironment())) {
				// Hide Player Locator Bar in disallowed environments
				LocatorBarManager locatorBar = new LocatorBarManager(player);
				locatorBar.disableTemporarily();
				// Reset compass target to player's location to hide indicator
				player.setCompassTarget(player.getLocation());
				continue;
			}

			final PlayerCache cache = PlayerCache.from(player);

			if (cache.getTrackingLocation() != null) {
				if (cache.getTrackingLocation().equals("Player")) {
					handlePlayerTracking(player, cache);
				} else if (cache.getTrackingLocation().equals("Death")) {
					handleDeathTracking(player, cache);
				}
			} else {
				// Not tracking anything, hide Player Locator Bar
				LocatorBarManager locatorBar = new LocatorBarManager(player);
				locatorBar.disableTemporarily();
				// Reset compass target to player's location to hide indicator
				player.setCompassTarget(player.getLocation());
			}
		}
	}

	private void handlePlayerTracking(Player player, PlayerCache cache) {
		final Player target = cache.getTargetByUUID() != null ? Bukkit.getPlayer(cache.getTargetByUUID()) : null;

		if (target == null || !target.isOnline()) {
			// Target offline, hide Player Locator Bar and clear tracking
			LocatorBarManager locatorBar = new LocatorBarManager(player);
			locatorBar.disableTemporarily();
			// Reset compass target to player's location to hide indicator
			player.setCompassTarget(player.getLocation());
			cache.setTrackingLocation(null);
			cache.setTargetByUUID(null);
			return;
		}

		LocatorBarManager locatorBar = new LocatorBarManager(player);
		LocatorBarManager targetLocatorBar = new LocatorBarManager(target);
		PlayerCache targetCache = PlayerCache.from(target);

		// Enable waypoint transmission for target so they appear as a waypoint
		targetLocatorBar.enableTransmit();

		// Check if target is in same world
		if (target.getWorld() == player.getWorld()) {
			// Same world - enable locator bar and point to target player
			locatorBar.enableTemporarily();
			// Set compass target to point to the target player's location
			player.setCompassTarget(target.getLocation());
		} else {
			// Different dimension - keep tracking, point to nearest portal last-used by
			// tracker OR target
			Location portalLoc = resolveCrossDimensionPortal(player, cache, targetCache,
					target.getWorld().getEnvironment());
			locatorBar.enableTemporarily();
			if (portalLoc != null) {
				player.setCompassTarget(portalLoc);
			} else {
				// No stored portal - try to find nearest in world
				portalLoc = findNearestPortalToDimension(player, target.getWorld().getEnvironment());
				if (portalLoc != null) {
					player.setCompassTarget(portalLoc);
				} else {
					// Fallback: point to world spawn (overworld) or last known portal
					Location fallback = getFallbackPortalTarget(player, target.getWorld().getEnvironment());
					player.setCompassTarget(fallback != null ? fallback : player.getLocation());
				}
			}
		}
	}

	private Location resolveCrossDimensionPortal(Player tracker, PlayerCache trackerCache, PlayerCache targetCache,
			World.Environment targetEnvironment) {
		final World trackerWorld = tracker.getWorld();
		final World.Environment trackerEnv = trackerWorld.getEnvironment();
		final Location trackerLoc = tracker.getLocation();

		final Location[] best = new Location[] { null };
		final double[] bestDistSq = new double[] { Double.MAX_VALUE };

		considerCandidate(best, bestDistSq, trackerWorld, trackerLoc, trackerCache.getPortalLocation());
		if (targetCache != null) {
			considerCandidate(best, bestDistSq, trackerWorld, trackerLoc, targetCache.getPortalLocation());
		}

		// Prefer the relevant "used portal" depending on which dimension we're in /
		// going to
		if (trackerEnv == World.Environment.NORMAL) {
			if (targetEnvironment == World.Environment.NETHER) {
				considerCandidate(best, bestDistSq, trackerWorld, trackerLoc,
						trackerCache.getOverworldNetherPortalLocation());
				if (targetCache != null)
					considerCandidate(best, bestDistSq, trackerWorld, trackerLoc,
							targetCache.getOverworldNetherPortalLocation());
			} else if (targetEnvironment == World.Environment.THE_END) {
				considerCandidate(best, bestDistSq, trackerWorld, trackerLoc,
						trackerCache.getOverworldEndPortalLocation());
				if (targetCache != null)
					considerCandidate(best, bestDistSq, trackerWorld, trackerLoc,
							targetCache.getOverworldEndPortalLocation());
			}
		} else if (trackerEnv == World.Environment.NETHER || trackerEnv == World.Environment.THE_END) {
			if (targetEnvironment == World.Environment.NORMAL) {
				considerCandidate(best, bestDistSq, trackerWorld, trackerLoc, trackerCache.getPortalLocation());
				if (targetCache != null)
					considerCandidate(best, bestDistSq, trackerWorld, trackerLoc, targetCache.getPortalLocation());
			}
		}

		// Fallback: if we couldn't find a "used portal", fall back to our existing
		// stored resolver
		if (best[0] == null) {
			best[0] = getPortalLocation(tracker, targetEnvironment);
		}
		return best[0];
	}

	private static void considerCandidate(Location[] best, double[] bestDistSq, World trackerWorld, Location origin,
			Location candidate) {
		if (candidate == null || candidate.getWorld() != trackerWorld)
			return;
		double d = origin.distanceSquared(candidate);
		if (d < bestDistSq[0]) {
			bestDistSq[0] = d;
			best[0] = candidate;
		}
	}

	private void handleDeathTracking(Player player, PlayerCache cache) {
		final Location deathLoc = cache.getDeathLocation();
		if (deathLoc == null) {
			// Death location invalid, hide Player Locator Bar
			LocatorBarManager locatorBar = new LocatorBarManager(player);
			locatorBar.disableTemporarily();
			// Reset compass target to player's location to hide indicator
			player.setCompassTarget(player.getLocation());
			cache.setTrackingLocation(null);
			return;
		}

		LocatorBarManager locatorBar = new LocatorBarManager(player);

		// Check if death location is in same world
		if (deathLoc.getWorld() == player.getWorld()) {
			// Same world - enable locator bar and point to death location
			locatorBar.enableTemporarily();
			player.setCompassTarget(deathLoc);
		} else {
			// Different dimension - always keep tracking, point to nearest portal that
			// leads to death location
			Location portalLoc = getPortalLocation(player, deathLoc.getWorld().getEnvironment());
			locatorBar.enableTemporarily();
			if (portalLoc != null) {
				player.setCompassTarget(portalLoc);
			} else {
				portalLoc = findNearestPortalToDimension(player, deathLoc.getWorld().getEnvironment());
				if (portalLoc != null) {
					player.setCompassTarget(portalLoc);
				} else {
					Location fallback = getFallbackPortalTarget(player, deathLoc.getWorld().getEnvironment());
					player.setCompassTarget(fallback != null ? fallback : player.getLocation());
				}
			}
		}
	}

	/**
	 * Get stored portal location that leads to target dimension.
	 * Tracker in overworld + target in nether -> overworld nether portal.
	 * Tracker in overworld + target in end -> overworld end portal.
	 * Tracker in nether/end + target in overworld -> nether/end portal (leads
	 * back).
	 */
	private Location getPortalLocation(Player player, World.Environment targetEnvironment) {
		PlayerCache cache = PlayerCache.from(player);
		World.Environment playerEnv = player.getWorld().getEnvironment();

		if (playerEnv == World.Environment.NORMAL) {
			// Tracker in overworld: need overworld-side portal to target dimension
			if (targetEnvironment == World.Environment.NETHER) {
				Location overworldNether = cache.getOverworldNetherPortalLocation();
				if (overworldNether != null && overworldNether.getWorld() != null) {
					return overworldNether;
				}
			}
			if (targetEnvironment == World.Environment.THE_END) {
				Location overworldEnd = cache.getOverworldEndPortalLocation();
				if (overworldEnd != null && overworldEnd.getWorld() != null) {
					return overworldEnd;
				}
			}
		} else if (playerEnv == World.Environment.NETHER || playerEnv == World.Environment.THE_END) {
			// Tracker in nether/end: portal in same dimension leads back to overworld
			if (targetEnvironment == World.Environment.NORMAL) {
				Location netherOrEndPortal = cache.getPortalLocation();
				if (netherOrEndPortal != null && netherOrEndPortal.getWorld() != null
						&& netherOrEndPortal.getWorld().getEnvironment() == playerEnv) {
					return netherOrEndPortal;
				}
			}
		}
		return null;
	}

	/**
	 * Find nearest portal in tracker's world that leads to target dimension.
	 */
	private Location findNearestPortalToDimension(Player player, World.Environment targetEnvironment) {
		return PortalFinder.findNearestPortalToDimension(player.getWorld(), player.getLocation(), targetEnvironment);
	}

	/**
	 * Fallback when no stored or found portal: overworld spawn if returning from
	 * nether/end, else player location.
	 */
	private Location getFallbackPortalTarget(Player player, World.Environment targetEnvironment) {
		World w = player.getWorld();
		if (targetEnvironment == World.Environment.NORMAL && w.getEnvironment() != World.Environment.NORMAL) {
			for (World world : Bukkit.getWorlds()) {
				if (world.getEnvironment() == World.Environment.NORMAL) {
					Location spawn = world.getSpawnLocation();
					if (spawn != null && spawn.getWorld() != null)
						return spawn;
					break;
				}
			}
		}
		return player.getLocation();
	}

	private boolean isEnvironmentAllowed(World.Environment environment) {
		String allowed = Settings.LocatorSection.ALLOWED_ENVIRONEMENTS.toLowerCase();
		if (allowed.equals("all")) {
			return true;
		}
		if (allowed.equals("normal") && environment == World.Environment.NORMAL) {
			return true;
		}
		if (allowed.equals("nether") && environment == World.Environment.NETHER) {
			return true;
		}
		if (allowed.equals("the end") && environment == World.Environment.THE_END) {
			return true;
		}
		return false;
	}
}
