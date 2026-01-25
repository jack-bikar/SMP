package games.coob.smp.task;

import games.coob.smp.PlayerCache;
import games.coob.smp.settings.Settings;
import games.coob.smp.tracking.LocatorBarManager;
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
		
		// Enable waypoint transmission for target so they appear as a waypoint
		targetLocatorBar.enableTransmit();
		
		// Check if target is in same world
		if (target.getWorld() == player.getWorld()) {
			// Same world - enable locator bar and point to target player
			locatorBar.enableTemporarily();
			// Set compass target to point to the target player's location
			player.setCompassTarget(target.getLocation());
		} else {
			// Different dimension - point to portal location if available
			Location portalLoc = getPortalLocation(player, target.getWorld().getEnvironment());
			if (portalLoc != null && isEnvironmentAllowed(portalLoc.getWorld().getEnvironment())) {
				// Enable locator bar and point to portal location
				locatorBar.enableTemporarily();
				player.setCompassTarget(portalLoc);
			} else {
				// No portal location found, hide Player Locator Bar
				locatorBar.disableTemporarily();
				// Reset compass target to player's location to hide indicator
				player.setCompassTarget(player.getLocation());
			}
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
			// Different dimension - point to portal location if available
			Location portalLoc = getPortalLocation(player, deathLoc.getWorld().getEnvironment());
			if (portalLoc != null && isEnvironmentAllowed(portalLoc.getWorld().getEnvironment())) {
				// Enable locator bar and point to portal location
				locatorBar.enableTemporarily();
				player.setCompassTarget(portalLoc);
			} else {
				// No portal location found, hide Player Locator Bar
				locatorBar.disableTemporarily();
				// Reset compass target to player's location to hide indicator
				player.setCompassTarget(player.getLocation());
			}
		}
	}

	private Location getPortalLocation(Player player, World.Environment targetEnvironment) {
		PlayerCache cache = PlayerCache.from(player);
		Location portalLoc = cache.getPortalLocation();
		World.Environment playerEnv = player.getWorld().getEnvironment();
		
		// Portal location is stored when player enters nether/end
		// If player is in nether/end and tracking someone in overworld, point to the portal they used
		// This helps them find their way back to the overworld where the target is
		if (portalLoc != null && portalLoc.getWorld() != null) {
			World.Environment portalEnv = portalLoc.getWorld().getEnvironment();
			
			// If player is in nether/end and portal is in same dimension, use it
			// This points to the portal that leads back to overworld (where target likely is)
			if (playerEnv == portalEnv && 
			    (playerEnv == World.Environment.NETHER || playerEnv == World.Environment.THE_END)) {
				// Player is in nether/end, portal is in nether/end
				// This portal leads back to overworld where target might be
				return portalLoc;
			}
			
			// If player is in overworld and portal is in nether/end, and target is in that dimension
			// We can't easily find the overworld portal, so we'll return null
			// (This is a limitation - we'd need to store both portal locations)
		}
		
		return null;
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
