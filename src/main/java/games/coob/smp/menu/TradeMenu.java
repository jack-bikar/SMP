package games.coob.smp.menu;

import games.coob.smp.trade.TradeSession;
import games.coob.smp.util.ItemCreator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * One player's view of a trade: their own offer on the left, the other
 * player's on the right. Items are offered by clicking them in the inventory
 * below and taken back by clicking them in the offer; nothing can be dragged,
 * so nothing can slip between the two sides.
 */
public final class TradeMenu extends SimpleMenu {

	private static final int ROWS = 5;
	private static final int SLOT_READY = 45;
	private static final int SLOT_HELP = 47;
	private static final int SLOT_CANCEL = 49;
	private static final int SLOT_PARTNER = 53;

	private final TradeSession session;

	public TradeMenu(Player viewer, TradeSession session) {
		super(viewer, 54, "&8Trade with " + session.getPartnerName(viewer));
		this.session = session;
		render();
	}

	/** The slot of offer stack {@code index}: 4 columns on the left for your own, on the right for theirs. */
	private static int slotOf(int index, boolean own) {
		return index / 4 * 9 + index % 4 + (own ? 0 : 5);
	}

	public void render() {
		inventory.clear();
		String partner = session.getPartnerName(viewer);
		List<ItemStack> own = session.getOffer(viewer);
		List<ItemStack> theirs = session.getPartnerOffer(viewer);
		for (int i = 0; i < own.size(); i++)
			inventory.setItem(slotOf(i, true), own.get(i));
		for (int i = 0; i < theirs.size(); i++)
			inventory.setItem(slotOf(i, false), theirs.get(i));

		ItemStack divider = ItemCreator.of(Material.BLACK_STAINED_GLASS_PANE, "&7Left: your offer",
				"&7Right: " + partner + "'s offer").make();
		for (int row = 0; row < ROWS; row++)
			inventory.setItem(row * 9 + 4, divider);
		ItemStack filler = ItemCreator.of(Material.GRAY_STAINED_GLASS_PANE, " ").make();
		for (int slot = ROWS * 9; slot < 54; slot++)
			inventory.setItem(slot, filler);

		int seconds = session.getSecondsLeft();
		String countdown = seconds > 0 ? "&eSwapping in " + seconds + "..." : null;
		inventory.setItem(SLOT_READY, session.isReady(viewer)
				? ItemCreator.of(Material.LIME_DYE, "&a&lReady",
						"", countdown != null ? countdown : "&7Waiting for &f" + partner + "&7.",
						"", "&eClick to change your mind").make()
				: ItemCreator.of(Material.GRAY_DYE, "&7&lNot ready",
						"", "&7Click when you're happy", "&7with both sides.").make());
		inventory.setItem(SLOT_PARTNER, session.isPartnerReady(viewer)
				? ItemCreator.of(Material.LIME_DYE, "&a&l" + partner + " is ready", "", countdown).make()
				: ItemCreator.of(Material.GRAY_DYE, "&7&l" + partner + " is choosing").make());

		inventory.setItem(SLOT_HELP, ItemCreator.of(Material.BOOK, "&e&lHow to trade",
				"", "&7Click items in your inventory", "&7to offer them &8(right-click: one)&7.",
				"&7Click your offer to take them back.",
				"", "&7Nothing moves until you are", "&7both ready. Any change takes", "&7back both Ready clicks.").make());
		inventory.setItem(SLOT_CANCEL, ItemCreator.of(Material.BARRIER, "&c&lCancel trade").make());
	}

	@Override
	protected void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
		// A double-click would take back the next stack too, or turn Ready straight off again
		if (isDoubleClick(slot, clickType))
			return;
		int row = slot / 9;
		int column = slot % 9;
		if (row < ROWS && column < 4) {
			if (clickType.isLeftClick() || clickType.isRightClick())
				session.removeItem(player, row * 4 + column, !clickType.isRightClick());
		} else if (slot == SLOT_READY) {
			session.toggleReady(player);
		} else if (slot == SLOT_CANCEL) {
			// Closing the menu cancels the trade
			player.closeInventory();
		}
	}

	@Override
	protected void onPlayerInventoryClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
		if (clickType.isLeftClick() || clickType.isRightClick())
			session.addItem(player, slot, !clickType.isRightClick());
	}

	@Override
	protected void onMenuClose(Player player, Inventory inventory) {
		session.menuClosed(player);
	}
}
