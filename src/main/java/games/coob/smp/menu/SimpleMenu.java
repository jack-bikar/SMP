package games.coob.smp.menu;

import games.coob.smp.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Base class for simple menus
 */
public abstract class SimpleMenu implements Listener {

	protected Inventory inventory;
	protected Player viewer;

	public SimpleMenu(Player viewer, int size, String title) {
		this.viewer = viewer;
		Component titleComponent = ColorUtil.toComponent(title);
		this.inventory = Bukkit.createInventory(null, size, titleComponent);
		Bukkit.getPluginManager().registerEvents(this, games.coob.smp.SMPPlugin.getInstance());
	}

	public void displayTo(Player player) {
		player.openInventory(inventory);
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if (event.getWhoClicked() instanceof Player player && event.getInventory().equals(inventory)) {
			event.setCancelled(true);
			onMenuClick(player, event.getSlot(), event.getCurrentItem());
		}
	}

	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {
		if (event.getPlayer() instanceof Player player && event.getInventory().equals(inventory)) {
			onMenuClose(player, inventory);
		}
	}

	protected abstract void onMenuClick(Player player, int slot, ItemStack clicked);

	protected void onMenuClose(Player player, Inventory inventory) {
	}

	public Inventory getInventory() {
		return inventory;
	}
}
