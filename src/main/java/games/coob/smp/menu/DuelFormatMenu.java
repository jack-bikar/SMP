package games.coob.smp.menu;

import games.coob.smp.duel.DuelSide;
import games.coob.smp.duel.TeamDuelManager;
import games.coob.smp.duel.TeamLobby;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ItemCreator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * Leader picks the team sizes (1v2, 1v3, 2v4...) with left/right clicks on each
 * banner. Nothing changes until they click apply, so players aren't moved
 * around while they are still choosing.
 */
public final class DuelFormatMenu extends SimpleMenu {

	private static final int SLOT_SUMMARY = 4;
	private static final int SLOT_RED = 11;
	private static final int SLOT_BLUE = 15;
	private static final int SLOT_BACK = 18;
	private static final int SLOT_OPEN = 22;
	private static final int SLOT_APPLY = 26;

	private final TeamLobby lobby;
	private int red;
	private int blue;

	public DuelFormatMenu(Player viewer, TeamLobby lobby) {
		super(viewer, 27, "&8Duel format");
		this.lobby = lobby;

		int max = Settings.DuelSection.MAX_TEAM_SIZE;
		this.red = Math.clamp(lobby.hasFixedFormat() ? lobby.getMaxSize(DuelSide.RED) : lobby.size(DuelSide.RED), 1, max);
		this.blue = Math.clamp(lobby.hasFixedFormat() ? lobby.getMaxSize(DuelSide.BLUE) : lobby.size(DuelSide.BLUE), 1, max);
		render();
	}

	private void render() {
		inventory.clear();
		int max = Settings.DuelSection.MAX_TEAM_SIZE;

		inventory.setItem(SLOT_SUMMARY, ItemCreator.of(Material.ARMOR_STAND, "&f&lFormat: &e" + red + "v" + blue,
				"", "&7Pick how many players each team has.", "&7Uneven formats like 1v3 are allowed.").make());

		inventory.setItem(SLOT_RED, sizeItem(DuelSide.RED, red, max));
		inventory.setItem(SLOT_BLUE, sizeItem(DuelSide.BLUE, blue, max));

		inventory.setItem(SLOT_BACK, ItemCreator.of(Material.ARROW, "&7Back").make());
		inventory.setItem(SLOT_OPEN, ItemCreator.of(Material.COMPASS, "&f&lOpen format",
				"", "&7Any size up to " + max + "v" + max + ",", "&7teams fill up as players join.", "", "&eClick to use").make());

		boolean fits = lobby.size() <= red + blue;
		inventory.setItem(SLOT_APPLY, fits
				? ItemCreator.of(Material.LIME_CONCRETE, "&a&lUse " + red + "v" + blue, "", "&eClick to apply").make()
				: ItemCreator.of(Material.RED_CONCRETE, "&c&lToo many players",
						"", "&7" + lobby.size() + " players are in the lobby,", "&7remove someone first.").make());
	}

	private static ItemStack sizeItem(DuelSide side, int size, int max) {
		ItemStack item = ItemCreator.of(side.getBanner(), side.getColorCode() + "&l" + side.getDisplayName() + " team: &f"
				+ size + " player" + (size != 1 ? "s" : ""),
				"", "&eLeft-click: &7one more", "&eRight-click: &7one less", "&7(1 to " + max + ")").make();
		item.setAmount(size);
		return item;
	}

	@Override
	protected void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
		TeamDuelManager manager = TeamDuelManager.getInstance();
		// They may have left, been removed or started meanwhile
		if (manager.getLobby(player) != lobby || !lobby.isLeader(player.getUniqueId())) {
			player.closeInventory();
			return;
		}

		int max = Settings.DuelSection.MAX_TEAM_SIZE;
		int change = clickType.isRightClick() ? -1 : 1;
		switch (slot) {
			case SLOT_RED -> red = Math.clamp(red + change, 1, max);
			case SLOT_BLUE -> blue = Math.clamp(blue + change, 1, max);
			case SLOT_BACK -> {
				new TeamLobbyMenu(player, lobby).displayTo(player);
				return;
			}
			case SLOT_OPEN -> {
				if (manager.setFormat(player, 0, 0))
					new TeamLobbyMenu(player, lobby).displayTo(player);
				return;
			}
			case SLOT_APPLY -> {
				if (manager.setFormat(player, red, blue))
					new TeamLobbyMenu(player, lobby).displayTo(player);
				return;
			}
			default -> {
				return;
			}
		}
		render();
	}
}
