package games.coob.smp.menu;

import games.coob.smp.duel.DuelManager;
import games.coob.smp.duel.DuelQueueManager;
import games.coob.smp.duel.TeamDuelManager;
import games.coob.smp.duel.TeamLobby;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ItemCreator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * /duel: everything duel-related in one place.
 */
public final class DuelMenu extends SimpleMenu {

	private static final int SLOT_INVITE = 4;
	private static final int SLOT_ONE_V_ONE = 10;
	private static final int SLOT_TEAM = 12;
	private static final int SLOT_QUEUE = 14;
	private static final int SLOT_STATS = 16;

	private TeamLobby pendingInvite;

	public DuelMenu(Player viewer) {
		super(viewer, 27, "&8Duels");
		render();
	}

	private void render() {
		inventory.clear();

		pendingInvite = TeamDuelManager.getInstance().findInvite(viewer);
		if (pendingInvite != null) {
			Player leader = Bukkit.getPlayer(pendingInvite.getLeaderId());
			inventory.setItem(SLOT_INVITE, ItemCreator.of(Material.PAPER, "&6&lTeam duel invite",
					"", "&7From &f" + (leader != null ? leader.getName() : "?") + " &7(" + pendingInvite.getFormat() + ")",
					"", "&eClick to join").make());
		}

		inventory.setItem(SLOT_ONE_V_ONE, ItemCreator.of(Material.IRON_SWORD, "&c&l1v1 Duel",
				"", "&7Challenge an online player.", "", "&eClick to choose").make());

		if (Settings.DuelSection.TEAMS_ENABLED) {
			boolean inLobby = TeamDuelManager.getInstance().getLobby(viewer) != null;
			inventory.setItem(SLOT_TEAM, ItemCreator.of(Material.RED_BANNER, "&9&lTeam Duel",
					"", "&72v2, 3v3 and up to " + Settings.DuelSection.MAX_TEAM_SIZE + "v"
							+ Settings.DuelSection.MAX_TEAM_SIZE + ".",
					"&7Invite players from a list,", "&7teams fill up automatically.",
					"", inLobby ? "&eClick to open your lobby" : "&eClick to create a lobby").make());
		}

		boolean queued = DuelQueueManager.getInstance().isInQueue(viewer);
		inventory.setItem(SLOT_QUEUE, ItemCreator.of(Material.CLOCK, queued ? "&e&lLeave queue" : "&a&lRandom 1v1",
				"", queued ? "&7You are waiting for an opponent." : "&7Get matched with the next player who joins.",
				"", queued ? "&eClick to leave" : "&eClick to join the queue").make());

		inventory.setItem(SLOT_STATS, ItemCreator.of(Material.BOOK, "&6&lYour stats",
				"", "&eClick to show").make());
	}

	@Override
	protected void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
		switch (slot) {
			case SLOT_INVITE -> {
				if (pendingInvite != null) {
					Player leader = Bukkit.getPlayer(pendingInvite.getLeaderId());
					if (leader != null)
						TeamDuelManager.getInstance().join(player, leader.getName());
				}
			}
			case SLOT_ONE_V_ONE -> new PlayerPickerMenu(player, "&8Challenge to a 1v1",
					target -> !DuelManager.getInstance().isInDuel(target),
					"Click to send a duel request",
					target -> {
						player.closeInventory();
						DuelManager.getInstance().sendRequest(player, target);
					},
					() -> new DuelMenu(player).displayTo(player)).displayTo(player);
			case SLOT_TEAM -> {
				if (Settings.DuelSection.TEAMS_ENABLED)
					TeamDuelManager.getInstance().openLobby(player);
			}
			case SLOT_QUEUE -> {
				player.closeInventory();
				if (DuelQueueManager.getInstance().isInQueue(player)) {
					DuelQueueManager.getInstance().leaveQueue(player);
				} else {
					player.performCommand("duel queue");
				}
			}
			case SLOT_STATS -> {
				player.closeInventory();
				player.performCommand("duel stats");
			}
			default -> {
			}
		}
	}
}
