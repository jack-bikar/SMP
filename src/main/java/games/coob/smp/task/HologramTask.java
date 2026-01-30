package games.coob.smp.task;

import games.coob.smp.hologram.BukkitHologram;
import games.coob.smp.model.DeathChestRegistry;
import games.coob.smp.settings.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Manages non-persistent death chest holograms: they appear only when a player
 * is within {@link Settings.DeathStorageSection#HOLOGRAM_VISIBLE_RANGE}.
 */
public final class HologramTask extends BukkitRunnable {

	@Override
	public void run() {
		final DeathChestRegistry registry = DeathChestRegistry.getInstance();
		final int range = Settings.DeathStorageSection.HOLOGRAM_VISIBLE_RANGE;
		final double rangeSq = (double) range * range;

		for (final Player player : Bukkit.getOnlinePlayers()) {
			for (final Location chestLoc : registry.getLocations()) {
				if (!player.getWorld().equals(chestLoc.getWorld())) continue;

				Block block = chestLoc.getBlock();
				BukkitHologram hologram = registry.getHologram(block);
				if (hologram == null) continue;

				double distSq = player.getLocation().distanceSquared(hologram.getLocation());
				boolean inRange = distSq <= rangeSq;

				if (inRange) {
					if (!player.hasMetadata(hologram.getUniqueId().toString())) {
						hologram.show(hologram.getLocation(), player, hologram.getLines());
					}
				} else {
					hologram.hide(player);
				}
			}
		}
	}
}