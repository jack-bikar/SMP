package games.coob.smp.task;

import games.coob.smp.PlayerCache;
import games.coob.smp.settings.Settings;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.tracking.TrackingRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

		// Only iterate through players who are actively tracking something
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
			// No longer tracking - clean up
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

		// Enable the target to be visible as a waypoint
		LocatorBarManager.enableTransmit(target);
		LocatorBarManager.enableReceive(tracker);

		if (target.getWorld().equals(tracker.getWorld())) {
			// Same dimension: track the player entity directly
			LocatorBarManager.setTarget(tracker, target);
		} else {
			// Different dimension: point to cached portal
			Location portalLocation = cache.getCachedPortalTarget();
			if (portalLocation != null && portalLocation.getWorld().equals(tracker.getWorld())) {
				LocatorBarManager.setTarget(tracker, portalLocation);
			} else {
				// Portal not cached for this dimension - hide until dimension change event
				// updates it
				LocatorBarManager.setTarget(tracker, tracker.getLocation());
			}
		}
	}

	private void updateDeathTracking(Player tracker, PlayerCache cache) {
		Location deathLocation = cache.getDeathLocation();

		if (deathLocation == null || deathLocation.getWorld() == null) {
			stopTracking(tracker, cache);
			return;
		}

		LocatorBarManager.enableReceive(tracker);

		if (deathLocation.getWorld().equals(tracker.getWorld())) {
			// Same dimension: point to death location
			LocatorBarManager.setTarget(tracker, deathLocation);
		} else {
			// Different dimension: point to cached portal
			Location portalLocation = cache.getCachedPortalTarget();
			if (portalLocation != null && portalLocation.getWorld().equals(tracker.getWorld())) {
				LocatorBarManager.setTarget(tracker, portalLocation);
			} else {
				LocatorBarManager.setTarget(tracker, tracker.getLocation());
			}
		}
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
			case "normal" -> player.getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL;
			case "nether" -> player.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER;
			case "the end" -> player.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END;
			default -> false;
		};
	}
}
