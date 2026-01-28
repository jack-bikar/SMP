package games.coob.smp.menu;

import games.coob.smp.PlayerCache;
import games.coob.smp.tracking.TrackingRequestManager;
import games.coob.smp.util.ItemCreator;
import games.coob.smp.util.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Menu for selecting players to track
 */
public class LocatorPlayersMenu extends SimpleMenu {

	private final List<Player> players;
	private int currentPage = 0;
	private static final int ITEMS_PER_PAGE = 27;

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

		// Add player heads
		for (int i = startIndex; i < endIndex; i++) {
			Player player = players.get(i);
			int slot = i - startIndex;
			inventory.setItem(slot, createPlayerItem(player));
		}

		// Back button (bottom-left)
		inventory.setItem(27, ItemCreator.of(
				Material.ARROW,
				"&c&lBack",
				"",
				"&7Return to player tracker menu.").make());

		// Navigation buttons
		if (currentPage > 0) {
			inventory.setItem(28, ItemCreator.of(
					Material.ARROW,
					"&a&lPrevious Page",
					"",
					"&7Go to the previous page.").make());
		}
		if (endIndex < players.size()) {
			inventory.setItem(34, ItemCreator.of(
					Material.ARROW,
					"&a&lNext Page",
					"",
					"&7Go to the next page.").make());
		}
	}

	private ItemStack createPlayerItem(Player player) {
		return ItemCreator.of(
				Material.PLAYER_HEAD,
				"&b&l" + player.getName(),
				"",
				"&7Click to track",
				"&7this player's location.",
				"",
				"&eClick to track")
				.skullOwner(player.getName()).make();
	}

	@Override
	protected void onMenuClick(Player viewer, int slot, ItemStack clicked) {
		if (clicked == null)
			return;

		// Handle back button
		if (clicked.getType() == Material.ARROW && slot == 27) {
			LocatorMenu.openMenu(viewer);
			return;
		}

		// Handle navigation
		if (clicked.getType() == Material.ARROW) {
			if (slot == 28 && currentPage > 0) {
				currentPage--;
				setupItems();
			} else if (slot == 34) {
				int startIndex = (currentPage + 1) * ITEMS_PER_PAGE;
				if (startIndex < players.size()) {
					currentPage++;
					setupItems();
				}
			}
			return;
		}

		// Handle player selection
		if (clicked.getType() == Material.PLAYER_HEAD) {
			int playerIndex = currentPage * ITEMS_PER_PAGE + slot;
			if (playerIndex < players.size()) {
				Player clickedPlayer = players.get(playerIndex);
				handlePlayerSelection(viewer, clickedPlayer);
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
