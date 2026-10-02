package games.coob.smp.menu;

import games.coob.smp.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

/**
 * Base class for simple menus. The menu is the inventory's holder, so a single
 * {@link MenuListener} can route clicks without registering a listener per menu.
 */
public abstract class SimpleMenu implements InventoryHolder {

	/** Longest gap between the two clicks of a double-click, with room for network delay. */
	private static final long DOUBLE_CLICK_MILLIS = 350;

	protected final Inventory inventory;
	protected final Player viewer;
	private int lastClickSlot = -1;
	private long lastClickTime;

	public SimpleMenu(Player viewer, int size, String title) {
		this.viewer = viewer;
		Component titleComponent = ColorUtil.toComponent(title);
		this.inventory = Bukkit.createInventory(this, size, titleComponent);
	}

	@Override
	public @NonNull Inventory getInventory() {
		return inventory;
	}

	public void displayTo(Player player) {
		player.openInventory(inventory);
	}

	/**
	 * Whether players may move items in this menu. Button menus return false,
	 * so every click is cancelled before {@link #onMenuClick} is called.
	 */
	protected boolean isEditable() {
		return false;
	}

	/**
	 * For editable menus: slots in the menu that can't be changed (e.g. fillers).
	 */
	protected boolean isLockedSlot(int slot) {
		return false;
	}

	protected abstract void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType);

	/**
	 * Whether this left click repeats the last one on the same slot within a
	 * double-click. The client sends a double-click as two separate clicks, so
	 * buttons whose second press does something else (confirm, toggle back,
	 * take the next item) ignore it. Call it on every click to keep track.
	 */
	protected boolean isDoubleClick(int slot, ClickType clickType) {
		if (clickType != ClickType.LEFT)
			return false;
		long now = System.currentTimeMillis();
		boolean repeat = slot == lastClickSlot && now - lastClickTime < DOUBLE_CLICK_MILLIS;
		lastClickSlot = slot;
		lastClickTime = now;
		return repeat;
	}

	/**
	 * For button menus: a click in the player's own inventory below the menu
	 * (already cancelled). {@code slot} is the slot in the player's inventory.
	 */
	protected void onPlayerInventoryClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
	}

	protected void onMenuClose(Player player, Inventory inventory) {
	}
}
