package games.coob.smp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

/**
 * Utility class for color and text formatting
 */
public final class ColorUtil {

	// Use legacyAmpersand() to handle & color codes (not § codes)
	private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

	private ColorUtil() {
	}

	/**
	 * Translates color codes in a string (legacy &amp; format)
	 * Converts &amp; codes to § codes for string output
	 *
	 * @param text The text to colorize
	 * @return Colorized text string
	 */
	public static String colorize(String text) {
		if (text == null) return null;
		// Convert & codes to § codes for string output
		return text.replace('&', '§');
	}

	/**
	 * Converts a legacy color string to a Component using Adventure API
	 * Supports &amp; color codes (e.g., &amp;a for green, &amp;c for red)
	 *
	 * @param text Legacy color string with &amp; codes
	 * @return Component ready for Adventure API
	 */
	public static Component toComponent(String text) {
		if (text == null) return Component.empty();
		// Use legacyAmpersand() to handle & codes (not legacySection() which handles § codes)
		return LEGACY_SERIALIZER.deserialize(text);
	}

	/**
	 * Sends a colored message to a player using Adventure API
	 * Supports &amp; color codes (e.g., &amp;a, &amp;c, &amp;e, &amp;r, etc.)
	 *
	 * @param player The player
	 * @param message The message (supports &amp; color codes)
	 */
	public static void sendMessage(org.bukkit.entity.Player player, String message) {
		if (message == null) return;
		player.sendMessage(toComponent(message));
	}

	/**
	 * Sends a colored message to a CommandSender using Adventure API
	 * Supports &amp; color codes for both players and console
	 *
	 * @param sender The command sender (player or console)
	 * @param message The message (supports &amp; color codes)
	 */
	public static void sendMessage(CommandSender sender, String message) {
		if (message == null) return;
		sender.sendMessage(toComponent(message));
	}
}
