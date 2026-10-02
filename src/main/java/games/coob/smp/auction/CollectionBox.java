package games.coob.smp.auction;

import games.coob.smp.SMPPlugin;
import games.coob.smp.config.ConfigFile;
import games.coob.smp.util.InventorySerialization;
import games.coob.smp.util.ItemSelection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Items owed to a player (what they won at the auction house, offers they
 * took, things that came back), in auction-boxes/&lt;uuid&gt;.yml until they take
 * them. Every change is written before the method returns; a box whose write
 * failed stays in memory and is written again later, so it is never dropped.
 */
public final class CollectionBox extends ConfigFile {

	private static final Map<UUID, CollectionBox> loaded = new HashMap<>();

	/** Set in onLoad, which runs (from the super constructor) before field initialisers would. */
	private List<ItemStack> items;
	/** The last write failed: what is in memory isn't on disk yet. */
	private boolean unsaved;

	private CollectionBox(UUID playerId) {
		super("auction-boxes/" + playerId + ".yml");
	}

	public static CollectionBox of(UUID playerId) {
		return loaded.computeIfAbsent(playerId, CollectionBox::new);
	}

	/** Forgets a loaded box, unless it still can't be written. */
	public static void unload(UUID playerId) {
		CollectionBox box = loaded.get(playerId);
		if (box != null && (!box.unsaved || box.persist()))
			loaded.remove(playerId);
	}

	/** Plugin disable: one more try at writing boxes whose earlier write failed. */
	public static void saveUnsaved() {
		for (CollectionBox box : loaded.values()) {
			if (box.unsaved)
				box.persist();
		}
	}

	@Override
	protected void onLoad() {
		items = new ArrayList<>();
		String data = getConfig().getString("Items");
		if (data == null)
			return;
		try {
			for (ItemStack item : InventorySerialization.fromBase64(data)) {
				if (item != null && !item.isEmpty())
					items.add(item);
			}
		} catch (RuntimeException e) {
			// Moved aside, so saving the (now empty) box can't overwrite the items
			File backup = new File(file.getParentFile(), file.getName() + ".broken-" + System.currentTimeMillis());
			file.renameTo(backup);
			SMPPlugin.getInstance().getLogger().log(Level.SEVERE, "Could not read the auction collection box "
					+ file.getName() + ", it was moved to " + backup.getName(), e);
		}
	}

	@Override
	protected void onSave() {
		getConfig().set("Items", items.isEmpty() ? null : InventorySerialization.toBase64(items.toArray(new ItemStack[0])));
	}

	/** Copies, so a menu showing them can't change what is stored. */
	public List<ItemStack> getItems() {
		return AuctionListing.copy(items);
	}

	public int size() {
		return items.size();
	}

	public boolean isEmpty() {
		return items.isEmpty();
	}

	/**
	 * Adds items and writes the box.
	 *
	 * @return whether they are on disk (if not, they are kept in memory and written again later)
	 */
	public boolean add(List<ItemStack> added) {
		int before = items.size();
		for (ItemStack item : added) {
			if (item != null && !item.isEmpty())
				items.add(item.clone());
		}
		// Nothing new: no need to write (or to create a file for someone who never had a box)
		if (items.size() == before && !unsaved)
			return true;
		return persist();
	}

	/**
	 * Moves the stack at {@code index} into the player's inventory, if it fits.
	 *
	 * @return whether it was taken
	 */
	public boolean take(Player player, int index) {
		if (index < 0 || index >= items.size() || !ItemSelection.fits(player.getInventory(), null, List.of(items.get(index))))
			return false;
		ItemStack item = items.remove(index);
		items.addAll(player.getInventory().addItem(item.clone()).values());
		written(player);
		return true;
	}

	/**
	 * Moves every stack that fits into the player's inventory.
	 *
	 * @return how many stacks are left in the box
	 */
	public int takeAll(Player player) {
		List<ItemStack> kept = new ArrayList<>();
		boolean took = false;
		for (ItemStack item : items) {
			if (ItemSelection.fits(player.getInventory(), null, List.of(item))) {
				kept.addAll(player.getInventory().addItem(item.clone()).values());
				took = true;
			} else {
				kept.add(item);
			}
		}
		if (took) {
			items = kept;
			written(player);
		}
		return items.size();
	}

	/**
	 * Moves the last {@code count} stacks (the ones just added) into the
	 * player's inventory; whatever doesn't fit stays.
	 *
	 * @return how many of them are left in the box
	 */
	public int takeLast(Player player, int count) {
		int start = Math.max(0, items.size() - count);
		List<ItemStack> leftovers = new ArrayList<>();
		for (ItemStack item : items.subList(start, items.size()))
			leftovers.addAll(player.getInventory().addItem(item.clone()).values());
		if (count > 0) {
			items.subList(start, items.size()).clear();
			items.addAll(leftovers);
			written(player);
		}
		return leftovers.size();
	}

	/** The player's file first: a crash between the two writes leaves an item in both, never in neither. */
	private void written(Player player) {
		player.saveData();
		persist();
	}

	private boolean persist() {
		unsaved = !saveNow();
		return !unsaved;
	}
}
