package games.coob.smp.auction;

import games.coob.smp.PlayerCache;
import games.coob.smp.SMPPlugin;
import games.coob.smp.combat.CombatTracker;
import games.coob.smp.config.ConfigFile;
import games.coob.smp.duel.DuelManager;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.InventorySerialization;
import games.coob.smp.util.ItemSelection;
import games.coob.smp.util.ItemText;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * The auction house: every listing and the offers made on it, in
 * auction-house.yml. There is no money: sellers put items up, anyone else
 * offers items for them, and the seller takes the offer they like best.
 * Everything put up or offered is held here until it goes to someone's
 * {@link CollectionBox}.
 * <p>
 * Items move between files (a player's data, this file, collection boxes)
 * that can't be written in one go. Each change is written right away, the
 * side receiving the items first, so a crash in the moment between two
 * writes leaves items in both places rather than in neither. When the
 * receiving side can't be written, the giving side isn't written either.
 */
public final class AuctionHouse extends ConfigFile {

	@Getter
	private static final AuctionHouse instance = new AuctionHouse();

	/** Most stacks in one listing or offer: the menus show 3 rows of items. */
	public static final int MAX_STACKS = 27;
	public static final String ADMIN_PERMISSION = "smp.admin.auction";
	private static final int MAX_WANTS_LENGTH = 60;
	private static final String ID_CHARS = "abcdefghjkmnpqrstuvwxyz23456789";
	/** Taking a listing down and putting it up again can't flood the chat. */
	private static final long ANNOUNCE_COOLDOWN_MILLIS = 5 * 60_000L;

	/** Oldest first. Set in onLoad, which runs (from the super constructor) before field initialisers would. */
	private Map<String, AuctionListing> listings;
	/** Listings that couldn't be read: left as they are in the file, so an admin can still get the items back. */
	private Set<String> unreadable;
	/** Seller -> when a listing of theirs was last announced. */
	private final Map<UUID, Long> lastAnnounced = new HashMap<>();

	private AuctionHouse() {
		super("auction-house.yml");
	}

	@Override
	protected void onLoad() {
		listings = new LinkedHashMap<>();
		unreadable = new HashSet<>();
		ConfigurationSection section = getConfig().getConfigurationSection("Listings");
		if (section == null)
			return;
		for (String id : section.getKeys(false)) {
			try {
				ConfigurationSection data = Objects.requireNonNull(section.getConfigurationSection(id));
				List<ItemStack> items = decode(data.getString("Items"));
				if (items.isEmpty())
					throw new IllegalStateException("no items could be read");
				AuctionListing listing = new AuctionListing(id, UUID.fromString(data.getString("Seller")),
						data.getString("Seller_Name", "?"), items, data.getString("Wants"),
						data.getLong("Created"), data.getLong("Ends"));
				ConfigurationSection offers = data.getConfigurationSection("Offers");
				if (offers != null) {
					for (String bidder : offers.getKeys(false)) {
						ConfigurationSection offer = Objects.requireNonNull(offers.getConfigurationSection(bidder));
						listing.addOffer(new AuctionOffer(UUID.fromString(bidder), offer.getString("Name", "?"),
								decode(offer.getString("Items")), offer.getLong("Made")));
					}
				}
				listings.put(id, listing);
			} catch (RuntimeException e) {
				unreadable.add(id);
				SMPPlugin.getInstance().getLogger().log(Level.WARNING, "Could not read auction listing " + id
						+ "; it is left as it is in auction-house.yml", e);
			}
		}
	}

	@Override
	protected void onSave() {
		ConfigurationSection section = getConfig().getConfigurationSection("Listings");
		if (section == null)
			section = getConfig().createSection("Listings");
		for (String id : section.getKeys(false)) {
			if (!listings.containsKey(id) && !unreadable.contains(id))
				section.set(id, null);
		}
		for (AuctionListing listing : listings.values()) {
			ConfigurationSection data = section.createSection(listing.getId());
			data.set("Seller", listing.getSellerId().toString());
			data.set("Seller_Name", listing.getSellerName());
			data.set("Wants", listing.getWants());
			data.set("Created", listing.getCreatedAt());
			data.set("Ends", listing.getEndsAt());
			data.set("Items", encode(listing.getItems()));
			for (AuctionOffer offer : listing.getOffers()) {
				ConfigurationSection offerData = data.createSection("Offers." + offer.bidderId());
				offerData.set("Name", offer.bidderName());
				offerData.set("Made", offer.madeAt());
				offerData.set("Items", encode(offer.items()));
			}
		}
	}

	private static List<ItemStack> decode(@Nullable String data) {
		List<ItemStack> items = new ArrayList<>();
		for (ItemStack item : InventorySerialization.fromBase64(Objects.requireNonNull(data, "no items saved"))) {
			if (item != null && !item.isEmpty())
				items.add(item);
		}
		return items;
	}

	private static String encode(List<ItemStack> items) {
		return InventorySerialization.toBase64(items.toArray(new ItemStack[0]));
	}

	// -------------------------------------------------------------------------
	// Looking things up
	// -------------------------------------------------------------------------

	/** Newest first. */
	public List<AuctionListing> getListings() {
		return new ArrayList<>(listings.values()).reversed();
	}

	public @Nullable AuctionListing get(String id) {
		return listings.get(id);
	}

	public int countListings(UUID sellerId) {
		int count = 0;
		for (AuctionListing listing : listings.values()) {
			if (listing.getSellerId().equals(sellerId))
				count++;
		}
		return count;
	}

	/** Offers waiting on the seller's listings. */
	public int countOffersFor(UUID sellerId) {
		int count = 0;
		for (AuctionListing listing : listings.values()) {
			if (listing.getSellerId().equals(sellerId))
				count += listing.getOffers().size();
		}
		return count;
	}

	/** Whether the listing is still up and taking offers. */
	private boolean isOpen(AuctionListing listing) {
		return listings.get(listing.getId()) == listing && !listing.hasEnded(System.currentTimeMillis());
	}

	// -------------------------------------------------------------------------
	// Rules
	// -------------------------------------------------------------------------

	/** Why the player can't move items in or out of the auction house right now, or null. */
	public static @Nullable String whyCantUse(Player player) {
		// A duel kit replaces the inventory and is swapped back afterwards: anything given now would be lost
		if (DuelManager.getInstance().isInDuel(player))
			return "&cYou can't use the auction house during a duel.";
		// Putting items away mid-fight would keep them from the other player
		if (CombatTracker.isInCombat(player))
			return "&cYou can't use the auction house while in combat.";
		// Their own gear comes back from a kit duel after they respawn, replacing the whole inventory
		if (player.isDead())
			return "&cYou can't use the auction house while dead.";
		if (PlayerCache.from(player).hasKitStash())
			return "&cYour own items haven't come back from your last kit duel yet; the auction house has to wait.";
		return null;
	}

	/** Why the player can't put up a new listing right now, or null. */
	public @Nullable String whyCantSell(Player player) {
		if (!Settings.AuctionSection.ENABLED)
			return "&cThe auction house is closed.";
		String problem = whyCantUse(player);
		if (problem != null)
			return problem;
		int max = Settings.AuctionSection.MAX_LISTINGS_PER_PLAYER;
		if (countListings(player.getUniqueId()) >= max)
			return "&cYou already have " + max + " listing" + (max == 1 ? "" : "s") + " up. Cancel one or wait for one to end.";
		return null;
	}

	/** The seller's note without colour codes, cut to length, or null if there is nothing left. */
	public static @Nullable String cleanWants(@Nullable String text) {
		if (text == null)
			return null;
		// Until nothing changes: removing "&a" from "&&aa" would leave a new "&a"
		String clean = text;
		String previous;
		do {
			previous = clean;
			clean = clean.replaceAll("(?i)[&§](#[0-9a-f]{6}|[0-9a-fk-or])", "").replace("§", "");
		} while (!clean.equals(previous));
		clean = clean.strip();
		if (clean.isEmpty())
			return null;
		return clean.length() > MAX_WANTS_LENGTH ? clean.substring(0, MAX_WANTS_LENGTH) : clean;
	}

	// -------------------------------------------------------------------------
	// Changes
	// -------------------------------------------------------------------------

	/**
	 * Puts the picked items up for auction.
	 *
	 * @return whether they were put up (if not, the player was told why)
	 */
	public boolean list(Player seller, ItemSelection selection, @Nullable String wants) {
		String problem = whyCantSell(seller);
		if (problem == null && selection.isEmpty())
			problem = "&cPick the items to put up first.";
		if (problem == null && selection.prune(seller.getInventory()))
			problem = "&eSome of those items are no longer in your inventory; check them again.";
		if (problem != null) {
			ColorUtil.sendMessage(seller, problem);
			return false;
		}

		List<ItemStack> items = selection.take(seller.getInventory());
		long now = System.currentTimeMillis();
		AuctionListing listing = new AuctionListing(newId(), seller.getUniqueId(), seller.getName(), items,
				wants, now, now + Settings.AuctionSection.LISTING_MILLIS);
		listings.put(listing.getId(), listing);
		if (!saveNow()) {
			listings.remove(listing.getId());
			giveStraightBack(seller, items);
			return false;
		}
		seller.saveData();

		ColorUtil.sendMessage(seller, "&aYour items are up for auction for "
				+ formatTime(Settings.AuctionSection.LISTING_MILLIS) + ". You'll hear when someone makes an offer.");
		Long announced = lastAnnounced.get(seller.getUniqueId());
		if (Settings.AuctionSection.ANNOUNCE_NEW_LISTINGS && (announced == null || now - announced >= ANNOUNCE_COOLDOWN_MILLIS)) {
			lastAnnounced.put(seller.getUniqueId(), now);
			Component announcement = ColorUtil.toComponent("&6[Auction] &e" + seller.getName() + " &7put up ")
					.append(summary(items)).append(Component.text(" "))
					.append(button("[View]", "/auction view " + listing.getId(), "Click to see the listing"));
			for (Player online : Bukkit.getOnlinePlayers()) {
				if (!online.equals(seller))
					online.sendMessage(announcement);
			}
		}
		log(seller.getName() + " put up #" + listing.getId() + ": " + ItemText.plain(items)
				+ (wants != null ? " (wants: " + wants + ")" : ""));
		refreshMenus();
		return true;
	}

	/**
	 * Offers the picked items for a listing; they are held until the seller
	 * decides or the bidder withdraws the offer.
	 *
	 * @return whether the offer was made (if not, the player was told why)
	 */
	public boolean makeOffer(Player bidder, AuctionListing listing, ItemSelection selection) {
		String problem = Settings.AuctionSection.ENABLED ? whyCantUse(bidder) : "&cThe auction house is closed.";
		if (problem == null && !isOpen(listing))
			problem = "&cThat listing has ended.";
		if (problem == null && listing.isSeller(bidder))
			problem = "&cYou can't make an offer on your own listing.";
		if (problem == null && listing.getOffer(bidder.getUniqueId()) != null)
			problem = "&cYou already made an offer on this listing. Withdraw it first to make a new one.";
		if (problem == null && selection.isEmpty())
			problem = "&cPick the items to offer first.";
		if (problem == null && selection.prune(bidder.getInventory()))
			problem = "&eSome of those items are no longer in your inventory; check them again.";
		if (problem != null) {
			ColorUtil.sendMessage(bidder, problem);
			return false;
		}

		List<ItemStack> items = selection.take(bidder.getInventory());
		listing.addOffer(new AuctionOffer(bidder.getUniqueId(), bidder.getName(), items, System.currentTimeMillis()));
		if (!saveNow()) {
			listing.removeOffer(bidder.getUniqueId());
			giveStraightBack(bidder, items);
			return false;
		}
		bidder.saveData();

		ColorUtil.sendMessage(bidder, "&aOffer sent to &e" + listing.getSellerName()
				+ "&a. Your items are held until they decide, or until you withdraw it.");
		notify(listing.getSellerId(), ColorUtil.toComponent("&6[Auction] &e" + bidder.getName() + " &7offered ")
				.append(summary(items)).append(ColorUtil.toComponent(" &7for your listing. "))
				.append(button("[View]", "/auction view " + listing.getId(), "Click to see the offers")));
		log(bidder.getName() + " offered on #" + listing.getId() + ": " + ItemText.plain(items));
		refreshMenus();
		return true;
	}

	/** Gives a bidder their offered items back. */
	public void withdrawOffer(Player bidder, AuctionListing listing) {
		String problem = whyCantUse(bidder);
		AuctionOffer offer = listings.get(listing.getId()) == listing ? listing.getOffer(bidder.getUniqueId()) : null;
		if (problem == null && offer == null)
			problem = "&cYou have no offer on that listing any more.";
		if (problem != null) {
			ColorUtil.sendMessage(bidder, problem);
			return;
		}

		listing.removeOffer(bidder.getUniqueId());
		saveAfterDelivery(deliver(bidder.getUniqueId(), offer.items()));

		log(bidder.getName() + " withdrew their offer on #" + listing.getId());
		notify(listing.getSellerId(), ColorUtil.toComponent("&6[Auction] &e" + bidder.getName()
				+ " &7withdrew their offer on your listing."));
		giveBack(bidder, "&eYou withdrew your offer.");
		refreshMenus();
	}

	/**
	 * The seller takes an offer: they get the offered items, the bidder gets
	 * the listing, and every other offer goes back.
	 *
	 * @return whether the offer was taken (if not, the seller was told why)
	 */
	public boolean acceptOffer(Player seller, AuctionListing listing, UUID bidderId) {
		String problem = listing.isSeller(seller) ? whyCantUse(seller) : "&cOnly the seller can take an offer.";
		AuctionOffer offer = listings.get(listing.getId()) == listing ? listing.getOffer(bidderId) : null;
		if (problem == null && offer == null)
			problem = "&cThat offer isn't there any more.";
		if (problem != null) {
			ColorUtil.sendMessage(seller, problem);
			return false;
		}

		saveAfterDelivery(close(listing, offer));
		log(seller.getName() + " took " + offer.bidderName() + "'s offer on #" + listing.getId() + ": gave "
				+ ItemText.plain(listing.getItems()) + ", got " + ItemText.plain(offer.items()));
		notify(bidderId, ColorUtil.toComponent("&6[Auction] &e" + seller.getName()
				+ " &atook your offer! &7What you bid for is in your collection box. ").append(collectButton()));
		for (AuctionOffer other : listing.getOffers()) {
			if (!other.bidderId().equals(bidderId))
				notify(other.bidderId(), ColorUtil.toComponent("&6[Auction] &e" + seller.getName()
						+ " &7took another offer, so yours is back in your collection box. ").append(collectButton()));
		}
		giveBack(seller, "&aYou took &e" + offer.bidderName() + "&a's offer.");
		refreshMenus();
		return true;
	}

	/** The seller (or an admin) takes a listing down; everything goes back. */
	public void cancelListing(Player actor, AuctionListing listing) {
		boolean seller = listing.isSeller(actor);
		if (!seller && !actor.hasPermission(ADMIN_PERMISSION))
			return;
		String problem = seller ? whyCantUse(actor) : null;
		if (problem == null && listings.get(listing.getId()) != listing)
			problem = "&cThat listing has already ended.";
		if (problem != null) {
			ColorUtil.sendMessage(actor, problem);
			return;
		}

		saveAfterDelivery(close(listing, null));
		log(actor.getName() + (seller ? "" : " (admin)") + " took down #" + listing.getId());
		for (AuctionOffer offer : listing.getOffers())
			notify(offer.bidderId(), ColorUtil.toComponent("&6[Auction] &7" + listing.getSellerName()
					+ "'s listing was taken down; your offer is back in your collection box. ").append(collectButton()));
		if (seller) {
			giveBack(actor, "&eYou took your listing down.");
		} else {
			notify(listing.getSellerId(), ColorUtil.toComponent(
					"&6[Auction] &7An admin took your listing down; the items are in your collection box. ").append(collectButton()));
			ColorUtil.sendMessage(actor, "&aListing taken down; everything went back to its owners.");
		}
		refreshMenus();
	}

	/** Ends listings that ran out of time. */
	public void tick() {
		long now = System.currentTimeMillis();
		boolean changed = false;
		boolean delivered = true;
		for (AuctionListing listing : new ArrayList<>(listings.values())) {
			if (!listing.hasEnded(now))
				continue;
			delivered &= close(listing, null);
			changed = true;
			log("#" + listing.getId() + " ran out of time");
			notify(listing.getSellerId(), ColorUtil.toComponent("&6[Auction] &7Your listing ran out of time without you taking an offer;"
					+ " the items are in your collection box. ").append(collectButton()));
			for (AuctionOffer offer : listing.getOffers())
				notify(offer.bidderId(), ColorUtil.toComponent("&6[Auction] &7" + listing.getSellerName()
						+ "'s listing ran out of time; your offer is back in your collection box. ").append(collectButton()));
		}
		if (changed) {
			saveAfterDelivery(delivered);
			refreshMenus();
		}
	}

	/**
	 * Takes a listing down: its items go to whoever made the taken offer (or
	 * back to the seller), the taken offer to the seller, every other offer
	 * back. This file is written afterwards, with {@link #saveAfterDelivery}.
	 *
	 * @return whether every box was written
	 */
	private boolean close(AuctionListing listing, @Nullable AuctionOffer taken) {
		listings.remove(listing.getId());
		boolean delivered = deliver(taken != null ? taken.bidderId() : listing.getSellerId(), listing.getItems());
		for (AuctionOffer offer : listing.getOffers()) {
			boolean isTaken = taken != null && offer.bidderId().equals(taken.bidderId());
			delivered &= deliver(isTaken ? listing.getSellerId() : offer.bidderId(), offer.items());
		}
		return delivered;
	}

	/** @return whether the box was written */
	private static boolean deliver(UUID playerId, List<ItemStack> items) {
		boolean written = CollectionBox.of(playerId).add(items);
		// Boxes of players who aren't on only need to be on disk (one that couldn't be written stays loaded)
		if (Bukkit.getPlayer(playerId) == null)
			CollectionBox.unload(playerId);
		return written;
	}

	/**
	 * Writes this file once the boxes have the items: a crash in between leaves
	 * them in both, never in neither. If a box couldn't be written, this file
	 * isn't either, so the items stay in it on disk while the box tries again.
	 */
	private void saveAfterDelivery(boolean delivered) {
		if (delivered)
			saveNow();
		else
			SMPPlugin.getInstance().getLogger().severe("A collection box couldn't be written, so auction-house.yml wasn't "
					+ "saved this time; the items are kept in memory and written again later.");
	}

	/** Items just taken from the player go back where they came from (they fit: their slots were just emptied). */
	private static void giveStraightBack(Player player, List<ItemStack> items) {
		for (ItemStack item : items) {
			for (ItemStack leftover : player.getInventory().addItem(item).values())
				player.getWorld().dropItemNaturally(player.getLocation(), leftover);
		}
		ColorUtil.sendMessage(player, "&cThe auction house couldn't be saved, so nothing changed and your items are back. Please tell an admin.");
	}

	/** Moves what fits from the player's collection box into their inventory, and tells them where things are. */
	private static void giveBack(Player player, String message) {
		int left = CollectionBox.of(player.getUniqueId()).takeAll(player);
		if (left == 0)
			ColorUtil.sendMessage(player, message + " &7The items are in your inventory.");
		else
			player.sendMessage(ColorUtil.toComponent(message + " &7Not everything fit; the rest is in your collection box. ")
					.append(collectButton()));
	}

	private String newId() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		String id;
		do {
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < 5; i++)
				builder.append(ID_CHARS.charAt(random.nextInt(ID_CHARS.length())));
			id = builder.toString();
		} while (listings.containsKey(id) || unreadable.contains(id));
		return id;
	}

	// -------------------------------------------------------------------------
	// Messages
	// -------------------------------------------------------------------------

	private static void notify(UUID playerId, Component message) {
		Player player = Bukkit.getPlayer(playerId);
		if (player != null)
			player.sendMessage(message);
	}

	/** "64x Diamond", or "64x Diamond and 3 more". */
	private static Component summary(List<ItemStack> items) {
		Component first = ItemText.describe(items.getFirst());
		return items.size() == 1 ? first : first.append(ColorUtil.toComponent(" &7and " + (items.size() - 1) + " more"));
	}

	public static Component button(String label, String command, String hover) {
		return Component.text(label, NamedTextColor.GREEN)
				.hoverEvent(HoverEvent.showText(Component.text(hover)))
				.clickEvent(ClickEvent.runCommand(command));
	}

	public static Component collectButton() {
		return button("[Collect]", "/auction collect", "Click to open your collection box");
	}

	/** "2d 4h", "3h 20m", "5m", or "less than a minute". */
	public static String formatTime(long millis) {
		long minutes = millis / 60_000;
		if (minutes < 1)
			return "less than a minute";
		long days = minutes / 1440;
		long hours = minutes / 60 % 24;
		if (days > 0)
			return days + "d " + hours + "h";
		if (hours > 0)
			return hours + "h " + minutes % 60 + "m";
		return minutes + "m";
	}

	/** Redraws every open auction menu, so nobody acts on something that changed. */
	private static void refreshMenus() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof AuctionView view)
				view.refresh();
		}
	}

	/** Who traded what, for settling disputes. */
	private static void log(String message) {
		SMPPlugin.getInstance().getLogger().info("[Auction] " + message);
	}
}
