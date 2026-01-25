package games.coob.smp.listener;

import games.coob.smp.PlayerCache;
import games.coob.smp.settings.Settings;
import games.coob.smp.tracking.LocatorBarManager;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LocatorListener implements Listener {

	private static final LocatorListener instance = new LocatorListener();

	public static LocatorListener getInstance() {
		return instance;
	}

	@EventHandler
	public void onPlayerInteract(final PlayerInteractEvent event) {
		// Locator functionality removed - use /tracking menu command instead
	}

	@EventHandler
	public void onPlayerDimensionChange(final PlayerTeleportEvent event) {
		// Only handle dimension changes if custom tracking is enabled
		if (Settings.LocatorSection.ENABLE_LOCATOR_BAR || !Settings.LocatorSection.ENABLE_TRACKING) {
			return;
		}

		final Player player = event.getPlayer();
		final PlayerCache cache = PlayerCache.from(player);
		LocatorBarManager locatorBar = new LocatorBarManager(player);

		// Update Player Locator Bar if tracking a player or death location
		if (cache.getTrackingLocation() != null) {
			if (cache.getTrackingLocation().equals("Player")) {
				final Player target = cache.getTargetByUUID() != null ? 
					org.bukkit.Bukkit.getPlayer(cache.getTargetByUUID()) : null;
				if (target != null && target.isOnline()) {
					if (target.getWorld() == player.getWorld()) {
						// Same world - enable locator bar and point to target
						locatorBar.enableTemporarily();
						player.setCompassTarget(target.getLocation());
					} else {
						// Different dimension - point to portal if available
						Location portalLoc = cache.getPortalLocation();
						if (portalLoc != null && portalLoc.getWorld() == player.getWorld()) {
							locatorBar.enableTemporarily();
							player.setCompassTarget(portalLoc);
						} else {
							locatorBar.disableTemporarily();
							player.setCompassTarget(player.getLocation());
						}
					}
				}
			} else if (cache.getTrackingLocation().equals("Death")) {
				final Location deathLoc = cache.getDeathLocation();
				if (deathLoc != null) {
					if (deathLoc.getWorld() == player.getWorld()) {
						// Same world - enable locator bar and point to death location
						locatorBar.enableTemporarily();
						player.setCompassTarget(deathLoc);
					} else {
						// Different dimension - point to portal if available
						Location portalLoc = cache.getPortalLocation();
						if (portalLoc != null && portalLoc.getWorld() == player.getWorld()) {
							locatorBar.enableTemporarily();
							player.setCompassTarget(portalLoc);
						} else {
							locatorBar.disableTemporarily();
							player.setCompassTarget(player.getLocation());
						}
					}
				}
			}
		}
	}

	@EventHandler
	public void onEntityPortalEnter(final EntityPortalEnterEvent event) {
		// Only track portal locations if custom tracking is enabled
		if (Settings.LocatorSection.ENABLE_LOCATOR_BAR || !Settings.LocatorSection.ENABLE_TRACKING) {
			return;
		}

		final Entity entity = event.getEntity();
		final Location location = event.getLocation();

		if (entity instanceof final Player player) {
			final PlayerCache cache = PlayerCache.from(player);

			// Store portal location for cross-dimension tracking
			// Store for both nether and end portals
			if (player.getWorld().getEnvironment() == World.Environment.NETHER || 
			    player.getWorld().getEnvironment() == World.Environment.THE_END) {
				cache.setPortalLocation(location);
			}
		}
	}
}
