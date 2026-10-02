package games.coob.smp.auction;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Items a player offered for a listing, held by the auction house until the
 * seller takes the offer or it goes back.
 */
public record AuctionOffer(UUID bidderId, String bidderName, List<ItemStack> items, long madeAt) {

	public AuctionOffer {
		items = AuctionListing.copy(items);
	}

	/** Copies, so a menu showing them can't change what is stored. */
	@Override
	public List<ItemStack> items() {
		return AuctionListing.copy(items);
	}
}
