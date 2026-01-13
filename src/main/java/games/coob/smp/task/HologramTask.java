package games.coob.smp.task;

import games.coob.smp.hologram.Hologram;
import games.coob.smp.hologram.HologramRegistryInterface;
import games.coob.smp.hologram.HologramRegistryProvider;
import games.coob.smp.settings.Settings;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.mineacademy.fo.remain.Remain;

/**
 * Represents a self-repeating task managing hologram.
 */
@RequiredArgsConstructor
public final class HologramTask extends BukkitRunnable {

	@Override
	public void run() {
		final HologramRegistryInterface registry = HologramRegistryProvider.getHologramRegistryInstance();

		for (final Player player : Remain.getOnlinePlayers()) {
			for (final Hologram hologram : registry.getLoadedHolograms()) {
				if (!player.hasMetadata(hologram.getUniqueId().toString()) && registry.isRegistered(hologram))
					showPlayersInRange(hologram, player);

				if (!player.getWorld().equals(hologram.getLocation().getWorld()) || player.getLocation().distance(hologram.getLocation()) > Settings.DeathStorageSection.HOLOGRAM_VISIBLE_RANGE)
					hologram.hide(player);
			}
		}
	}

	/*
	 * Shows the hologram to players within the set range
	 */
	private void showPlayersInRange(final Hologram hologram, final Player player) {
		final Location hologramLocation = hologram.getLocation();
		final Location playerLocation = player.getLocation();

		if (player.getWorld().equals(hologramLocation.getWorld()) && playerLocation.distance(hologramLocation) <= Settings.DeathStorageSection.HOLOGRAM_VISIBLE_RANGE) {
			hologram.show(hologramLocation, player, hologram.getLines());
		}
	}
}