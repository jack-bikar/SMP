package games.coob.smp.util;

import games.coob.smp.duel.kit.DuelKit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Items a player picked from their own inventory to offer (in a trade, an
 * auction listing or an offer). They stay in the inventory until {@link #take}
 * removes them, so a crash or a closed menu can never lose them; {@link #prune}
 * catches items that were moved, used or dropped in the meantime.
 */
public final class ItemSelection {

	/** How many of the item in an inventory slot were picked. */
	private record Pick(int slot, ItemStack item, int amount) {
	}

	private final int maxStacks;
	private final List<Pick> picks = new ArrayList<>();

	public ItemSelection(int maxStacks) {
		this.maxStacks = maxStacks;
	}

	/**
	 * Picks the stack in a slot of the player's inventory: all of it, or one
	 * more. Tells the player why when it can't be picked.
	 *
	 * @return whether the selection changed
	 */
	public boolean add(Player player, int slot, boolean all) {
		PlayerInventory inventory = player.getInventory();
		if (slot < 0 || slot >= inventory.getStorageContents().length)
			return false;
		ItemStack item = inventory.getItem(slot);
		if (item == null || item.isEmpty())
			return false;
		if (DuelKit.isKitItem(item)) {
			ColorUtil.sendMessage(player, "&cDuel kit items can't be traded.");
			return false;
		}

		int index = indexOf(slot);
		// Something else is in that slot now: pick it afresh
		if (index >= 0 && !picks.get(index).item().isSimilar(item)) {
			picks.remove(index);
			index = -1;
		}
		int picked = index >= 0 ? picks.get(index).amount() : 0;
		if (picked >= item.getAmount()) {
			ColorUtil.sendMessage(player, "&eYou already put in all of that stack.");
			return false;
		}
		if (index < 0 && picks.size() >= maxStacks) {
			ColorUtil.sendMessage(player, "&cThere's no room for more stacks.");
			return false;
		}

		Pick pick = new Pick(slot, item.asOne(), all ? item.getAmount() : picked + 1);
		if (index >= 0)
			picks.set(index, pick);
		else
			picks.add(pick);
		return true;
	}

	/**
	 * Puts back the stack shown at {@code index} in {@link #items()}: all of it, or one.
	 *
	 * @return whether the selection changed
	 */
	public boolean remove(int index, boolean all) {
		if (index < 0 || index >= picks.size())
			return false;
		Pick pick = picks.get(index);
		if (all || pick.amount() <= 1)
			picks.remove(index);
		else
			picks.set(index, new Pick(pick.slot(), pick.item(), pick.amount() - 1));
		return true;
	}

	/** The picked items, one stack per picked slot, in the order they were picked. */
	public List<ItemStack> items() {
		List<ItemStack> items = new ArrayList<>(picks.size());
		for (Pick pick : picks)
			items.add(pick.item().asQuantity(pick.amount()));
		return items;
	}

	public boolean isEmpty() {
		return picks.isEmpty();
	}

	/**
	 * Forgets picks whose items left the inventory, and lowers amounts to what
	 * is still there.
	 *
	 * @return whether anything changed
	 */
	public boolean prune(PlayerInventory inventory) {
		boolean changed = false;
		for (int i = picks.size() - 1; i >= 0; i--) {
			Pick pick = picks.get(i);
			ItemStack now = inventory.getItem(pick.slot());
			if (now == null || !now.isSimilar(pick.item())) {
				picks.remove(i);
				changed = true;
			} else if (now.getAmount() < pick.amount()) {
				picks.set(i, new Pick(pick.slot(), pick.item(), now.getAmount()));
				changed = true;
			}
		}
		return changed;
	}

	/**
	 * Removes the picked items from the inventory and returns them; the
	 * selection is empty afterwards. Call {@link #prune} first.
	 *
	 * @throws IllegalStateException if some of the items are no longer there (nothing is taken then)
	 */
	public List<ItemStack> take(PlayerInventory inventory) {
		for (Pick pick : picks) {
			ItemStack now = inventory.getItem(pick.slot());
			if (now == null || !now.isSimilar(pick.item()) || now.getAmount() < pick.amount())
				throw new IllegalStateException("Picked items left slot " + pick.slot());
		}

		List<ItemStack> taken = new ArrayList<>(picks.size());
		for (Pick pick : picks) {
			ItemStack now = inventory.getItem(pick.slot());
			taken.add(now.asQuantity(pick.amount()));
			int left = now.getAmount() - pick.amount();
			inventory.setItem(pick.slot(), left > 0 ? now.asQuantity(left) : null);
		}
		picks.clear();
		return taken;
	}

	/**
	 * Whether all the items would fit in the inventory, once {@code leaving}
	 * (the player's own offer, or null) has been taken out of it.
	 */
	public static boolean fits(PlayerInventory inventory, @Nullable ItemSelection leaving, List<ItemStack> items) {
		ItemStack[] storage = inventory.getStorageContents();
		ItemStack[] copy = new ItemStack[storage.length];
		for (int slot = 0; slot < storage.length; slot++)
			copy[slot] = storage[slot] != null ? storage[slot].clone() : null;
		if (leaving != null) {
			for (Pick pick : leaving.picks) {
				ItemStack item = pick.slot() < copy.length ? copy[pick.slot()] : null;
				if (item != null) {
					int left = item.getAmount() - pick.amount();
					copy[pick.slot()] = left > 0 ? item.asQuantity(left) : null;
				}
			}
		}

		// Adding to a scratch copy follows the same stacking rules as the real inventory
		Inventory scratch = Bukkit.createInventory(null, (copy.length + 8) / 9 * 9);
		for (int slot = 0; slot < copy.length; slot++)
			scratch.setItem(slot, copy[slot]);
		for (ItemStack item : items) {
			if (!scratch.addItem(item.clone()).isEmpty())
				return false;
		}
		return true;
	}

	private int indexOf(int slot) {
		for (int i = 0; i < picks.size(); i++) {
			if (picks.get(i).slot() == slot)
				return i;
		}
		return -1;
	}
}
