package games.coob.smp.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Utility for serializing inventories to/from Base64
 * Uses modern serialization approach compatible with Paper 1.21+
 */
public class InventorySerialization {

	/**
	 * A method to serialize an inventory to a Base64 string.
	 *
	 * @param inventory The inventory to serialize
	 * @return Base64 encoded string
	 */
	public static String toBase64(final Inventory inventory) {
		try {
			final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			// Note: BukkitObjectOutputStream is deprecated but still the standard way for ItemStack serialization
			@SuppressWarnings("deprecation")
			final BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

			// Write the size of the inventory
			dataOutput.writeInt(inventory.getSize());

			// Save every element in the list
			for (int i = 0; i < inventory.getSize(); i++) {
				dataOutput.writeObject(inventory.getItem(i));
			}

			// Serialize that array
			dataOutput.close();
			return Base64Coder.encodeLines(outputStream.toByteArray());
		} catch (final Exception e) {
			throw new IllegalStateException("Unable to save item stacks.", e);
		}
	}

	/**
	 * A method to get an {@link Inventory} from an encoded, Base64, string.
	 *
	 * @param data The Base64 encoded string
	 * @return The deserialized inventory
	 * @throws IOException If deserialization fails
	 */
	public static Inventory fromBase64(final String data) throws IOException {
		try {
			final ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
			// Note: BukkitObjectInputStream is deprecated but still the standard way for ItemStack deserialization
			@SuppressWarnings("deprecation")
			final BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
			final Inventory inventory = Bukkit.getServer().createInventory(null, dataInput.readInt());

			// Read the serialized inventory
			for (int i = 0; i < inventory.getSize(); i++) {
				inventory.setItem(i, (ItemStack) dataInput.readObject());
			}

			dataInput.close();
			return inventory;
		} catch (final ClassNotFoundException e) {
			throw new IOException("Unable to decode class type.", e);
		}
	}
}