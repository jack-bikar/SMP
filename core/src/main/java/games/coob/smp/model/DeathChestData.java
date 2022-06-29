package games.coob.smp.model;

import java.io.IOException;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.ConfigSerializable;

import games.coob.nmsinterface.NMSHologramI;
import games.coob.smp.hologram.NMSHologramProvider;
import games.coob.smp.util.InventorySerialization;
import lombok.Getter;

@Getter
public class DeathChestData implements ConfigSerializable {

	private Location location;

	private UUID uuid;

	private Inventory inventory;

	private NMSHologramI hologram;

	public void setLocation(final Location location) {
		this.location = location;
	}

	public void setInventory(final Inventory inventory) {
		this.inventory = inventory;
	}

	public void setUuid(final UUID uuid) {
		this.uuid = uuid;
	}

	public void setHologram(final NMSHologramI hologram) {
		this.hologram = hologram;
	}

	@Override
	public SerializedMap serialize() {
		return SerializedMap.ofArray(
				"Location", this.location,
				"UUID", this.uuid,
				"Inventory", InventorySerialization.toBase64(this.inventory),
				"Hologram", this.hologram);
	}

	public static DeathChestData deserialize(final SerializedMap map) throws IOException {
		final String inventoryString = map.getString("Inventory");
		final Inventory inventory = InventorySerialization.fromBase64(inventoryString);
		final Location location = map.getLocation("Location");
		final UUID uuid = map.getUUID("UUID");
		final NMSHologramI hologram = NMSHologramProvider.deserialize(map.getMap("Hologram"));
		final DeathChestData deathChestData = new DeathChestData();

		deathChestData.setLocation(location);
		deathChestData.setUuid(uuid);
		deathChestData.setInventory(inventory);
		deathChestData.setHologram(hologram);

		return deathChestData;
	}
}
