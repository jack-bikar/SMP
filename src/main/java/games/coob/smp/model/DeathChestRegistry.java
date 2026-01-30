package games.coob.smp.model;

import games.coob.smp.PlayerCache;
import games.coob.smp.config.ConfigFile;
import games.coob.smp.config.SerializedMap;
import games.coob.smp.hologram.BukkitHologram;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ValidationUtil;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DeathChestRegistry extends ConfigFile {

	@Getter
    private static final DeathChestRegistry instance = new DeathChestRegistry();

    private Set<DeathChestData> registeredDeathChests = new HashSet<>();
	/** Non-persistent holograms, created when players are in range. */
	private final Map<Location, BukkitHologram> hologramCache = new HashMap<>();

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

	/**
	 * Register a death chest. Optionally cache an existing hologram (e.g. created on death)
	 * so it is reused; holograms are non-persistent and only shown within radius.
	 */
	public void register(final Block block, final Player player, final BukkitHologram initialHologram) {
		final DeathChestData deathChestData = new DeathChestData();
		final PlayerCache cache = PlayerCache.from(player);

		ValidationUtil.checkBoolean(!this.isRegistered(block), block + " has already been registered");

		deathChestData.setInventory(cache.getDeathChestInventory());
		deathChestData.setLocation(block.getLocation());
		deathChestData.setUuid(player.getUniqueId());

		this.registeredDeathChests.add(deathChestData);
		if (initialHologram != null) {
			this.hologramCache.put(block.getLocation(), initialHologram);
		}
		this.save();
	}

	public void unregister(final Block block) {
		Location loc = block.getLocation();
		BukkitHologram cached = hologramCache.remove(loc);
		if (cached != null) {
			cached.removeAll();
		}
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

	public DeathChestData getDeathChestData(final Block block) {
		for (final DeathChestData data : this.registeredDeathChests)
			if (data.getLocation().getBlock().equals(block))
				return data;
		return null;
	}

	/**
	 * Get or create a non-persistent hologram for this death chest.
	 * Holograms are created on demand and only shown to players within
	 * {@link Settings.DeathStorageSection#HOLOGRAM_VISIBLE_RANGE}.
	 */
	public BukkitHologram getHologram(final Block block) {
		DeathChestData data = getDeathChestData(block);
		if (data == null) return null;

		Location loc = data.getLocation();
		BukkitHologram cached = hologramCache.get(loc);
		if (cached != null) return cached;

		Location holoLoc = block.getLocation().clone().add(0.5, 1.0, 0.5);
		String line = Settings.DeathStorageSection.HOLOGRAM_TEXT.replace("{player}", data.getOwnerName());
		BukkitHologram hologram = new BukkitHologram();
		hologram.ensureCreated(holoLoc, line);
		hologramCache.put(loc, hologram);
		return hologram;
	}

	/** All cached (non-persistent) holograms, for cleanup. */
	public Collection<BukkitHologram> getCachedHolograms() {
		return new ArrayList<>(hologramCache.values());
	}

	public Block getBlock(final Location location) {
		for (final DeathChestData deathChestData : this.registeredDeathChests) {
			if (deathChestData.getLocation().equals(location))
				return deathChestData.getLocation().getBlock();
		}

		return null;
	}
}
