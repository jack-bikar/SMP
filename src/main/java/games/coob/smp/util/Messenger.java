package games.coob.smp.util;

import games.coob.smp.util.ColorUtil;
import org.bukkit.entity.Player;

/**
 * Utility class for sending formatted messages to players
 */
public final class Messenger {

	private Messenger() {
	}

	/**
	 * Send a success message to a player
	 *
	 * @param player The player
	 * @param message The message
	 */
	public static void success(Player player, String message) {
		ColorUtil.sendMessage(player, "&a" + message);
	}

	/**
	 * Send an info message to a player
	 *
	 * @param player The player
	 * @param message The message
	 */
	public static void info(Player player, String message) {
		ColorUtil.sendMessage(player, "&e" + message);
	}

	/**
	 * Send an error message to a player
	 *
	 * @param player The player
	 * @param message The message
	 */
	public static void error(Player player, String message) {
		ColorUtil.sendMessage(player, "&c" + message);
	}

	/**
	 * Send a message to a player
	 *
	 * @param player The player
	 * @param message The message
	 */
	public static void send(Player player, String message) {
		ColorUtil.sendMessage(player, message);
	}
}
