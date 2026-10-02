package games.coob.smp.auction;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Items up for auction. Anyone else can make one offer of items for them; the
 * seller takes whichever offer they like, or the listing ends after a while
 * and everything goes back.
 */
@Getter
public final class AuctionListing {

	private final String id;
	private final UUID sellerId;
	private final String sellerName;
	@Getter(lombok.AccessLevel.NONE)
	private final List<ItemStack> items;
	/** What the seller is looking for, in their own words, or null. */
	private final @Nullable String wants;
	private final long createdAt;
	private final long endsAt;
	/** Bidder -> their offer, oldest first. */
	@Getter(lombok.AccessLevel.NONE)
	private final Map<UUID, AuctionOffer> offers = new LinkedHashMap<>();

	AuctionListing(String id, UUID sellerId, String sellerName, List<ItemStack> items, @Nullable String wants,
			long createdAt, long endsAt) {
		this.id = id;
		this.sellerId = sellerId;
		this.sellerName = sellerName;
		this.items = copy(items);
		this.wants = wants;
		this.createdAt = createdAt;
		this.endsAt = endsAt;
	}

	/** Copies, so a menu showing them can't change what is stored. */
	public List<ItemStack> getItems() {
		return copy(items);
	}

	public List<AuctionOffer> getOffers() {
		return new ArrayList<>(offers.values());
	}

	public @Nullable AuctionOffer getOffer(UUID bidderId) {
		return offers.get(bidderId);
	}

	void addOffer(AuctionOffer offer) {
		offers.put(offer.bidderId(), offer);
	}

	void removeOffer(UUID bidderId) {
		offers.remove(bidderId);
	}

	public boolean isSeller(Player player) {
		return sellerId.equals(player.getUniqueId());
	}

	public boolean hasEnded(long now) {
		return now >= endsAt;
	}

	static List<ItemStack> copy(List<ItemStack> items) {
		List<ItemStack> copy = new ArrayList<>(items.size());
		for (ItemStack item : items)
			copy.add(item.clone());
		return copy;
	}
}
