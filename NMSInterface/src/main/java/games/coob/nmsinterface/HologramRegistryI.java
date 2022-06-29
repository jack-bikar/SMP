package games.coob.nmsinterface;

import java.util.List;
import java.util.UUID;

public interface HologramRegistryI {

	void unregister(final NMSHologramI hologram);

	void register(final NMSHologramI hologram);

	boolean isRegistered(final NMSHologramI hologram);

	boolean isRegistered(final UUID entityUniqueId);

	List<NMSHologramI> loadHolograms();

	List<NMSHologramI> getLoadedHolograms();

	void spawnFromDisk();
}
