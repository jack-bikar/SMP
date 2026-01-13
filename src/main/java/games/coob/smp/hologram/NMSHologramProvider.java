package games.coob.smp.hologram;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.mineacademy.fo.collection.SerializedMap;

/**
 * Provider for creating and deserializing holograms using Paper 1.21+ API
 */
public final class HologramProvider {

	public static Hologram deserialize(final SerializedMap map) {
		return BukkitHologram.deserialize(map);
	}

	public static void sendTo(final Location location, final Player player, final String... linesOfText) {
		final Hologram hologram = new BukkitHologram();
		hologram.show(location, player, linesOfText);
	}

	public static Hologram createHologram() {
		return new BukkitHologram();
	}
}
