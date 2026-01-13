package games.coob.smp.hologram;

import lombok.Getter;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.constants.FoConstants;
import org.mineacademy.fo.settings.YamlConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Registry for managing holograms using Paper 1.21+ API
 */
public class HologramRegistry extends YamlConfig {

	@Getter
	private static final HologramRegistry instance = new HologramRegistry();

	/**
	 * Represents currently loaded Holograms
	 */
	private List<BukkitHologram> loadedHolograms = new ArrayList<>();

	/**
	 * Create a new registry and load
	 */
	private HologramRegistry() {
		this.loadConfiguration(NO_DEFAULT, FoConstants.File.DATA);
	}


	/**
	 * Automatically loads stored disk Holograms and spawns them
	 */
	public void spawnFromDisk() {
		// Load holograms from disk
		this.loadedHolograms = loadHolograms();

		System.out.println("@Found " + this.loadedHolograms.size() + " Holograms on the disk");

		for (final BukkitHologram hologram : this.loadedHolograms)
			System.out.println("\tspawned " + hologram + " at " + hologram.getLocation());
	}

	public List<BukkitHologram> loadHolograms() {
		final List<BukkitHologram> loadedHologram = new ArrayList<>();

		for (final SerializedMap map : getMapList("Saved_Holograms")) {
			final BukkitHologram hologram = BukkitHologram.deserialize(map);
			loadedHologram.add(hologram);
		}

		return loadedHologram;
	}

	/**
	 * Registers a new hologram to our map
	 *
	 * @param hologram The hologram to register
	 */
	public void register(final BukkitHologram hologram) {
		Valid.checkBoolean(!this.isRegistered(hologram), hologram + " is already registered!");

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
		this.set("Saved_Holograms", this.getLoadedHolograms());
	}
}
