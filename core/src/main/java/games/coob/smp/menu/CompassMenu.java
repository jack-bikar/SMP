package games.coob.smp.menu;

import games.coob.smp.PlayerCache;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.menu.Menu;
import org.mineacademy.fo.menu.button.Button;
import org.mineacademy.fo.menu.button.ButtonMenu;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.remain.CompMaterial;

public class CompassMenu extends Menu {

	private final Button selectPlayerButton;
	private final Button selectBedLocationButton;
	private final Button selectDeathLocationButton;

	public CompassMenu(final Player player) {
		setTitle("&lCompass Menu");
		setSize(9 * 3);

		this.selectPlayerButton = new ButtonMenu(new CompassPlayersMenu(player), ItemCreator.of(
				CompMaterial.PLAYER_HEAD,
				"Select a player to track",
				"",
				"Click here to",
				"open a player",
				"selection menu."));

		this.selectBedLocationButton = new Button() {
			@Override
			public void onClickedInMenu(final Player player, final Menu menu, final ClickType click) {
				if (player.getBedSpawnLocation() != null) {
					if (player.getBedSpawnLocation().getWorld() == player.getWorld()) {
						final Location location = player.getBedSpawnLocation();
						final PlayerCache cache = PlayerCache.from(player);
						location.setY(1);

						if (player.getWorld().getEnvironment() == World.Environment.NORMAL)
							player.setCompassTarget(location);
						else {
							if (player.getInventory().contains(CompMaterial.COMPASS.getMaterial())) {
								for (int i = 0; i <= player.getInventory().getSize(); i++) {
									final ItemStack item = player.getInventory().getItem(i);

									if (item != null && item.getType() == CompMaterial.COMPASS.getMaterial()) {
										final CompassMeta compass = (CompassMeta) item.getItemMeta();

										// location.getBlock().setType(CompMaterial.LODESTONE.getMaterial());
										compass.setLodestone(location);
										compass.setLodestoneTracked(false); // we do not want a real lodestone to be present at that location.
										item.setItemMeta(compass);
									}
								}
							}
						}

						cache.setTrackingLocation("Bed");
						player.closeInventory();
						Messenger.success(player, "&aYour are now tracking your bed spawn location.");
					} else
						Messenger.info(player, "&cYou must be in the same world as your bed spawn location to track it.");
				} else Messenger.info(player, "&cNo bed location was found.");
			}

			@Override
			public ItemStack getItem() {
				return ItemCreator.of(
						CompMaterial.BLUE_BED,
						"&bTrack your bed location",
						"",
						"Click here to",
						"start tracking your",
						"bed spawn location.").make();
			}
		};

		this.selectDeathLocationButton = new Button() {
			@Override
			public void onClickedInMenu(final Player player, final Menu menu, final ClickType click) {
				final PlayerCache cache = PlayerCache.from(player);

				if (cache.getDeathLocation() != null) {
					if (cache.getDeathLocation().getWorld() == player.getWorld()) {
						if (player.getWorld().getEnvironment() == World.Environment.NORMAL)
							player.setCompassTarget(cache.getDeathLocation());
						else {
							if (player.getInventory().contains(CompMaterial.COMPASS.getMaterial())) {
								for (int i = 0; i <= player.getInventory().getSize(); i++) {
									final ItemStack item = player.getInventory().getItem(i);

									if (item != null && item.getType() == CompMaterial.COMPASS.getMaterial()) {
										final CompassMeta compass = (CompassMeta) item.getItemMeta();
										final Location location = cache.getDeathLocation();

										compass.setLodestone(location);
										compass.setLodestoneTracked(false); // we do not want a real lodestone to be present at that location.
										item.setItemMeta(compass);
									}
								}
							}
						}

						cache.setTrackingLocation("Death");
						player.closeInventory();
						Messenger.success(player, "&aYour are now tracking your death location.");

					} else Messenger.info(player, "&cYou must be in the same world as you death location to track it.");
				} else Messenger.info(player, "&cNo death location was found.");
			}

			@Override
			public ItemStack getItem() {
				return ItemCreator.of(
						CompMaterial.SKELETON_SKULL,
						"&cTrack your death location",
						"",
						"Click here to start",
						"tracking your",
						"previous death location.").make();
			}
		};
	}

	@Override
	public ItemStack getItemAt(final int slot) {

		if (slot == 9 + 2)
			return this.selectPlayerButton.getItem();

		if (slot == 9 + 4)
			return this.selectBedLocationButton.getItem();

		if (slot == 9 + 6)
			return this.selectDeathLocationButton.getItem();

		return null;
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getInfo()
	 */
	@Override
	protected String[] getInfo() {
		return new String[]{
				"This menu allows you",
				"to track locations"
		};
	}

	/**
	 * Open the spectate player selection menu to the given player
	 *
	 * @param player
	 */
	public static void openMenu(final Player player) {
		new CompassMenu(player).displayTo(player);
	}
}
