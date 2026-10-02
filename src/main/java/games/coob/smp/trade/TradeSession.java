package games.coob.smp.trade;

import games.coob.smp.SMPPlugin;
import games.coob.smp.auction.AuctionHouse;
import games.coob.smp.auction.CollectionBox;
import games.coob.smp.menu.TradeMenu;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.ItemSelection;
import games.coob.smp.util.ItemText;
import games.coob.smp.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * A trade between two players. Each one picks items from their own inventory
 * (they stay there until the swap) and clicks Ready; when both are ready, a
 * short countdown runs and the items are swapped in one go. Any change to
 * either offer takes back both Ready clicks, so nobody can swap an item out
 * at the last second.
 */
public final class TradeSession {

	/** Stacks each player can put in (the menu shows 4 columns of 5 rows). */
	public static final int OFFER_STACKS = 20;

	/** One of the two players. */
	private static final class Side {
		private final UUID id;
		private final String name;
		private final ItemSelection offer = new ItemSelection(OFFER_STACKS);
		private boolean ready;
		private TradeMenu menu;
		private boolean menuOpen;

		private Side(Player player) {
			this.id = player.getUniqueId();
			this.name = player.getName();
		}
	}

	private final Side first;
	private final Side second;
	private @Nullable BukkitTask countdown;
	/** Seconds until the swap while counting down, otherwise 0. */
	private int secondsLeft;
	private boolean ended;

	TradeSession(Player first, Player second) {
		this.first = new Side(first);
		this.second = new Side(second);
	}

	void open() {
		for (Side side : new Side[] { first, second }) {
			Player player = Bukkit.getPlayer(side.id);
			side.menu = new TradeMenu(player, this);
			side.menu.displayTo(player);
			// Another plugin can stop a menu from opening; the trade can't go on without it
			side.menuOpen = player.getOpenInventory().getTopInventory() == side.menu.getInventory();
			if (!side.menuOpen) {
				cancel("&cThe trade was cancelled: the trade window couldn't be opened for " + side.name + ".");
				return;
			}
		}
	}

	private Side side(Player player) {
		return first.id.equals(player.getUniqueId()) ? first : second;
	}

	private Side other(Side side) {
		return side == first ? second : first;
	}

	public List<ItemStack> getOffer(Player player) {
		return side(player).offer.items();
	}

	public List<ItemStack> getPartnerOffer(Player player) {
		return other(side(player)).offer.items();
	}

	public String getPartnerName(Player player) {
		return other(side(player)).name;
	}

	public boolean isReady(Player player) {
		return side(player).ready;
	}

	public boolean isPartnerReady(Player player) {
		return other(side(player)).ready;
	}

	/** Seconds until the swap while both are ready, otherwise 0. */
	public int getSecondsLeft() {
		return secondsLeft;
	}

	public void addItem(Player player, int inventorySlot, boolean all) {
		if (!ended && side(player).offer.add(player, inventorySlot, all))
			offerChanged();
	}

	public void removeItem(Player player, int index, boolean all) {
		if (!ended && side(player).offer.remove(index, all))
			offerChanged();
	}

	public void toggleReady(Player player) {
		if (ended)
			return;
		Side side = side(player);
		if (!side.ready) {
			if (first.offer.isEmpty() && second.offer.isEmpty()) {
				ColorUtil.sendMessage(player, "&cNeither of you has put anything in yet.");
				return;
			}
			if (side.offer.prune(player.getInventory())) {
				ColorUtil.sendMessage(player, "&eSome of the items you put in are no longer in your inventory; check your offer again.");
				offerChanged();
				return;
			}
		}

		side.ready = !side.ready;
		if (first.ready && second.ready)
			startCountdown();
		else
			stopCountdown();
		render();
	}

	/** A player's trade window closed (the cancel button, Esc, or anything else). */
	public void menuClosed(Player player) {
		side(player).menuOpen = false;
		cancel("&cThe trade was cancelled: " + player.getName() + " closed it.");
	}

	/** Ends the trade without swapping anything; nothing has moved yet, so nothing needs giving back. */
	public void cancel(String message) {
		if (ended)
			return;
		ended = true;
		stopCountdown();
		TradeManager.getInstance().forget(this);
		for (Side side : new Side[] { first, second }) {
			Player player = Bukkit.getPlayer(side.id);
			if (player == null)
				continue;
			closeMenu(player, side);
			ColorUtil.sendMessage(player, message);
		}
	}

	/** Both Ready clicks are taken back, and whoever was ready hears it. */
	private void offerChanged() {
		for (Side side : new Side[] { first, second }) {
			Player player = side.ready ? Bukkit.getPlayer(side.id) : null;
			if (player != null)
				player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 1.0f);
			side.ready = false;
		}
		stopCountdown();
		render();
	}

	private void startCountdown() {
		stopCountdown();
		secondsLeft = Settings.TradeSection.CONFIRM_SECONDS;
		// From the scheduler, never inside a click: items aren't moved during an inventory event
		countdown = SchedulerUtil.runTimer(secondsLeft > 0 ? 20 : 1, 20, () -> {
			if (--secondsLeft > 0) {
				render();
				return;
			}
			stopCountdown();
			complete();
		});
	}

	private void stopCountdown() {
		if (countdown != null) {
			countdown.cancel();
			countdown = null;
		}
		secondsLeft = 0;
	}

	private void complete() {
		Player one = Bukkit.getPlayer(first.id);
		Player two = Bukkit.getPlayer(second.id);
		if (ended || one == null || two == null)
			return;

		String problem = TradeManager.whyCantTrade(one, two);
		if (problem != null) {
			cancel(problem);
			return;
		}
		// Items that left an inventory during the countdown: show the new offer and start over
		boolean changed = first.offer.prune(one.getInventory()) | second.offer.prune(two.getInventory());
		if (changed) {
			offerChanged();
			message("&eAn offer changed because items left an inventory; check it again.");
			return;
		}
		List<ItemStack> fromOne = first.offer.items();
		List<ItemStack> fromTwo = second.offer.items();
		boolean oneHasRoom = ItemSelection.fits(one.getInventory(), first.offer, fromTwo);
		if (!oneHasRoom || !ItemSelection.fits(two.getInventory(), second.offer, fromOne)) {
			offerChanged();
			message("&c" + (oneHasRoom ? two.getName() : one.getName())
					+ " doesn't have room for the items. Make some space and click Ready again.");
			return;
		}

		ended = true;
		TradeManager.getInstance().forget(this);
		fromOne = first.offer.take(one.getInventory());
		fromTwo = second.offer.take(two.getInventory());
		// Each player both gives and gets, so no order of writing their two files is safe on its own.
		// The items wait in the collection boxes while the files are written: a crash at any point
		// leaves them in two places, never in none.
		CollectionBox boxOne = CollectionBox.of(first.id);
		CollectionBox boxTwo = CollectionBox.of(second.id);
		boxOne.add(fromTwo);
		boxTwo.add(fromOne);
		one.saveData();
		two.saveData();
		int leftOne = boxOne.takeLast(one, fromTwo.size());
		int leftTwo = boxTwo.takeLast(two, fromOne.size());

		closeMenu(one, first);
		closeMenu(two, second);
		sendSummary(one, two.getName(), fromOne, fromTwo, leftOne);
		sendSummary(two, one.getName(), fromTwo, fromOne, leftTwo);
		SMPPlugin.getInstance().getLogger().info("[Trade] " + one.getName() + " gave " + two.getName() + ": "
				+ ItemText.plain(fromOne) + "; " + two.getName() + " gave " + one.getName() + ": " + ItemText.plain(fromTwo));
	}

	/** @param leftOver stacks that didn't fit after all (the room check came first) and wait in the collection box */
	private static void sendSummary(Player player, String partner, List<ItemStack> gave, List<ItemStack> got, int leftOver) {
		ColorUtil.sendMessage(player, "&aTrade with &e" + partner + " &acomplete.");
		player.sendMessage(ColorUtil.toComponent("&7You gave: ").append(ItemText.list(gave)));
		player.sendMessage(ColorUtil.toComponent("&7You got: ").append(ItemText.list(got)));
		if (leftOver > 0)
			player.sendMessage(ColorUtil.toComponent("&eNot everything fit; the rest is in your collection box. ")
					.append(AuctionHouse.collectButton()));
		player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
	}

	private void message(String message) {
		for (Side side : new Side[] { first, second }) {
			Player player = Bukkit.getPlayer(side.id);
			if (player != null)
				ColorUtil.sendMessage(player, message);
		}
	}

	private void closeMenu(Player player, Side side) {
		if (side.menuOpen && player.getOpenInventory().getTopInventory() == side.menu.getInventory())
			player.closeInventory();
		side.menuOpen = false;
	}

	private void render() {
		for (Side side : new Side[] { first, second }) {
			if (side.menuOpen)
				side.menu.render();
		}
	}
}
