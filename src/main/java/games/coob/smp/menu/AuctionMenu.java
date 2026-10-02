package games.coob.smp.menu;

import games.coob.smp.auction.AuctionHouse;
import games.coob.smp.auction.AuctionListing;
import games.coob.smp.auction.AuctionView;
import games.coob.smp.auction.CollectionBox;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.ItemCreator;
import games.coob.smp.util.ItemText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /auction: everything up for offers, newest first.
 */
public final class AuctionMenu extends SimpleMenu implements AuctionView {

	/** Which listings are shown. */
	public enum Filter {
		ALL("All listings"),
		MINE("Your listings"),
		OFFERED("Ones you made an offer on");

		private final String label;

		Filter(String label) {
			this.label = label;
		}
	}

	private static final int PER_PAGE = 45;
	private static final int SLOT_SELL = 45;
	private static final int SLOT_FILTER = 46;
	private static final int SLOT_PREVIOUS = 48;
	private static final int SLOT_HELP = 49;
	private static final int SLOT_NEXT = 50;
	private static final int SLOT_BOX = 53;

	/** Ids of the listings on this page, by slot. */
	private final List<String> shown = new ArrayList<>();
	private Filter filter;
	private int page;

	public AuctionMenu(Player viewer, Filter filter) {
		super(viewer, 54, "&8Auction house");
		this.filter = filter;
		render();
	}

	/** The menu for picking items to put up, with what the seller wants back (or null). */
	public static void openSellPicker(Player player, @Nullable String wants, @Nullable Runnable onBack) {
		String problem = AuctionHouse.getInstance().whyCantSell(player);
		if (problem != null) {
			ColorUtil.sendMessage(player, problem);
			return;
		}
		List<String> notes = wants != null
				? List.of("&7You want: &f" + wants)
				: List.of("&7To say what you want back,", "&7use &f/ah sell <what you want>");
		new ItemPickerMenu(player, "&8Pick items to sell", "Put up for auction", notes,
				selection -> AuctionHouse.getInstance().list(player, selection, wants), onBack).displayTo(player);
	}

	private List<AuctionListing> listings() {
		List<AuctionListing> all = AuctionHouse.getInstance().getListings();
		UUID id = viewer.getUniqueId();
		return switch (filter) {
			case ALL -> all;
			case MINE -> all.stream().filter(listing -> listing.getSellerId().equals(id)).toList();
			case OFFERED -> all.stream().filter(listing -> listing.getOffer(id) != null).toList();
		};
	}

	private void render() {
		inventory.clear();
		shown.clear();
		List<AuctionListing> listings = listings();
		page = Math.min(page, Math.max(0, (listings.size() - 1) / PER_PAGE));
		long now = System.currentTimeMillis();
		int start = page * PER_PAGE;
		for (int i = start; i < Math.min(start + PER_PAGE, listings.size()); i++) {
			inventory.setItem(i - start, icon(listings.get(i), now));
			shown.add(listings.get(i).getId());
		}
		if (listings.isEmpty())
			inventory.setItem(22, ItemCreator.of(Material.BARRIER, "&7Nothing here yet",
					"", filter == Filter.ALL ? "&7Be the first: click &aSell items&7." : "&7Click the hopper to see every listing.").make());

		inventory.setItem(SLOT_SELL, ItemCreator.of(Material.CHEST, "&a&lSell items",
				"", "&7Put items up for anyone", "&7to make an offer on.",
				"", "&7To say what you want back:", "&f/ah sell <what you want>",
				"", "&eClick to pick items").make());
		inventory.setItem(SLOT_FILTER, ItemCreator.of(Material.HOPPER, "&b&lShowing: " + filter.label,
				"", "&eClick to change").make());
		inventory.setItem(SLOT_HELP, ItemCreator.of(Material.BOOK, "&6&lHow it works",
				"", "&7There's no money here:", "&7offers are made with items.",
				"&7Open a listing to make one;", "&7the seller takes the offer", "&7they like best.",
				"", "&7Offered items are held safely", "&7and come back if not taken.").make());
		int stacks = CollectionBox.of(viewer.getUniqueId()).size();
		inventory.setItem(SLOT_BOX, ItemCreator.of(Material.ENDER_CHEST, "&d&lCollection box",
				"", stacks > 0 ? "&f" + stacks + " &7stack" + (stacks == 1 ? "" : "s") + " waiting for you" : "&7Nothing waiting for you",
				"", "&eClick to open").make());
		if (page > 0)
			inventory.setItem(SLOT_PREVIOUS, ItemCreator.of(Material.ARROW, "&a&lPrevious page").make());
		if (start + PER_PAGE < listings.size())
			inventory.setItem(SLOT_NEXT, ItemCreator.of(Material.ARROW, "&a&lNext page").make());
	}

	/** The listing's first item, with who sells it, what is in it and how it's going. */
	private ItemStack icon(AuctionListing listing, long now) {
		List<ItemStack> items = listing.getItems();
		List<Component> lore = new ArrayList<>();
		lore.add(ColorUtil.toComponent("&7Seller: &f" + listing.getSellerName()));
		lore.addAll(ItemText.lines(items, 6));
		if (listing.getWants() != null)
			lore.add(ColorUtil.toComponent("&7Wants: ").append(Component.text(listing.getWants(), NamedTextColor.WHITE)));
		lore.add(ColorUtil.toComponent("&7Offers: &f" + listing.getOffers().size()));
		lore.add(ColorUtil.toComponent("&7Ends in: &f" + AuctionHouse.formatTime(listing.getEndsAt() - now)));
		lore.add(ColorUtil.toComponent(listing.isSeller(viewer) ? "&eClick to see the offers" : "&eClick to view"));
		return ItemText.withLore(items.getFirst(), lore);
	}

	@Override
	public void refresh() {
		render();
	}

	@Override
	protected void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
		// A double-click would skip a filter
		boolean repeat = isDoubleClick(slot, clickType);
		Runnable back = () -> {
			render();
			displayTo(player);
		};
		if (slot < PER_PAGE) {
			if (slot >= shown.size())
				return;
			AuctionListing listing = AuctionHouse.getInstance().get(shown.get(slot));
			if (listing == null)
				render();
			else
				new AuctionListingMenu(player, listing, back).displayTo(player);
		} else if (slot == SLOT_SELL) {
			openSellPicker(player, null, back);
		} else if (slot == SLOT_FILTER && !repeat) {
			filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
			page = 0;
			render();
		} else if (slot == SLOT_PREVIOUS && page > 0) {
			page--;
			render();
		} else if (slot == SLOT_NEXT) {
			page++;
			render();
		} else if (slot == SLOT_BOX) {
			new CollectionBoxMenu(player, back).displayTo(player);
		}
	}
}
