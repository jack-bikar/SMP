package games.coob.smp.menu;

import games.coob.smp.PlayerCache;
import games.coob.smp.util.ItemCreator;
import games.coob.smp.util.Messenger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;

import java.util.List;

/**
 * Menu for selecting players to track
 */
public class CompassPlayersMenu extends SimpleMenu {

	private final List<Player> players;
	private int currentPage = 0;
	private static final int ITEMS_PER_PAGE = 27;

	public CompassPlayersMenu(final Player viewer) {
		super(viewer, 9 * 4, "&lSelect a player to track");
		this.players = compilePlayers(viewer);
		setupItems();
	}

	private static List<Player> compilePlayers(final Player viewer) {
		return viewer.getWorld().getPlayers();
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

		// Navigation buttons
		if (currentPage > 0) {
			inventory.setItem(27, ItemCreator.of(Material.ARROW, "&aPrevious Page").make());
		}
		if (endIndex < players.size()) {
			inventory.setItem(35, ItemCreator.of(Material.ARROW, "&aNext Page").make());
		}
	}

	private ItemStack createPlayerItem(Player player) {
		return ItemCreator.of(
				Material.PLAYER_HEAD,
				player.getName(),
				"",
				"Click to track",
				player.getName())
				.skullOwner(player.getName()).make();
	}

	@Override
	protected void onMenuClick(Player viewer, int slot, ItemStack clicked) {
		if (clicked == null) return;

		// Handle navigation
		if (clicked.getType() == Material.ARROW) {
			if (slot == 27 && currentPage > 0) {
				currentPage--;
				setupItems();
			} else if (slot == 35) {
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
		final PlayerCache cache = PlayerCache.from(viewer);
		final Location location = clickedPlayer.getLocation();
		location.setY(1);

		if (clickedPlayer.getWorld() == viewer.getWorld()) {
			if (viewer.getWorld().getEnvironment() == World.Environment.NORMAL) {
				viewer.setCompassTarget(clickedPlayer.getLocation());
			} else {
				updateCompassLodestone(viewer, location);
			}

			cache.setTrackingLocation("Player");
			cache.setTargetByUUID(clickedPlayer.getUniqueId());
			viewer.closeInventory();
			Messenger.success(viewer, "You are now tracking &3" + clickedPlayer.getName() + "'s &alocation.");
		} else {
			Messenger.info(viewer, "You must be in the same world as the player you want to track.");
		}
	}

	private void updateCompassLodestone(Player player, Location location) {
		if (player.getInventory().contains(Material.COMPASS)) {
			for (int i = 0; i <= player.getInventory().getSize(); i++) {
				final ItemStack item = player.getInventory().getItem(i);

				if (item != null && item.getType() == Material.COMPASS) {
					final CompassMeta compass = (CompassMeta) item.getItemMeta();
					compass.setLodestone(location);
					compass.setLodestoneTracked(false);
					item.setItemMeta(compass);
				}
			}
		}
	}

	/**
	 * Open the player selection menu to the given player
	 */
	public static void openMenu(final Player player) {
		new CompassPlayersMenu(player).displayTo(player);
	}
}
