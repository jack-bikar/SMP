package games.coob.smp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Utility class for color and text formatting
 */
public final class ColorUtil {

	private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
	private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

	private ColorUtil() {
	}

	/**
	 * Translates color codes in a string (legacy &amp; format)
	 *
	 * @param text The text to colorize
	 * @return Colorized text
	 */
	public static String colorize(String text) {
		if (text == null) return null;
		// Use Adventure API to convert legacy codes, then back to string if needed
		Component component = LEGACY_SERIALIZER.deserialize(text);
		return LEGACY_SERIALIZER.serialize(component);
	}

	/**
	 * Converts a legacy color string to a Component
	 *
	 * @param text Legacy color string
	 * @return Component
	 */
	public static Component toComponent(String text) {
		if (text == null) return Component.empty();
		return LEGACY_SERIALIZER.deserialize(text);
	}

	/**
	 * Sends a colored message to a player using Adventure API
	 *
	 * @param player The player
	 * @param message The message (supports &amp; color codes)
	 */
	public static void sendMessage(org.bukkit.entity.Player player, String message) {
		player.sendMessage(toComponent(colorize(message)));
	}
}
