package games.coob.smp.menu;

import games.coob.smp.PlayerCache;
import games.coob.smp.task.LocatorTask;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.tracking.TrackingRegistry;
import games.coob.smp.tracking.TrackingRequestManager;
import games.coob.smp.tracking.WaypointPacketSender;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.ItemCreator;
import games.coob.smp.util.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Menu for selecting players to track. Paginated with 10 players per page.
 * Max 10 players can be tracked at once per player.
 */
public class LocatorPlayersMenu extends SimpleMenu {

	private final List<Player> players;
	private int currentPage = 0;
	private static final int ITEMS_PER_PAGE = 10;
	/** Slots 0-9 hold player heads; row 3 has back/nav. */
	private static final int PLAYER_SLOT_START = 0;
	private static final int PLAYER_SLOT_END = 9;
	private static final int SLOT_BACK = 27;
	private static final int SLOT_PREV = 28;
	private static final int SLOT_NEXT = 34;

	public LocatorPlayersMenu(final Player viewer) {
		super(viewer, 9 * 4, "&lSelect a player to track");
		this.players = compilePlayers(viewer);
		setupItems();
	}

	private static List<Player> compilePlayers(final Player viewer) {
		// Include players from ALL dimensions (worlds)
		return Bukkit.getOnlinePlayers().stream()
				.filter(player -> !player.equals(viewer))
				.map(player -> (Player) player)
				.toList();
	}

	private void setupItems() {
		inventory.clear();
		int startIndex = currentPage * ITEMS_PER_PAGE;
		int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, players.size());

		// Player heads in slots 0-9 only
		for (int i = startIndex; i < endIndex; i++) {
			Player player = players.get(i);
			int slot = PLAYER_SLOT_START + (i - startIndex);
			inventory.setItem(slot, createPlayerItem(player));
		}

		// Back button
		inventory.setItem(SLOT_BACK, ItemCreator.of(
				Material.ARROW,
				"&c&lBack",
				"",
				"&7Return to player tracker menu.").make());

		// Previous page
		if (currentPage > 0) {
			inventory.setItem(SLOT_PREV, ItemCreator.of(
					Material.ARROW,
					"&a&lPrevious Page",
					"",
					"&7Go to the previous page.").make());
		}
		// Next page
		if (endIndex < players.size()) {
			inventory.setItem(SLOT_NEXT, ItemCreator.of(
					Material.ARROW,
					"&a&lNext Page",
					"",
					"&7Go to the next page.").make());
		}
	}

	private ItemStack createPlayerItem(Player player) {
		PlayerCache cache = PlayerCache.from(viewer);
		boolean isTracking = cache.getTrackedTarget(player.getUniqueId()) != null;

		if (isTracking) {
			return ItemCreator.of(
					Material.PLAYER_HEAD,
					"&b&l" + player.getName(),
					"",
					"&aCurrently tracking",
					"&cRight-click to stop tracking",
					"",
					"&eLeft-click to request (already tracking)")
					.skullOwner(player.getName()).make();
		} else {
			return ItemCreator.of(
					Material.PLAYER_HEAD,
					"&b&l" + player.getName(),
					"",
					"&7Not tracking",
					"&eLeft-click to request tracking",
					"",
					"&eClick to track")
					.skullOwner(player.getName()).make();
		}
	}

	@Override
	protected void onMenuClick(Player viewer, int slot, ItemStack clicked, ClickType clickType) {
		if (clicked == null)
			return;

		// Handle back button
		if (clicked.getType() == Material.ARROW && slot == SLOT_BACK) {
			LocatorMenu.openMenu(viewer);
			return;
		}

		// Handle navigation
		if (clicked.getType() == Material.ARROW) {
			if (slot == SLOT_PREV && currentPage > 0) {
				currentPage--;
				setupItems();
			} else if (slot == SLOT_NEXT) {
				int startIndex = (currentPage + 1) * ITEMS_PER_PAGE;
				if (startIndex < players.size()) {
					currentPage++;
					setupItems();
				}
			}
			return;
		}

		// Handle player selection (only slots 0-9 are player heads)
		if (clicked.getType() == Material.PLAYER_HEAD && slot >= PLAYER_SLOT_START && slot <= PLAYER_SLOT_END) {
			int playerIndex = currentPage * ITEMS_PER_PAGE + (slot - PLAYER_SLOT_START);
			if (playerIndex < players.size()) {
				Player clickedPlayer = players.get(playerIndex);
				PlayerCache cache = PlayerCache.from(viewer);
				boolean isTracking = cache.getTrackedTarget(clickedPlayer.getUniqueId()) != null;

				if (clickType == ClickType.RIGHT && isTracking) {
					// Stop tracking this player
					cache.removeTrackedPlayer(clickedPlayer.getUniqueId());
					WaypointPacketSender.removeWaypoint(viewer,
							WaypointPacketSender.generateWaypointId(viewer.getUniqueId(), clickedPlayer.getUniqueId()));
					if (!cache.isTracking()) {
						TrackingRegistry.stopTracking(viewer.getUniqueId());
						LocatorBarManager.disableReceive(viewer);
						LocatorBarManager.clearTarget(viewer);
						LocatorTask.cleanupPlayer(viewer.getUniqueId());
						WaypointPacketSender.clearWaypoint(viewer);
					}
					ColorUtil.sendMessage(viewer, "&aStopped tracking &3" + clickedPlayer.getName() + "&a.");
					setupItems();
				} else {
					// Left-click: send tracking request (skip if already tracking)
					if (isTracking) {
						Messenger.info(viewer, "You are already tracking " + clickedPlayer.getName() + ".");
						return;
					}
					if (!cache.canTrackMorePlayers()) {
						ColorUtil.sendMessage(viewer, "&cYou can only track up to &e" + PlayerCache.MAX_PLAYERS_TO_TRACK
								+ " &cplayers. Stop tracking someone first.");
						return;
					}
					handlePlayerSelection(viewer, clickedPlayer);
				}
			}
		}
	}

	private void handlePlayerSelection(Player viewer, Player clickedPlayer) {
		if (viewer.equals(clickedPlayer)) {
			Messenger.info(viewer, "You cannot track yourself.");
			return;
		}

		// Allow tracking across dimensions - will point to portal if in different
		// dimension
		// Send tracking request
		TrackingRequestManager.getInstance().sendTrackingRequest(viewer, clickedPlayer);
		viewer.closeInventory();
	}

	/**
	 * Open the player selection menu to the given player
	 */
	public static void openMenu(final Player player) {
		new LocatorPlayersMenu(player).displayTo(player);
	}
}
