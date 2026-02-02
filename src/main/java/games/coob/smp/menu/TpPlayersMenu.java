package games.coob.smp.menu;

import games.coob.smp.tp.TpRequestManager;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.ItemCreator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Paginated menu to select a player to send a TP request to. Click a head to
 * send request; target gets [ACCEPT] [DENY] in chat.
 */
public class TpPlayersMenu extends SimpleMenu {

	private final List<Player> players;
	private int currentPage = 0;
	private static final int ITEMS_PER_PAGE = 10;
	private static final int PLAYER_SLOT_START = 0;
	private static final int PLAYER_SLOT_END = 9;
	private static final int SLOT_CLOSE = 27;
	private static final int SLOT_PREV = 28;
	private static final int SLOT_NEXT = 34;

	public TpPlayersMenu(final Player viewer) {
		super(viewer, 9 * 4, "&lSelect a player to teleport to");
		this.players = compilePlayers(viewer);
		setupItems();
	}

	private static List<Player> compilePlayers(final Player viewer) {
		List<Player> list = new ArrayList<>();
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (!p.equals(viewer))
				list.add(p);
		}
		return list;
	}

	private void setupItems() {
		inventory.clear();
		int startIndex = currentPage * ITEMS_PER_PAGE;
		int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, players.size());

		for (int i = startIndex; i < endIndex; i++) {
			Player player = players.get(i);
			int slot = PLAYER_SLOT_START + (i - startIndex);
			inventory.setItem(slot, createPlayerItem(player));
		}

		inventory.setItem(SLOT_CLOSE, ItemCreator.of(
				Material.ARROW,
				"&c&lClose",
				"",
				"&7Close this menu.").make());

		if (currentPage > 0) {
			inventory.setItem(SLOT_PREV, ItemCreator.of(
					Material.ARROW,
					"&a&lPrevious Page",
					"",
					"&7Go to the previous page.").make());
		}
		if (endIndex < players.size()) {
			inventory.setItem(SLOT_NEXT, ItemCreator.of(
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
				"&eClick to send TP request",
				"&7You will teleport to them if they accept.")
				.skullOwner(player.getName()).make();
	}

	@Override
	protected void onMenuClick(Player viewer, int slot, ItemStack clicked, ClickType clickType) {
		if (clicked == null)
			return;

		if (clicked.getType() == Material.ARROW && slot == SLOT_CLOSE) {
			viewer.closeInventory();
			return;
		}

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

		if (clicked.getType() == Material.PLAYER_HEAD && slot >= PLAYER_SLOT_START && slot <= PLAYER_SLOT_END) {
			int playerIndex = currentPage * ITEMS_PER_PAGE + (slot - PLAYER_SLOT_START);
			if (playerIndex < players.size()) {
				Player target = players.get(playerIndex);
				if (viewer.equals(target)) {
					ColorUtil.sendMessage(viewer, "&cYou cannot teleport to yourself.");
					return;
				}
				TpRequestManager.getInstance().sendRequest(viewer, target);
				viewer.closeInventory();
			}
		}
	}

	public static void openMenu(final Player player) {
		new TpPlayersMenu(player).displayTo(player);
	}
}
