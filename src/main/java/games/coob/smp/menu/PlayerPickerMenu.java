package games.coob.smp.menu;

import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.ItemCreator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Paginated list of online players to pick from (duel challenges, team invites).
 * By default picking is left to the callback (which usually closes the menu);
 * {@link #stayOpen} keeps the list open to pick several players in a row.
 */
public final class PlayerPickerMenu extends SimpleMenu {

	private static final int PER_PAGE = 45;
	private static final int SLOT_BACK = 45;
	private static final int SLOT_PREVIOUS = 48;
	private static final int SLOT_NEXT = 50;
	private static final int SLOT_PICK_ALL = 53;

	/** Stored by id and looked up on click, so relogged players are handled correctly. */
	private final List<UUID> players = new ArrayList<>();
	private final List<String> names = new ArrayList<>();
	private final Predicate<Player> filter;
	private final String action;
	private final Consumer<Player> onPick;
	private final Runnable onBack;
	private int page;
	/** Set by {@link #stayOpen}: a line shown under players already picked, or null. */
	private Function<Player, String> status;
	private String pickAllLabel;
	/** Stops "pick everyone" early (e.g. the lobby is full). */
	private BooleanSupplier noRoom;

	/**
	 * @param filter which online players to list (the viewer is always left out)
	 * @param action what clicking does, shown on each head (e.g. "Click to invite")
	 * @param onPick called with the chosen player (still online)
	 * @param onBack called by the back button, or null for no back button
	 */
	public PlayerPickerMenu(Player viewer, String title, Predicate<Player> filter, String action,
			Consumer<Player> onPick, Runnable onBack) {
		super(viewer, 54, title);
		this.filter = filter;
		this.action = action;
		this.onPick = onPick;
		this.onBack = onBack;
		loadPlayers();
		render();
	}

	/**
	 * Keeps the list open after each pick, to pick several players in a row
	 * (team invites). It is refreshed after every pick.
	 *
	 * @param status a line shown under a player already picked (e.g. "Invited"), or null for the others
	 * @param pickAllLabel name of a button that picks everyone listed who has no status yet, or null for none
	 * @param noRoom       whether there is no room for more picks (stops "pick everyone"), or null
	 */
	public PlayerPickerMenu stayOpen(Function<Player, String> status, String pickAllLabel, BooleanSupplier noRoom) {
		this.status = status;
		this.pickAllLabel = pickAllLabel;
		this.noRoom = noRoom;
		render();
		return this;
	}

	private void loadPlayers() {
		players.clear();
		names.clear();
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (!player.equals(viewer) && filter.test(player)) {
				players.add(player.getUniqueId());
				names.add(player.getName());
			}
		}
		page = Math.min(page, Math.max(0, (players.size() - 1) / PER_PAGE));
	}

	private void render() {
		inventory.clear();
		int start = page * PER_PAGE;
		for (int i = start; i < Math.min(start + PER_PAGE, players.size()); i++) {
			Player player = Bukkit.getPlayer(players.get(i));
			String line = status != null && player != null ? status.apply(player) : null;
			inventory.setItem(i - start, ItemCreator.of(Material.PLAYER_HEAD, (line != null ? "&a&l" : "&b&l") + names.get(i), "",
					line != null ? line : "&e" + action).skullOwner(Bukkit.getOfflinePlayer(players.get(i))).make());
		}
		if (pickAllLabel != null && !players.isEmpty())
			inventory.setItem(SLOT_PICK_ALL, ItemCreator.of(Material.WRITABLE_BOOK, "&a&l" + pickAllLabel,
					"", "&7Everyone listed above who", "&7hasn't been picked yet.", "", "&eClick").make());

		if (players.isEmpty())
			inventory.setItem(22, ItemCreator.of(Material.BARRIER, "&cNobody available",
					"", "&7No other online players can be picked right now.").make());

		if (onBack != null)
			inventory.setItem(SLOT_BACK, ItemCreator.of(Material.ARROW, "&c&lBack").make());
		if (page > 0)
			inventory.setItem(SLOT_PREVIOUS, ItemCreator.of(Material.ARROW, "&a&lPrevious page").make());
		if (start + PER_PAGE < players.size())
			inventory.setItem(SLOT_NEXT, ItemCreator.of(Material.ARROW, "&a&lNext page").make());
	}

	@Override
	protected void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
		if (slot == SLOT_BACK && onBack != null) {
			onBack.run();
		} else if (slot == SLOT_PICK_ALL && pickAllLabel != null) {
			for (UUID id : new ArrayList<>(players)) {
				if (noRoom != null && noRoom.getAsBoolean()) {
					ColorUtil.sendMessage(player, "&eThere is no room for more players.");
					break;
				}
				Player target = Bukkit.getPlayer(id);
				if (target != null && status.apply(target) == null)
					onPick.accept(target);
			}
			refreshIfOpen(player);
		} else if (slot == SLOT_PREVIOUS && page > 0) {
			page--;
			render();
		} else if (slot == SLOT_NEXT && (page + 1) * PER_PAGE < players.size()) {
			page++;
			render();
		} else if (slot < PER_PAGE) {
			int index = page * PER_PAGE + slot;
			if (index >= players.size())
				return;
			Player picked = Bukkit.getPlayer(players.get(index));
			if (picked == null) {
				ColorUtil.sendMessage(player, "&c" + names.get(index) + " is no longer online.");
				return;
			}
			onPick.accept(picked);
			refreshIfOpen(player);
		}
	}

	/** In {@link #stayOpen} mode: the list again, with the new picks, unless the callback moved on. */
	private void refreshIfOpen(Player player) {
		if (status == null || player.getOpenInventory().getTopInventory() != inventory)
			return;
		loadPlayers();
		render();
	}
}
