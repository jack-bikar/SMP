package games.coob.smp.hologram;

import java.util.List;
import java.util.UUID;

/**
 * Interface for hologram registry using Paper 1.21+ API
 */
public interface HologramRegistryInterface {

	/**
	 * Register a hologram
	 *
	 * @param hologram The hologram to register
	 */
	void register(Hologram hologram);

	/**
	 * Unregister a hologram
	 *
	 * @param hologram The hologram to unregister
	 */
	void unregister(Hologram hologram);

	/**
	 * Check if a hologram is registered
	 *
	 * @param hologram The hologram to check
	 * @return True if registered
	 */
	boolean isRegistered(Hologram hologram);

	/**
	 * Check if a hologram is registered by UUID
	 *
	 * @param entityUniqueId The UUID to check
	 * @return True if registered
	 */
	boolean isRegistered(UUID entityUniqueId);

	/**
	 * Load holograms from disk
	 *
	 * @return List of loaded holograms
	 */
	List<Hologram> loadHolograms();

	/**
	 * Get all loaded holograms
	 *
	 * @return List of loaded holograms
	 */
	List<Hologram> getLoadedHolograms();

	/**
	 * Spawn holograms from disk
	 */
	void spawnFromDisk();
}
