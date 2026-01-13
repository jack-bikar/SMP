package games.coob.smp.listener;

import games.coob.smp.PlayerCache;
import games.coob.smp.menu.CompassMenu;
import games.coob.smp.settings.Settings;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import games.coob.smp.util.SchedulerUtil;
import org.bukkit.Material;
import org.bukkit.Sound;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CompassListener implements Listener {

	private static final CompassListener instance = new CompassListener();

	public static CompassListener getInstance() {
		return instance;
	}

	@EventHandler
	public void onPlayerInteract(final PlayerInteractEvent event) {
		final Action action = event.getAction();
		final Player player = event.getPlayer();

		if (Settings.CompassSection.ENABLE_COMPASS) {
			if (player.getInventory().getItemInMainHand().getType() == Material.COMPASS) {
				if (action.equals(Action.RIGHT_CLICK_AIR) || action.equals(Action.RIGHT_CLICK_BLOCK) || action.equals(Action.PHYSICAL)) {
					player.playSound(player.getLocation(), Sound.BLOCK_LAVA_POP, 1.0f, 1.0f);

					CompassMenu.openMenu(player);
				}
			}
		}
	}

	@EventHandler
	public void onPlayerDimensionChange(final PlayerTeleportEvent event) {
		if (Settings.CompassSection.ENABLE_COMPASS) {
			final Player player = event.getPlayer();
			final PlayerCache cache = PlayerCache.from(player);

			if (Settings.CompassSection.ALLOWED_ENVIRONEMENTS.equals("normal") || Settings.CompassSection.ALLOWED_ENVIRONEMENTS.equals("all")) {
				if (player.getWorld().getEnvironment() != World.Environment.NORMAL) {
					if (cache.getTrackingLocation() != null) {
						if (cache.getTrackingLocation().equals("Death"))
							player.setCompassTarget(cache.getDeathLocation());
						else if (cache.getTrackingLocation().equals("Bed")) {
							@SuppressWarnings("deprecation")
							Location bedLocation = player.getBedSpawnLocation();
							if (bedLocation != null) {
								SchedulerUtil.runLater(1, () -> player.setCompassTarget(bedLocation));
							}
						}

						if (cache.getPortalLocation() != null) {
						if (player.getInventory().contains(Material.COMPASS))
							for (int i = 0; i <= player.getInventory().getSize(); i++) {
								final ItemStack item = player.getInventory().getItem(i);

								if (item != null && item.getType() == Material.COMPASS) {
										final CompassMeta compass = (CompassMeta) item.getItemMeta();

										if (compass.hasLodestone()) {
											compass.setLodestone(null);
											item.setItemMeta(compass);
										}
									}
								}
						}
					}
				} else {
					if (Settings.CompassSection.ALLOWED_ENVIRONEMENTS.equals("nether") || Settings.CompassSection.ALLOWED_ENVIRONEMENTS.equals("all")) {
						if (player.getInventory().contains(Material.COMPASS) && cache.getPortalLocation() != null) {
							for (int i = 0; i <= player.getInventory().getSize(); i++) {
								final ItemStack item = player.getInventory().getItem(i);

								if (item != null && item.getType() == Material.COMPASS) {
									final CompassMeta compass = (CompassMeta) item.getItemMeta();

									if (cache.getTrackingLocation() != null) {
										if (cache.getTrackingLocation().equals("Death")) {
											final Location location = cache.getDeathLocation();

											compass.setLodestone(location);
											compass.setLodestoneTracked(false); // we do not want a real lodestone to be present at that location.
											item.setItemMeta(compass);
										} else if (cache.getTrackingLocation().equals("Bed")) {
											final Location portalLocation = cache.getPortalLocation();

											compass.setLodestone(portalLocation);
											compass.setLodestoneTracked(false); // we do not want a real lodestone to be present at that location.
											item.setItemMeta(compass);
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

	@EventHandler
	public void onEntityPortalEnter(final EntityPortalEnterEvent event) {
		final Entity entity = event.getEntity();
		final Location location = event.getLocation();

		if (entity instanceof final Player player) {
			final PlayerCache cache = PlayerCache.from(player);

			if (player.getWorld().getEnvironment() == World.Environment.NETHER)
				cache.setPortalLocation(location);
		}
	}
}