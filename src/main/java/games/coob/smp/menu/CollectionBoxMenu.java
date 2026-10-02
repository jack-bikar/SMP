package games.coob.smp.menu;

import games.coob.smp.auction.AuctionHouse;
import games.coob.smp.auction.AuctionView;
import games.coob.smp.auction.CollectionBox;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.ItemCreator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Items the auction house is holding for the viewer: click one to take it,
 * or take everything that fits.
 */
public final class CollectionBoxMenu extends SimpleMenu implements AuctionView {

	private static final int PER_PAGE = 45;
	private static final int SLOT_BACK = 45;
	private static final int SLOT_PREVIOUS = 48;
	private static final int SLOT_TAKE_ALL = 49;
	private static final int SLOT_NEXT = 50;

	private final @Nullable Runnable onBack;
	private int page;

	/** @param onBack the back button, or null for none */
	public CollectionBoxMenu(Player viewer, @Nullable Runnable onBack) {
		super(viewer, 54, "&8Collection box");
		this.onBack = onBack;
		render();
	}

	private CollectionBox box() {
		return CollectionBox.of(viewer.getUniqueId());
	}

	private void render() {
		inventory.clear();
		List<ItemStack> items = box().getItems();
		page = Math.min(page, Math.max(0, (items.size() - 1) / PER_PAGE));
		int start = page * PER_PAGE;
		for (int i = start; i < Math.min(start + PER_PAGE, items.size()); i++)
			inventory.setItem(i - start, items.get(i));

		if (items.isEmpty())
			inventory.setItem(22, ItemCreator.of(Material.BARRIER, "&7Nothing waiting for you",
					"", "&7Offers you take, things you", "&7win and items that come back", "&7from the auction house land here.").make());
		else
			inventory.setItem(SLOT_TAKE_ALL, ItemCreator.of(Material.HOPPER, "&a&lTake everything",
					"", "&7Moves everything that fits", "&7into your inventory.", "", "&7Or click one item to take it.").make());
		if (onBack != null)
			inventory.setItem(SLOT_BACK, ItemCreator.of(Material.ARROW, "&c&lBack").make());
		if (page > 0)
			inventory.setItem(SLOT_PREVIOUS, ItemCreator.of(Material.ARROW, "&a&lPrevious page").make());
		if (start + PER_PAGE < items.size())
			inventory.setItem(SLOT_NEXT, ItemCreator.of(Material.ARROW, "&a&lNext page").make());
	}

	@Override
	public void refresh() {
		render();
	}

	@Override
	protected void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
		// A double-click would take the next item too
		if (isDoubleClick(slot, clickType))
			return;
		if (slot == SLOT_BACK && onBack != null) {
			onBack.run();
		} else if (slot == SLOT_PREVIOUS && page > 0) {
			page--;
			render();
		} else if (slot == SLOT_NEXT) {
			page++;
			render();
		} else if (slot == SLOT_TAKE_ALL || slot < PER_PAGE) {
			CollectionBox box = box();
			int index = slot == SLOT_TAKE_ALL ? -1 : page * PER_PAGE + slot;
			if (box.isEmpty() || index >= box.size())
				return;
			String problem = AuctionHouse.whyCantUse(player);
			if (problem != null) {
				ColorUtil.sendMessage(player, problem);
				return;
			}
			if (index < 0) {
				int left = box.takeAll(player);
				if (left > 0)
					ColorUtil.sendMessage(player, "&eYour inventory is full; " + left + " stack" + (left == 1 ? " is" : "s are") + " still here.");
			} else if (!box.take(player, index)) {
				ColorUtil.sendMessage(player, "&cYou don't have room for that.");
			}
			render();
		}
	}
}
