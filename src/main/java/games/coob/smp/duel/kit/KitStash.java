package games.coob.smp.duel.kit;

import games.coob.smp.PlayerCache;
import games.coob.smp.SMPPlugin;
import games.coob.smp.util.InventorySerialization;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Keeps a player's own items, XP and effects safe while a duel kit replaces
 * them, and gives them back afterwards (when they are sent home, respawn, or
 * join again after a disconnect or crash).
 * <p>
 * The stored copy is written to disk before the kit is given, and forgotten
 * only after the player's own file has been saved with their items back, so a
 * crash at any point can neither lose nor duplicate anything.
 */
public final class KitStash {

	private KitStash() {
	}

	/**
	 * Stores the player's own gear before a kit replaces it.
	 *
	 * @return false if it couldn't be stored, because an older copy is still
	 *         waiting to be given back: the player must not get a kit then
	 */
	public static boolean store(Player player) {
		PlayerCache cache = PlayerCache.from(player);
		if (cache.hasKitStash())
			return false;

		// The cursor and crafting grid aren't part of the inventory, and closing the menu could
		// drop them on the ground: they are stored too, after the inventory's own slots
		List<ItemStack> extra = new ArrayList<>();
		ItemStack cursor = player.getItemOnCursor();
		if (!cursor.isEmpty()) {
			extra.add(cursor.clone());
			player.setItemOnCursor(null);
		}
		if (player.getOpenInventory().getTopInventory() instanceof CraftingInventory crafting) {
			ItemStack[] matrix = crafting.getMatrix();
			for (ItemStack item : matrix) {
				if (item != null && !item.isEmpty())
					extra.add(item.clone());
			}
			crafting.setMatrix(new ItemStack[matrix.length]);
		}
		player.closeInventory();

		ItemStack[] contents = player.getInventory().getContents();
		ItemStack[] all = Arrays.copyOf(contents, contents.length + extra.size());
		for (int i = 0; i < extra.size(); i++)
			all[contents.length + i] = extra.get(i);

		List<Map<String, Object>> effects = new ArrayList<>();
		for (PotionEffect effect : player.getActivePotionEffects())
			effects.add(effect.serialize());
		cache.setKitStash(InventorySerialization.toBase64(all), player.getLevel(), player.getExp(), effects);
		return true;
	}

	/**
	 * Replaces the kit with the player's own gear, if a kit replaced it.
	 *
	 * @return whether anything was given back
	 */
	public static boolean restore(Player player) {
		PlayerCache cache = PlayerCache.from(player);
		if (!cache.hasKitStash())
			return false;

		ItemStack[] items;
		try {
			items = InventorySerialization.fromBase64(cache.getKitStashItems());
		} catch (RuntimeException e) {
			// Keep the stored copy, so an admin can still get the items back from the player's data file
			SMPPlugin.getInstance().getLogger().log(Level.SEVERE, "Could not read " + player.getName()
					+ "'s stored duel items; they are still in their SMP data file", e);
			return false;
		}

		player.closeInventory();
		player.setItemOnCursor(null);
		PlayerInventory inventory = player.getInventory();
		inventory.clear();
		inventory.setContents(Arrays.copyOf(items, inventory.getSize()));
		// What was on the cursor or in the crafting grid
		for (int i = inventory.getSize(); i < items.length; i++) {
			if (items[i] == null || items[i].isEmpty())
				continue;
			for (ItemStack leftover : inventory.addItem(items[i]).values())
				player.getWorld().dropItemNaturally(player.getLocation(), leftover);
		}
		player.setLevel(cache.getKitStashLevel());
		player.setExp(Math.clamp(cache.getKitStashExp(), 0f, 0.9999f));
		for (PotionEffect effect : player.getActivePotionEffects())
			player.removePotionEffect(effect.getType());
		for (Map<String, Object> effect : cache.getKitStashEffects()) {
			try {
				player.addPotionEffect(new PotionEffect(effect));
			} catch (RuntimeException e) {
				// An effect that no longer exists: the rest still come back
				SMPPlugin.getInstance().getLogger().warning("Could not give " + player.getName()
						+ " back a stored effect " + effect + ": " + e.getMessage());
			}
		}

		// Their own items are in their player file before the stored copy is forgotten
		player.saveData();
		cache.clearKitStash();
		return true;
	}

	/** Removes every kit item from an inventory. */
	public static int removeKitItems(Inventory inventory) {
		int removed = 0;
		ItemStack[] contents = inventory.getContents();
		for (int slot = 0; slot < contents.length; slot++) {
			if (DuelKit.isKitItem(contents[slot])) {
				inventory.setItem(slot, null);
				removed++;
			}
		}
		return removed;
	}
}
