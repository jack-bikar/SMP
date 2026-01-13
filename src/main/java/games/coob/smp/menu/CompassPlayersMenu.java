package games.coob.smp.menu;

import games.coob.smp.PlayerCache;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.menu.MenuPagged;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.remain.CompMaterial;

import java.util.List;

/**
 * The menu used to select players to teleport to when spectating
 */
public class CompassPlayersMenu extends MenuPagged<Player> {

	/**
	 * Create a new spectate menu
	 */
	CompassPlayersMenu(final Player viewer) {
		super(compilePlayers(viewer));

		setTitle("&lSelect a player to track");
	}

	/*
	 * Get a list of players we can spectate
	 */
	private static List<Player> compilePlayers(final Player viewer) {
		final World world = viewer.getWorld();

		return world.getPlayers();
	}

	/**
	 * @see MenuPagged#convertToItemStack(Object)
	 */
	@Override
	protected ItemStack convertToItemStack(final Player player) {
		return ItemCreator.of(
						CompMaterial.PLAYER_HEAD,
						player.getName(),
						"",
						"Click to track",
						player.getName())
				.skullOwner(player.getName()).make();
	}

	/**
	 * @see MenuPagged#onPageClick(Player, Object, ClickType)
	 */
	@Override
	protected void onPageClick(final Player viewer, final Player clickedPlayer, final ClickType click) {
		final PlayerCache cache = PlayerCache.from(viewer);
		final Location location = clickedPlayer.getLocation();
		location.setY(1);

		if (clickedPlayer.getWorld() == viewer.getWorld()) {
			if (viewer.getWorld().getEnvironment() == World.Environment.NORMAL)
				viewer.setCompassTarget(clickedPlayer.getLocation());
			else {
				if (viewer.getInventory().contains(CompMaterial.COMPASS.getMaterial())) {
					for (int i = 0; i <= viewer.getInventory().getSize(); i++) {
						final ItemStack item = viewer.getInventory().getItem(i);

						if (item != null && item.getType() == CompMaterial.COMPASS.getMaterial()) {
							final CompassMeta compass = (CompassMeta) item.getItemMeta();

							compass.setLodestone(location);
							compass.setLodestoneTracked(false); // we do not want a real lodestone to be present at that location.
							item.setItemMeta(compass);
						}
					}
				}
			}

			cache.setTrackingLocation("Player");
			cache.setTargetByUUID(clickedPlayer.getUniqueId());
			viewer.closeInventory();
			Messenger.success(viewer, "&aYou are now tracking &3" + clickedPlayer.getName() + "'s &alocation.");

		} else Messenger.info(viewer, "&cYou must be in the same world as the player you want to track.");
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getInfo()
	 */
	@Override
	protected String[] getInfo() {
		return new String[]{
				"Click to track a player"
		};
	}
}
