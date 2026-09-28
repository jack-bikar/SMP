package games.coob.smp.menu;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Routes inventory events to the {@link SimpleMenu} that owns the inventory.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MenuListener implements Listener {

	private static final MenuListener instance = new MenuListener();

	public static MenuListener getInstance() {
		return instance;
	}

	@EventHandler(ignoreCancelled = true)
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getInventory().getHolder(false) instanceof SimpleMenu menu)
				|| !(event.getWhoClicked() instanceof Player player))
			return;

		if (!menu.isEditable()) {
			event.setCancelled(true);
			// Only react to clicks in the menu itself, not the player's own inventory
			if (event.getClickedInventory() != menu.getInventory())
				return;
		} else if (event.getClickedInventory() == menu.getInventory() && menu.isLockedSlot(event.getSlot())) {
			event.setCancelled(true);
			return;
		}

		menu.onMenuClick(player, event.getSlot(), event.getCurrentItem(), event.getClick());
	}

	@EventHandler(ignoreCancelled = true)
	public void onInventoryDrag(InventoryDragEvent event) {
		if (!(event.getInventory().getHolder(false) instanceof SimpleMenu menu))
			return;
		int topSize = menu.getInventory().getSize();
		for (int rawSlot : event.getRawSlots()) {
			if (rawSlot < topSize && (!menu.isEditable() || menu.isLockedSlot(rawSlot))) {
				event.setCancelled(true);
				return;
			}
		}
	}

	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {
		if (event.getInventory().getHolder(false) instanceof SimpleMenu menu
				&& event.getPlayer() instanceof Player player)
			menu.onMenuClose(player, menu.getInventory());
	}
}
