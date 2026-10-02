package games.coob.smp.menu;

import games.coob.smp.auction.AuctionHouse;
import games.coob.smp.util.ItemCreator;
import games.coob.smp.util.ItemSelection;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Picking items from your inventory, for an auction listing or an offer.
 * The items stay in your inventory until you confirm.
 */
public final class ItemPickerMenu extends SimpleMenu {

	private static final int SLOT_BACK = 27;
	private static final int SLOT_INFO = 31;
	private static final int SLOT_CONFIRM = 35;

	private final ItemSelection selection = new ItemSelection(AuctionHouse.MAX_STACKS);
	private final String confirmLabel;
	private final List<String> notes;
	private final Predicate<ItemSelection> onConfirm;
	private final @Nullable Runnable onBack;

	/**
	 * @param notes     extra lines on the info item (e.g. what the seller wants)
	 * @param onConfirm uses the picked items: true when done, false to keep picking (it tells the player why)
	 * @param onBack    the back button, also opened once done; or null for a cancel button that closes the menu
	 */
	public ItemPickerMenu(Player viewer, String title, String confirmLabel, List<String> notes,
			Predicate<ItemSelection> onConfirm, @Nullable Runnable onBack) {
		super(viewer, 36, title);
		this.confirmLabel = confirmLabel;
		this.notes = notes;
		this.onConfirm = onConfirm;
		this.onBack = onBack;
		render();
	}

	private void render() {
		inventory.clear();
		List<ItemStack> picked = selection.items();
		for (int i = 0; i < picked.size(); i++)
			inventory.setItem(i, picked.get(i));
		ItemStack filler = ItemCreator.of(Material.GRAY_STAINED_GLASS_PANE, " ").make();
		for (int slot = AuctionHouse.MAX_STACKS; slot < inventory.getSize(); slot++)
			inventory.setItem(slot, filler);

		inventory.setItem(SLOT_BACK, onBack != null
				? ItemCreator.of(Material.ARROW, "&c&lBack").make()
				: ItemCreator.of(Material.BARRIER, "&c&lCancel").make());
		List<String> info = new ArrayList<>(List.of("", "&7Click items in your inventory", "&7to add them &8(right-click: one)&7.",
				"&7Click them up here to take", "&7them out again."));
		if (!notes.isEmpty()) {
			info.add("");
			info.addAll(notes);
		}
		inventory.setItem(SLOT_INFO, ItemCreator.of(Material.BOOK, "&e&lPick items", info.toArray(new String[0])).make());
		inventory.setItem(SLOT_CONFIRM, selection.isEmpty()
				? ItemCreator.of(Material.GRAY_DYE, "&7&l" + confirmLabel, "", "&7Pick some items first.").make()
				: ItemCreator.of(Material.LIME_DYE, "&a&l" + confirmLabel, "", "&eClick to confirm").make());
	}

	@Override
	protected void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
		// A double-click would take out the next stack too
		if (isDoubleClick(slot, clickType))
			return;
		if (slot < AuctionHouse.MAX_STACKS) {
			if ((clickType.isLeftClick() || clickType.isRightClick()) && selection.remove(slot, !clickType.isRightClick()))
				render();
		} else if (slot == SLOT_BACK) {
			close(player);
		} else if (slot == SLOT_CONFIRM && !selection.isEmpty()) {
			if (onConfirm.test(selection))
				close(player);
			else
				render();
		}
	}

	@Override
	protected void onPlayerInventoryClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
		if ((clickType.isLeftClick() || clickType.isRightClick()) && selection.add(player, slot, !clickType.isRightClick()))
			render();
	}

	private void close(Player player) {
		if (onBack != null)
			onBack.run();
		else
			player.closeInventory();
	}
}
