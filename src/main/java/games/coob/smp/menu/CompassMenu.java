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

public class CompassMenu extends SimpleMenu {

	private static final int PLAYER_BUTTON_SLOT = 9 + 2;
	private static final int BED_BUTTON_SLOT = 9 + 4;
	private static final int DEATH_BUTTON_SLOT = 9 + 6;

	public CompassMenu(final Player player) {
		super(player, 9 * 3, "&lCompass Menu");
		setupItems();
	}

	private void setupItems() {
		// Player selection button
		inventory.setItem(PLAYER_BUTTON_SLOT, ItemCreator.of(
				Material.PLAYER_HEAD,
				"Select a player to track",
				"",
				"Click here to",
				"open a player",
				"selection menu.").make());

		// Bed location button
		inventory.setItem(BED_BUTTON_SLOT, ItemCreator.of(
				Material.BLUE_BED,
				"&bTrack your bed location",
				"",
				"Click here to",
				"start tracking your",
				"bed spawn location.").make());

		// Death location button
		inventory.setItem(DEATH_BUTTON_SLOT, ItemCreator.of(
				Material.SKELETON_SKULL,
				"&cTrack your death location",
				"",
				"Click here to start",
				"tracking your",
				"previous death location.").make());
	}

	@Override
	protected void onMenuClick(Player player, int slot, ItemStack clicked) {
		if (slot == PLAYER_BUTTON_SLOT) {
			CompassPlayersMenu.openMenu(player);
		} else if (slot == BED_BUTTON_SLOT) {
			handleBedLocation(player);
		} else if (slot == DEATH_BUTTON_SLOT) {
			handleDeathLocation(player);
		}
	}

	private void handleBedLocation(Player player) {
		@SuppressWarnings("deprecation")
		Location bedLocation = player.getBedSpawnLocation();
		if (bedLocation != null) {
			if (bedLocation.getWorld() == player.getWorld()) {
				final Location location = bedLocation.clone();
				final PlayerCache cache = PlayerCache.from(player);
				location.setY(1);

				if (player.getWorld().getEnvironment() == World.Environment.NORMAL) {
					player.setCompassTarget(location);
				} else {
					updateCompassLodestone(player, location);
				}

				cache.setTrackingLocation("Bed");
				player.closeInventory();
				Messenger.success(player, "You are now tracking your bed spawn location.");
			} else {
				Messenger.info(player, "You must be in the same world as your bed spawn location to track it.");
			}
		} else {
			Messenger.info(player, "No bed location was found.");
		}
	}

	private void handleDeathLocation(Player player) {
		final PlayerCache cache = PlayerCache.from(player);

		if (cache.getDeathLocation() != null) {
			if (cache.getDeathLocation().getWorld() == player.getWorld()) {
				if (player.getWorld().getEnvironment() == World.Environment.NORMAL) {
					player.setCompassTarget(cache.getDeathLocation());
				} else {
					updateCompassLodestone(player, cache.getDeathLocation());
				}

				cache.setTrackingLocation("Death");
				player.closeInventory();
				Messenger.success(player, "You are now tracking your death location.");
			} else {
				Messenger.info(player, "You must be in the same world as your death location to track it.");
			}
		} else {
			Messenger.info(player, "No death location was found.");
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
	 * Open the compass menu to the given player
	 */
	public static void openMenu(final Player player) {
		new CompassMenu(player).displayTo(player);
	}
}
