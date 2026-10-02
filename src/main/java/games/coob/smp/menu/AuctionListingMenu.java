package games.coob.smp.menu;

import games.coob.smp.auction.AuctionHouse;
import games.coob.smp.auction.AuctionListing;
import games.coob.smp.auction.AuctionOffer;
import games.coob.smp.auction.AuctionView;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.ItemCreator;
import games.coob.smp.util.ItemText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One listing: its items, who sells them and what they want, and every offer
 * made on it. The seller opens an offer from here to take it; anyone else
 * makes or withdraws their own.
 */
public final class AuctionListingMenu extends SimpleMenu implements AuctionView {

	private static final int OFFERS_PER_PAGE = 9;
	private static final int SLOT_INFO = 31;
	private static final int FIRST_OFFER_SLOT = 36;
	private static final int SLOT_BACK = 45;
	private static final int SLOT_PREVIOUS = 47;
	private static final int SLOT_ACTION = 49;
	private static final int SLOT_NEXT = 51;
	private static final int SLOT_ADMIN = 53;

	private final String listingId;
	private final Runnable onBack;
	/** Bidders of the offers on this page, by slot. */
	private final List<UUID> shownOffers = new ArrayList<>();
	private int page;
	/** A button clicked once that needs a second click to go ahead, or -1. */
	private int confirming = -1;

	public AuctionListingMenu(Player viewer, AuctionListing listing, Runnable onBack) {
		super(viewer, 54, "&8" + listing.getSellerName() + "'s listing");
		this.listingId = listing.getId();
		this.onBack = onBack;
		render();
	}

	private void render() {
		inventory.clear();
		shownOffers.clear();
		AuctionListing listing = AuctionHouse.getInstance().get(listingId);
		if (listing == null) {
			inventory.setItem(22, ItemCreator.of(Material.BARRIER, "&cThis listing has ended",
					"", "&7The seller took an offer,", "&7took it down, or it ran", "&7out of time.").make());
			inventory.setItem(SLOT_BACK, ItemCreator.of(Material.ARROW, "&c&lBack").make());
			return;
		}

		List<ItemStack> items = listing.getItems();
		for (int i = 0; i < items.size() && i < AuctionHouse.MAX_STACKS; i++)
			inventory.setItem(i, items.get(i));
		ItemStack filler = ItemCreator.of(Material.GRAY_STAINED_GLASS_PANE, " ").make();
		for (int slot = AuctionHouse.MAX_STACKS; slot < FIRST_OFFER_SLOT; slot++)
			inventory.setItem(slot, filler);
		for (int slot = SLOT_BACK; slot < 54; slot++)
			inventory.setItem(slot, filler);

		List<AuctionOffer> offers = listing.getOffers();
		List<Component> info = new ArrayList<>();
		if (listing.getWants() != null)
			info.add(ColorUtil.toComponent("&7Wants: ").append(Component.text(listing.getWants(), NamedTextColor.WHITE)));
		info.add(ColorUtil.toComponent("&7Offers: &f" + offers.size()));
		info.add(ColorUtil.toComponent("&7Ends in: &f" + AuctionHouse.formatTime(listing.getEndsAt() - System.currentTimeMillis())));
		inventory.setItem(SLOT_INFO, ItemCreator.of(Material.PLAYER_HEAD, "&6&l" + listing.getSellerName() + "'s listing")
				.lore(info).skullOwner(Bukkit.getOfflinePlayer(listing.getSellerId())).make());

		boolean seller = listing.isSeller(viewer);
		page = Math.min(page, Math.max(0, (offers.size() - 1) / OFFERS_PER_PAGE));
		int start = page * OFFERS_PER_PAGE;
		for (int i = start; i < Math.min(start + OFFERS_PER_PAGE, offers.size()); i++) {
			AuctionOffer offer = offers.get(i);
			List<Component> lore = new ArrayList<>(ItemText.lines(offer.items(), 8));
			lore.add(ColorUtil.toComponent(seller ? "&eClick to see it or take it" : "&eClick to see it"));
			inventory.setItem(FIRST_OFFER_SLOT + i - start, ItemCreator.of(Material.PLAYER_HEAD, "&b&l" + offer.bidderName() + "'s offer")
					.lore(lore).skullOwner(Bukkit.getOfflinePlayer(offer.bidderId())).make());
			shownOffers.add(offer.bidderId());
		}
		if (offers.isEmpty())
			inventory.setItem(FIRST_OFFER_SLOT + 4, ItemCreator.of(Material.GRAY_DYE, "&7No offers yet",
					"", seller ? "&7You'll hear in chat when" : "&7Be the first to make one.", seller ? "&7someone makes one." : null).make());

		inventory.setItem(SLOT_BACK, ItemCreator.of(Material.ARROW, "&c&lBack").make());
		if (page > 0)
			inventory.setItem(SLOT_PREVIOUS, ItemCreator.of(Material.ARROW, "&a&lPrevious offers").make());
		if (start + OFFERS_PER_PAGE < offers.size())
			inventory.setItem(SLOT_NEXT, ItemCreator.of(Material.ARROW, "&a&lMore offers").make());

		if (seller)
			inventory.setItem(SLOT_ACTION, confirming == SLOT_ACTION
					? ItemCreator.of(Material.RED_CONCRETE, "&c&lClick again to take it down",
							"", "&7Your items and every offer", "&7go back to their owners.").make()
					: ItemCreator.of(Material.BARRIER, "&c&lTake listing down",
							"", "&7Your items and every offer", "&7go back to their owners.").make());
		else if (listing.getOffer(viewer.getUniqueId()) != null)
			inventory.setItem(SLOT_ACTION, ItemCreator.of(Material.HOPPER, "&e&lWithdraw your offer",
					"", "&7Your items come back to you.").make());
		else
			inventory.setItem(SLOT_ACTION, ItemCreator.of(Material.WRITABLE_BOOK, "&a&lMake an offer",
					"", "&7Pick items from your", "&7inventory to offer for this.", "", "&eClick to choose").make());

		if (!seller && viewer.hasPermission(AuctionHouse.ADMIN_PERMISSION))
			inventory.setItem(SLOT_ADMIN, ItemCreator.of(Material.TNT,
					confirming == SLOT_ADMIN ? "&4&lClick again to remove" : "&4&lRemove listing &7(admin)",
					"", "&7Everything goes back", "&7to its owners.").make());
	}

	@Override
	public void refresh() {
		confirming = -1;
		render();
	}

	@Override
	protected void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
		if (slot == SLOT_BACK) {
			onBack.run();
			return;
		}
		// The client sends a double-click as two clicks: it mustn't confirm what the first one asked about
		if (isDoubleClick(slot, clickType))
			return;
		AuctionListing listing = AuctionHouse.getInstance().get(listingId);
		if (listing == null)
			return;
		int confirmed = confirming;
		confirming = -1;
		Runnable back = () -> {
			render();
			displayTo(player);
		};

		if (slot >= FIRST_OFFER_SLOT && slot < FIRST_OFFER_SLOT + OFFERS_PER_PAGE) {
			int index = slot - FIRST_OFFER_SLOT;
			if (index < shownOffers.size())
				new AuctionOfferMenu(player, listing, shownOffers.get(index), back).displayTo(player);
			return;
		}
		switch (slot) {
			case SLOT_PREVIOUS -> page = Math.max(0, page - 1);
			case SLOT_NEXT -> page++;
			case SLOT_ACTION -> {
				if (listing.isSeller(player)) {
					// Ending it sends every offer back: a second click makes sure it was meant
					if (confirmed == SLOT_ACTION) {
						AuctionHouse.getInstance().cancelListing(player, listing);
						return;
					}
					confirming = SLOT_ACTION;
				} else if (listing.getOffer(player.getUniqueId()) != null) {
					AuctionHouse.getInstance().withdrawOffer(player, listing);
					return;
				} else {
					openOfferPicker(player, listing, back);
					return;
				}
			}
			case SLOT_ADMIN -> {
				if (listing.isSeller(player) || !player.hasPermission(AuctionHouse.ADMIN_PERMISSION))
					return;
				if (confirmed == SLOT_ADMIN) {
					AuctionHouse.getInstance().cancelListing(player, listing);
					return;
				}
				confirming = SLOT_ADMIN;
			}
			default -> {
			}
		}
		render();
	}

	private static void openOfferPicker(Player player, AuctionListing listing, Runnable back) {
		String problem = AuctionHouse.whyCantUse(player);
		if (problem != null) {
			ColorUtil.sendMessage(player, problem);
			return;
		}
		List<String> notes = new ArrayList<>();
		notes.add("&7For &f" + listing.getSellerName() + "&7's items.");
		if (listing.getWants() != null)
			notes.add("&7They want: &f" + listing.getWants());
		new ItemPickerMenu(player, "&8Your offer", "Send offer", notes,
				selection -> AuctionHouse.getInstance().makeOffer(player, listing, selection), back).displayTo(player);
	}
}
