package games.coob.smp.hologram;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Utility class for creating holograms
 */
public final class HologramProvider {

	public static void sendTo(final Location location, final Player player, final String... linesOfText) {
		final BukkitHologram hologram = new BukkitHologram();
		hologram.show(location, player, linesOfText);
	}

	public static BukkitHologram createHologram() {
		return new BukkitHologram();
	}
}
