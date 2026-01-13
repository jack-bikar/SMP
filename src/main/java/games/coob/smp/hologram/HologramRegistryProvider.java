package games.coob.smp.hologram;

/**
 * Provider for getting the hologram registry instance
 */
public final class HologramRegistryProvider {

	public static HologramRegistryInterface getHologramRegistryInstance() {
		return HologramRegistry.getInstance();
	}
}
