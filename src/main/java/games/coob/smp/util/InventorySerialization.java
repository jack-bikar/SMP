package games.coob.smp.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * Converts item arrays to and from Base64 strings for storage in YAML.
 */
public final class InventorySerialization {

	private InventorySerialization() {
	}

	public static String toBase64(final ItemStack[] items) {
		return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
	}

	public static ItemStack[] fromBase64(final String data) {
		return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(data));
	}

	/**
	 * Reads the format written by older versions of this plugin (Bukkit object
	 * streams). Only used to migrate old data.
	 */
	@SuppressWarnings("deprecation")
	public static ItemStack[] fromLegacyBase64(final String data) throws Exception {
		try (BukkitObjectInputStream input = new BukkitObjectInputStream(
				new ByteArrayInputStream(Base64Coder.decodeLines(data)))) {
			final ItemStack[] items = new ItemStack[input.readInt()];
			for (int i = 0; i < items.length; i++)
				items[i] = (ItemStack) input.readObject();
			return items;
		}
	}
}
