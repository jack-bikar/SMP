package games.coob.smp.menu;

import games.coob.smp.auction.AuctionHouse;
import games.coob.smp.auction.AuctionListing;
import games.coob.smp.auction.AuctionOffer;
import games.coob.smp.auction.AuctionView;
import games.coob.smp.util.ItemCreator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * One offer on a listing, item by item, so it can be looked at properly.
 * The seller takes it from here; whoever made it can withdraw it.
 */
public final class AuctionOfferMenu extends SimpleMenu implements AuctionView {

	private static final int SLOT_BACK = 27;
	private static final int SLOT_INFO = 31;
	private static final int SLOT_ACTION = 35;

	private final String listingId;
	private final UUID bidderId;
	private final Runnable onBack;
	/** The seller clicked "take" once; a second click goes ahead. */
	private boolean confirming;

	public AuctionOfferMenu(Player viewer, AuctionListing listing, UUID bidderId, Runnable onBack) {
		super(viewer, 36, "&8Offer on " + listing.getSellerName() + "'s listing");
		this.listingId = listing.getId();
		this.bidderId = bidderId;
		this.onBack = onBack;
		render();
	}

	private @Nullable AuctionListing listing() {
		return AuctionHouse.getInstance().get(listingId);
	}

	private void render() {
		inventory.clear();
		ItemStack filler = ItemCreator.of(Material.GRAY_STAINED_GLASS_PANE, " ").make();
		for (int slot = AuctionHouse.MAX_STACKS; slot < inventory.getSize(); slot++)
			inventory.setItem(slot, filler);
		inventory.setItem(SLOT_BACK, ItemCreator.of(Material.ARROW, "&c&lBack").make());

		AuctionListing listing = listing();
		AuctionOffer offer = listing != null ? listing.getOffer(bidderId) : null;
		if (offer == null) {
			inventory.setItem(13, ItemCreator.of(Material.BARRIER, "&cThis offer isn't there any more",
					"", "&7It was withdrawn or taken,", "&7or the listing ended.").make());
			return;
		}

		List<ItemStack> items = offer.items();
		for (int i = 0; i < items.size() && i < AuctionHouse.MAX_STACKS; i++)
			inventory.setItem(i, items.get(i));
		inventory.setItem(SLOT_INFO, ItemCreator.of(Material.PLAYER_HEAD, "&b&l" + offer.bidderName() + "'s offer",
				"&7For &f" + listing.getSellerName() + "&7's listing",
				"&7Made &f" + AuctionHouse.formatTime(System.currentTimeMillis() - offer.madeAt()) + " &7ago")
				.skullOwner(Bukkit.getOfflinePlayer(bidderId)).make());

		if (listing.isSeller(viewer))
			inventory.setItem(SLOT_ACTION, confirming
					? ItemCreator.of(Material.LIME_CONCRETE, "&a&lClick again to confirm",
							"", "&7You get these items,", "&f" + offer.bidderName() + " &7gets yours.").make()
					: ItemCreator.of(Material.LIME_DYE, "&a&lTake this offer",
							"", "&7You get these items,", "&f" + offer.bidderName() + " &7gets yours, and",
							"&7every other offer goes back.", "", "&eClick to take it").make());
		else if (viewer.getUniqueId().equals(bidderId))
			inventory.setItem(SLOT_ACTION, ItemCreator.of(Material.HOPPER, "&e&lWithdraw your offer",
					"", "&7Your items come back to you.").make());
	}

	@Override
	public void refresh() {
		confirming = false;
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
		AuctionListing listing = listing();
		if (slot != SLOT_ACTION || listing == null || listing.getOffer(bidderId) == null)
			return;

		if (listing.isSeller(player)) {
			// Taking an offer can't be undone: a second click makes sure it was meant
			if (!confirming) {
				confirming = true;
				render();
			} else if (AuctionHouse.getInstance().acceptOffer(player, listing, bidderId)) {
				player.closeInventory();
			} else {
				confirming = false;
				render();
			}
		} else if (player.getUniqueId().equals(bidderId)) {
			AuctionHouse.getInstance().withdrawOffer(player, listing);
			onBack.run();
		}
	}
}
