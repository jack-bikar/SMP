package games.coob.smp.hologram;

import games.coob.smp.SMPPlugin;
import games.coob.smp.config.ConfigFile;
import games.coob.smp.config.SerializedMap;
import games.coob.smp.util.ValidationUtil;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registry for managing holograms using Paper 1.21+ API
 */
public class HologramRegistry extends ConfigFile {

	private static final HologramRegistry instance = new HologramRegistry();

	public static HologramRegistry getInstance() {
		return instance;
	}

	/**
	 * Represents currently loaded Holograms
	 */
	private List<BukkitHologram> loadedHolograms = new ArrayList<>();

	/**
	 * Create a new registry and load
	 */
	private HologramRegistry() {
		super("data.yml");
	}


	/**
	 * Automatically loads stored disk Holograms and spawns them
	 */
	public void spawnFromDisk() {
		// Load holograms from disk
		this.loadedHolograms = loadHolograms();

		SMPPlugin.getInstance().getLogger().info("Found " + this.loadedHolograms.size() + " Holograms on the disk");

		for (final BukkitHologram hologram : this.loadedHolograms)
			SMPPlugin.getInstance().getLogger().info("Spawned " + hologram + " at " + hologram.getLocation());
	}

	public List<BukkitHologram> loadHolograms() {
		final List<BukkitHologram> loadedHologram = new ArrayList<>();

		ConfigurationSection section = getConfig().getConfigurationSection("Saved_Holograms");
		if (section != null) {
			for (String key : section.getKeys(false)) {
				Map<String, Object> mapData = section.getConfigurationSection(key).getValues(true);
				SerializedMap map = SerializedMap.of(mapData);
				final BukkitHologram hologram = BukkitHologram.deserialize(map);
				loadedHologram.add(hologram);
			}
		}

		return loadedHologram;
	}

	/**
	 * Registers a new hologram to our map
	 *
	 * @param hologram The hologram to register
	 */
	public void register(final BukkitHologram hologram) {
		ValidationUtil.checkBoolean(!this.isRegistered(hologram), hologram + " is already registered!");

		this.loadedHolograms.add(hologram);
		this.save();
	}

	public void unregister(final BukkitHologram hologram) {
		this.loadedHolograms.remove(hologram);
		this.save();
	}

	public boolean isRegistered(final BukkitHologram hologram) {
		return this.isRegistered(hologram.getUniqueId());
	}

	public boolean isRegistered(final UUID entityUniqueId) {
		for (final BukkitHologram hologram : this.loadedHolograms)
			if (hologram != null && hologram.getUniqueId().equals(entityUniqueId))
				return true;

		return false;
	}

	public List<BukkitHologram> getLoadedHolograms() {
		return Collections.unmodifiableList(loadedHolograms);
	}

	@Override
	protected void onSave() {
		List<Map<String, Object>> serialized = new ArrayList<>();
		for (BukkitHologram hologram : loadedHolograms) {
			serialized.add(hologram.serialize());
		}
		getConfig().set("Saved_Holograms", serialized);
	}
}
