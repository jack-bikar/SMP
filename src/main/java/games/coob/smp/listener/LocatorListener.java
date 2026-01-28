package games.coob.smp.listener;

import games.coob.smp.PlayerCache;
import games.coob.smp.SMPPlugin;
import games.coob.smp.settings.Settings;
import games.coob.smp.tracking.LocatorBarGamerule;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.tracking.PortalRegistry;
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
import org.bukkit.event.world.PortalCreateEvent;

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
		// Only handle dimension changes if custom tracking is enabled.
		// IMPORTANT: PlayerTeleportEvent fires BEFORE the world switch happens. If we
		// set targets here,
		// Paper/vanilla can reset them during the dimension change. So we re-apply 1
		// tick later.
		if (Settings.LocatorSection.ENABLE_LOCATOR_BAR || !Settings.LocatorSection.ENABLE_TRACKING) {
			return;
		}

		if (event.getTo() == null || event.getTo().getWorld() == null || event.getFrom().getWorld() == null) {
			return;
		}
		if (event.getFrom().getWorld().equals(event.getTo().getWorld())) {
			return; // not a world/dimension change
		}

		final Player player = event.getPlayer();
		org.bukkit.Bukkit.getScheduler().runTaskLater(SMPPlugin.getInstance(), () -> applyTrackingState(player), 1L);
	}

	private static void applyTrackingState(Player player) {
		if (player == null || !player.isOnline())
			return;
		if (Settings.LocatorSection.ENABLE_LOCATOR_BAR || !Settings.LocatorSection.ENABLE_TRACKING)
			return;

		// Nether/End may have locator bar gamerule disabled; ensure it's enabled here
		LocatorBarGamerule.ensureEnabled(player.getWorld());

		final PlayerCache cache = PlayerCache.from(player);
		final LocatorBarManager locatorBar = new LocatorBarManager(player);

		if (cache.getTrackingLocation() == null) {
			// Not tracking -> hide
			locatorBar.disableTemporarily();
			player.setCompassTarget(player.getLocation());
			return;
		}

		if (cache.getTrackingLocation().equals("Player")) {
			final Player target = cache.getTargetByUUID() != null ? org.bukkit.Bukkit.getPlayer(cache.getTargetByUUID())
					: null;
			if (target == null || !target.isOnline()) {
				locatorBar.disableTemporarily();
				player.setCompassTarget(player.getLocation());
				cache.setTrackingLocation(null);
				cache.setTargetByUUID(null);
				return;
			}

			locatorBar.enableTemporarily();

			if (target.getWorld() == player.getWorld()) {
				player.setCompassTarget(target.getLocation());
			} else {
				Location portalLoc = getPortalForCrossDimension(cache, PlayerCache.from(target), player,
						target.getWorld().getEnvironment());
				player.setCompassTarget(portalLoc != null ? portalLoc : player.getLocation());
			}
			return;
		}

		if (cache.getTrackingLocation().equals("Death")) {
			final Location deathLoc = cache.getDeathLocation();
			if (deathLoc == null) {
				locatorBar.disableTemporarily();
				player.setCompassTarget(player.getLocation());
				cache.setTrackingLocation(null);
				return;
			}

			locatorBar.enableTemporarily();
			if (deathLoc.getWorld() == player.getWorld()) {
				player.setCompassTarget(deathLoc);
			} else {
				Location portalLoc = getPortalForCrossDimension(cache, null, player,
						deathLoc.getWorld().getEnvironment());
				player.setCompassTarget(portalLoc != null ? portalLoc : player.getLocation());
			}
		}
	}

	private static Location getPortalForCrossDimension(PlayerCache trackerCache, PlayerCache targetCache,
			org.bukkit.entity.Player player,
			World.Environment targetEnv) {
		final World trackerWorld = player.getWorld();
		final Location origin = player.getLocation();
		Location best = null;

		// Helper: pick nearest portal (must be in tracker world)
		java.util.function.BiFunction<Location, Location, Location> pickNearest = (a, b) -> {
			Location curBest = a;
			double curBestDist = (a != null && a.getWorld() == trackerWorld) ? origin.distanceSquared(a)
					: Double.MAX_VALUE;
			if (b != null && b.getWorld() == trackerWorld) {
				double d = origin.distanceSquared(b);
				if (d < curBestDist) {
					curBest = b;
				}
			}
			return curBest;
		};

		if (trackerWorld.getEnvironment() == World.Environment.NORMAL) {
			if (targetEnv == World.Environment.NETHER) {
				best = pickNearest.apply(trackerCache.getOverworldNetherPortalLocation(),
						targetCache != null ? targetCache.getOverworldNetherPortalLocation() : null);
			} else if (targetEnv == World.Environment.THE_END) {
				best = pickNearest.apply(trackerCache.getOverworldEndPortalLocation(),
						targetCache != null ? targetCache.getOverworldEndPortalLocation() : null);
			}
		} else if (trackerWorld.getEnvironment() == World.Environment.NETHER
				|| trackerWorld.getEnvironment() == World.Environment.THE_END) {
			if (targetEnv == World.Environment.NORMAL) {
				best = pickNearest.apply(trackerCache.getPortalLocation(),
						targetCache != null ? targetCache.getPortalLocation() : null);
			}
		}

		if (best != null)
			return best;
		return games.coob.smp.tracking.PortalFinder.findNearestPortalToDimension(trackerWorld, origin, targetEnv);
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

	@EventHandler
	public void onPortalCreate(final PortalCreateEvent event) {
		// Only track portal locations if custom tracking is enabled
		if (Settings.LocatorSection.ENABLE_LOCATOR_BAR || !Settings.LocatorSection.ENABLE_TRACKING) {
			return;
		}

		// Register nether portal creations (this gives us a cheap nearest-portal lookup
		// later)
		Location center = null;
		int count = 0;
		double sx = 0, sy = 0, sz = 0;

		for (org.bukkit.block.BlockState state : event.getBlocks()) {
			if (state == null)
				continue;
			if (state.getType() != Material.NETHER_PORTAL)
				continue;
			Location l = state.getLocation();
			if (l == null || l.getWorld() == null)
				continue;
			sx += l.getX() + 0.5;
			sy += l.getY() + 0.5;
			sz += l.getZ() + 0.5;
			count++;
		}

		if (count > 0) {
			center = new Location(event.getWorld(), sx / count, sy / count, sz / count);
			PortalRegistry.registerPortal(center, Material.NETHER_PORTAL);
		}
	}
}
