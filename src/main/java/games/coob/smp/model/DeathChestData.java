package games.coob.smp.model;

import games.coob.smp.config.Serializable;
import games.coob.smp.config.SerializedMap;
import games.coob.smp.util.InventorySerialization;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Setter
@Getter
public class DeathChestData implements Serializable {

	private Location location;

	private UUID uuid;

	private Inventory inventory;

	/** Holograms are non-persistent; not serialized. */
	@Override
	public Map<String, Object> serialize() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("Location", this.location);
		map.put("UUID", this.uuid != null ? this.uuid.toString() : null);
		map.put("Inventory", InventorySerialization.toBase64(this.inventory));
		return map;
	}

	public static DeathChestData deserialize(final SerializedMap map) throws IOException {
		final String inventoryString = map.getString("Inventory");
		final Inventory inventory = InventorySerialization.fromBase64(inventoryString);
		final Location location = map.getLocation("Location");
		final UUID uuid = map.getUUID("UUID");
		final DeathChestData deathChestData = new DeathChestData();

		deathChestData.setLocation(location);
		deathChestData.setUuid(uuid);
		deathChestData.setInventory(inventory);

		return deathChestData;
	}

	/** Owner name for hologram text; holograms are created on demand and not persisted. */
	public String getOwnerName() {
		if (uuid == null) return "Unknown";
		String name = Bukkit.getOfflinePlayer(uuid).getName();
		return name != null ? name : "Unknown";
	}
}
