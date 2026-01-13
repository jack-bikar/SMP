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
import org.mineacademy.fo.Common;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.CompSound;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CompassListener implements Listener {

	@Getter
	private static final Listener instance = new CompassListener();

	@EventHandler
	public void onPlayerInteract(final PlayerInteractEvent event) {
		final Action action = event.getAction();
		final Player player = event.getPlayer();

		if (Settings.CompassSection.ENABLE_COMPASS) {
			if (player.getItemInHand().getType() == Material.COMPASS) {
				if (action.equals(Action.RIGHT_CLICK_AIR) || action.equals(Action.RIGHT_CLICK_BLOCK) || action.equals(Action.PHYSICAL)) {
					CompSound.LAVA_POP.play(player.getLocation());

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
						else if (cache.getTrackingLocation().equals("Bed") && player.getBedSpawnLocation() != null)
							Common.runLater(() -> player.setCompassTarget(player.getBedSpawnLocation()));

						if (cache.getPortalLocation() != null) {
							if (player.getInventory().contains(CompMaterial.COMPASS.getMaterial()))
								for (int i = 0; i <= player.getInventory().getSize(); i++) {
									final ItemStack item = player.getInventory().getItem(i);

									if (item != null && item.getType() == CompMaterial.COMPASS.getMaterial()) {
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
						if (player.getInventory().contains(CompMaterial.COMPASS.getMaterial()) && cache.getPortalLocation() != null) {
							for (int i = 0; i <= player.getInventory().getSize(); i++) {
								final ItemStack item = player.getInventory().getItem(i);

								if (item != null && item.getType() == CompMaterial.COMPASS.getMaterial()) {
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