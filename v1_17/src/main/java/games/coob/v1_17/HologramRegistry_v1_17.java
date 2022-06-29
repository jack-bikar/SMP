package games.coob.v1_17;

import games.coob.nmsinterface.HologramRegistryI;
import games.coob.nmsinterface.NMSHologramI;
import lombok.Getter;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.constants.FoConstants;
import org.mineacademy.fo.settings.YamlConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Represents an elegant way to permanently store and load Holograms
 */
public final class HologramRegistry_v1_17 extends YamlConfig implements HologramRegistryI {

	/**
	 * The singleton of this class
	 */
	@Getter
	private static final HologramRegistry_v1_17 instance = new HologramRegistry_v1_17();

	/**
	 * Represents currently loaded Holograms
	 */
	private List<NMSHologramI> loadedHolograms = new ArrayList<>();

	/**
	 * Create a new registry and load
	 */
	private HologramRegistry_v1_17() {
		this.loadConfiguration(NO_DEFAULT, FoConstants.File.DATA);
	}


	@Override
	public List<NMSHologramI> loadHolograms() {
		final List<NMSHologramI> loadedHologram = new ArrayList<>();

		for (final SerializedMap map : getMapList("Saved_Holograms")) {
			final NMSHologramI hologram = NMSHologram_v1_17.deserialize(map);

			loadedHologram.add(hologram);
		}

		return loadedHologram;
	}

	/**
	 * Registers a new hologram to our map
	 *
	 * @param hologram
	 */
	@Override
	public void register(final NMSHologramI hologram) {
		Valid.checkBoolean(!this.isRegistered(hologram), hologram + " is already registered!");

		this.loadedHolograms.add(hologram);
		this.save();
	}

	@Override
	public void unregister(final NMSHologramI hologram) {
		this.loadedHolograms.remove(hologram);
		this.save();
	}

	/**
	 * Return true if the given hologram is already registered
	 *
	 * @param hologram
	 * @return
	 */
	@Override
	public boolean isRegistered(final NMSHologramI hologram) {
		return this.isRegistered(hologram.getUniqueId());
	}

	/**
	 * Return true if the given hologram is already registered
	 *
	 * @param entityUniqueId
	 * @return
	 */
	@Override
	public boolean isRegistered(final UUID entityUniqueId) {
		for (final NMSHologramI hologram : this.loadedHolograms)
			if (hologram != null && hologram.getUniqueId().equals(entityUniqueId))
				return true;

		return false;
	}

	/**
	 * Get the loaded holograms
	 */
	@Override
	public List<NMSHologramI> getLoadedHolograms() {
		return Collections.unmodifiableList(loadedHolograms);
	}

	@Override
	public void spawnFromDisk() {
		if (!MinecraftVersion.atLeast(MinecraftVersion.V.v1_16))
			return;

		// Tricky: This automatically calls the spawn method which puts the hologram to our loadedHolograms list

		// TODO save
		this.loadedHolograms = loadHolograms();

		System.out.println("@Found " + this.loadedHolograms.size() + " Holograms on the disk");

		for (final NMSHologramI hologram : this.loadedHolograms)
			System.out.println("\tspawned " + hologram + " at " + hologram.getLocation());
	}

	@Override
	protected void onSave() {
		this.set("Saved_Holograms", this.getLoadedHolograms());
	}
}
