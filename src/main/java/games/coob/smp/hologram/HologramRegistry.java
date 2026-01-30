package games.coob.smp.hologram;

import games.coob.smp.config.ConfigFile;
import games.coob.smp.util.ValidationUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
	 * Holograms are non-persistent; nothing is loaded from disk.
	 * Death chest holograms are created on demand in DeathChestRegistry when players are in range.
	 */
	public void spawnFromDisk() {
		// No-op: holograms are non-persistent and appear only within radius
	}

	/**
	 * Registers a hologram in memory only (not persisted).
	 */
	public void register(final BukkitHologram hologram) {
		ValidationUtil.checkBoolean(!this.isRegistered(hologram), hologram + " is already registered!");
		this.loadedHolograms.add(hologram);
	}

	/**
	 * Unregisters a hologram from memory (not persisted).
	 */
	public void unregister(final BukkitHologram hologram) {
		this.loadedHolograms.remove(hologram);
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
		// Holograms are non-persistent; do not save to disk
	}
}
