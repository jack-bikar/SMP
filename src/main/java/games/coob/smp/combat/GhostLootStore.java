package games.coob.smp.combat;

import games.coob.smp.config.ConfigFile;
import games.coob.smp.util.InventorySerialization;
import lombok.Getter;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * A copy of each ghost body's loot on disk (ghost-loot.yml). The body itself
 * isn't saved with the world, so if the server crashes while it is out, the
 * owner gets their items back on their next join instead of losing them.
 */
public final class GhostLootStore extends ConfigFile {

	@Getter
	private static final GhostLootStore instance = new GhostLootStore();

	private GhostLootStore() {
		super("ghost-loot.yml");
	}

	public void store(UUID playerId, ItemStack[] items) {
		getConfig().set(playerId.toString(), InventorySerialization.toBase64(items));
		save();
	}

	/** Removes and returns the stored loot, or null if there is none. */
	public ItemStack[] take(UUID playerId) {
		String data = getConfig().getString(playerId.toString());
		if (data == null)
			return null;
		clear(playerId);
		try {
			return InventorySerialization.fromBase64(data);
		} catch (RuntimeException e) {
			return null;
		}
	}

	public void clear(UUID playerId) {
		if (getConfig().contains(playerId.toString())) {
			getConfig().set(playerId.toString(), null);
			save();
		}
	}
}
