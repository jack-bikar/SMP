package games.coob.smp.listener;

import games.coob.smp.PlayerCache;
import games.coob.smp.settings.Settings;
import games.coob.smp.tracking.LocatorBarManager;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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
						// Different dimension - always keep tracking, point to portal
						Location portalLoc = getPortalForCrossDimension(cache, player, target.getWorld().getEnvironment());
						locatorBar.enableTemporarily();
						player.setCompassTarget(portalLoc != null ? portalLoc : player.getLocation());
					}
				}
			} else if (cache.getTrackingLocation().equals("Death")) {
				final Location deathLoc = cache.getDeathLocation();
				if (deathLoc != null) {
					if (deathLoc.getWorld() == player.getWorld()) {
						locatorBar.enableTemporarily();
						player.setCompassTarget(deathLoc);
					} else {
						Location portalLoc = getPortalForCrossDimension(cache, player, deathLoc.getWorld().getEnvironment());
						locatorBar.enableTemporarily();
						player.setCompassTarget(portalLoc != null ? portalLoc : player.getLocation());
					}
				}
			}
		}
	}

	private static Location getPortalForCrossDimension(PlayerCache cache, org.bukkit.entity.Player player, World.Environment targetEnv) {
		Location portal = cache.getPortalLocation();
		if (portal != null && portal.getWorld() == player.getWorld()) {
			if (targetEnv == World.Environment.NORMAL && (player.getWorld().getEnvironment() == World.Environment.NETHER || player.getWorld().getEnvironment() == World.Environment.THE_END)) {
				return portal;
			}
		}
		portal = cache.getOverworldNetherPortalLocation();
		if (portal != null && portal.getWorld() == player.getWorld() && targetEnv == World.Environment.NETHER) return portal;
		portal = cache.getOverworldEndPortalLocation();
		if (portal != null && portal.getWorld() == player.getWorld() && targetEnv == World.Environment.THE_END) return portal;
		return games.coob.smp.tracking.PortalFinder.findNearestPortalToDimension(player.getWorld(), player.getLocation(), targetEnv);
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
			final World.Environment env = player.getWorld().getEnvironment();

			// Store portal location for cross-dimension tracking
			if (env == World.Environment.NORMAL) {
				// Overworld: entering nether or end portal
				Block block = location.getBlock();
				Material type = block.getType();
				if (type == Material.NETHER_PORTAL) {
					cache.setOverworldNetherPortalLocation(location.clone().add(0.5, 0.5, 0.5));
				} else if (type == Material.END_PORTAL || type == Material.END_PORTAL_FRAME) {
					// End portal frame - use block center
					cache.setOverworldEndPortalLocation(location.clone().add(0.5, 0.5, 0.5));
				}
			} else if (env == World.Environment.NETHER || env == World.Environment.THE_END) {
				// Nether/End: entering portal (leads back to overworld)
				cache.setPortalLocation(location.clone().add(0.5, 0.5, 0.5));
			}
		}
	}
}
