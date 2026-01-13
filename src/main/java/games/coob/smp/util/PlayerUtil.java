package games.coob.smp.util;

import org.bukkit.entity.Player;

/**
 * Utility class for player operations
 */
public final class PlayerUtil {

	/**
	 * Standard usable player inventory size (excluding armor slots)
	 */
	public static final int USABLE_PLAYER_INV_SIZE = 36;

	private PlayerUtil() {
	}

	/**
	 * Get all online players
	 *
	 * @return Array of online players
	 */
	public static Player[] getOnlinePlayers() {
		return org.bukkit.Bukkit.getOnlinePlayers().toArray(new Player[0]);
	}
}
