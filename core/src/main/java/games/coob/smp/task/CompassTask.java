package games.coob.smp.task;

import games.coob.smp.PlayerCache;
import games.coob.smp.settings.Settings;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.Remain;

public class CompassTask extends BukkitRunnable {
	@Override
	public void run() {
		for (final Player player : Remain.getOnlinePlayers()) {
			final PlayerCache cache = PlayerCache.from(player);

			if (cache.getTrackingLocation() != null) {
				final Player target = Remain.getPlayerByUUID(cache.getTargetByUUID());

				if (target != null) {
					if (cache.getTrackingLocation().equals("Player") && player.getInventory().contains(CompMaterial.COMPASS.getMaterial())) {
						final Location location = target.getLocation();

						if (player.getWorld().getEnvironment() == World.Environment.NORMAL) {
							if (Settings.CompassSection.ALLOWED_ENVIRONEMENTS.equals("normal") || Settings.CompassSection.ALLOWED_ENVIRONEMENTS.equals("all"))
								player.setCompassTarget(location);
						} else {
							if (player.getWorld() == target.getWorld()) {
								for (int i = 0; i <= player.getInventory().getSize(); i++) {
									final ItemStack item = player.getInventory().getItem(i);

									if (item != null && item.getType() == CompMaterial.COMPASS.getMaterial()) {
										final CompassMeta compass = (CompassMeta) item.getItemMeta();

										if (compass.hasLodestone()) {
											compass.setLodestone(location);
											compass.setLodestoneTracked(false); // we do not want a real lodestone to be present at that location.
											item.setItemMeta(compass);
										}
									}
								}
							} else {
								if (Settings.CompassSection.ALLOWED_ENVIRONEMENTS.equals("nether") || Settings.CompassSection.ALLOWED_ENVIRONEMENTS.equals("all")) {
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
						}
					}
				}
			}
		}
	}
}
