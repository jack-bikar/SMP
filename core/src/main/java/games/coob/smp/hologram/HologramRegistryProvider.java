package games.coob.smp.hologram;

import games.coob.nmsinterface.HologramRegistryI;
import games.coob.v1_17.HologramRegistry_v1_17;
import games.coob.v1_18.HologramRegistry_v1_18;

import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.exception.FoException;

public final class HologramRegistryProvider {

	public static HologramRegistryI getHologramRegistryInstance() {
		if (MinecraftVersion.equals(MinecraftVersion.V.v1_18))
			return HologramRegistry_v1_18.getInstance();
		else if (MinecraftVersion.equals(MinecraftVersion.V.v1_17))
			return HologramRegistry_v1_17.getInstance();
		else if (MinecraftVersion.olderThan(MinecraftVersion.V.v1_17))
			return HologramRegistry.getInstance();

		throw new FoException("Unsupported Minecraft version " + MinecraftVersion.getServerVersion());
	}
}
