package games.coob.smp.model;

import games.coob.smp.PlayerCache;
import games.coob.smp.config.ConfigFile;
import games.coob.smp.config.SerializedMap;
import games.coob.smp.hologram.BukkitHologram;
import games.coob.smp.util.ValidationUtil;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DeathChestRegistry extends ConfigFile {

	@Getter
    private static final DeathChestRegistry instance = new DeathChestRegistry();

    private Set<DeathChestData> registeredDeathChests = new HashSet<>();

	private DeathChestRegistry() {
		super("data.yml");
	}

	@Override
	protected void onLoad() {
		this.registeredDeathChests = new HashSet<>();
		ConfigurationSection section = getConfig().getConfigurationSection("Death_Chests");
		if (section != null) {
			for (String key : section.getKeys(false)) {
				try {
					Map<String, Object> mapData = section.getConfigurationSection(key).getValues(true);
					SerializedMap map = SerializedMap.of(mapData);
					DeathChestData data = DeathChestData.deserialize(map);
					this.registeredDeathChests.add(data);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	protected void onSave() {
		List<Map<String, Object>> serialized = new ArrayList<>();
		for (DeathChestData data : registeredDeathChests) {
			serialized.add(data.serialize());
		}
		getConfig().set("Death_Chests", serialized);
	}

	public void register(final Block block, final Player player, final BukkitHologram hologram) {
		final DeathChestData deathChestData = new DeathChestData();
		final PlayerCache cache = PlayerCache.from(player);

		ValidationUtil.checkBoolean(!this.registeredDeathChests.contains(deathChestData), block + " has already been registered");

		deathChestData.setInventory(cache.getDeathChestInventory());
		deathChestData.setLocation(block.getLocation());
		deathChestData.setUuid(player.getUniqueId());
		deathChestData.setHologram(hologram);

		this.registeredDeathChests.add(deathChestData);
		this.save();
	}

	public void unregister(final Block block) {
		// synchronized (registeredBlocks) { // synchronized is used for anyscronous processing (Common.runLaterAsync)
		this.registeredDeathChests.removeIf(deathChestData -> deathChestData.getLocation().getBlock().equals(block));

		this.save();
	}

	public boolean isRegistered(final Block block) {
		for (final DeathChestData deathChestData : this.registeredDeathChests) {
			if (deathChestData.getLocation().equals(block.getLocation()))
				return true;
		}

		return false;
	}

	public List<Location> getLocations() {
		final List<Location> locations = new ArrayList<>();

		for (final DeathChestData deathChestData : this.registeredDeathChests)
			locations.add(deathChestData.getLocation());

		return locations;
	}

	public Inventory getInventory(final Block block) {
		for (final DeathChestData deathChestData : this.registeredDeathChests)
			if (deathChestData.getLocation().equals(block.getLocation()))
				return deathChestData.getInventory();

		return null;
	}

	public BukkitHologram getHologram(final Block block) {
		for (final DeathChestData deathChestData : this.registeredDeathChests)
			if (deathChestData.getLocation().equals(block.getLocation()))
				return deathChestData.getHologram();

		return null;
	}

	public Block getBlock(final Location location) {
		for (final DeathChestData deathChestData : this.registeredDeathChests) {
			if (deathChestData.getLocation().equals(location))
				return deathChestData.getLocation().getBlock();
		}

		return null;
	}
}
