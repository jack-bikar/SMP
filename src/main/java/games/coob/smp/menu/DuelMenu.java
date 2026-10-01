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
	/** Kits for the 1v1 challenges this player sends. */
	private static final int SLOT_KITS = 19;

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
					"", "&7From &f" + (leader != null ? leader.getName() : "?") + " &7(" + pendingInvite.getTargetFormat() + ")",
					"", "&eClick to join").make());
		}

		boolean kits = DuelManager.getInstance().wantsKits(viewer);
		inventory.setItem(SLOT_ONE_V_ONE, ItemCreator.of(Material.IRON_SWORD, "&c&l1v1 Duel",
				"", "&7Challenge an online player.", kits ? "&7With kits." : "&7With your own gear.",
				"", "&eClick to choose").make());
		if (DuelManager.kitsAvailable())
			inventory.setItem(SLOT_KITS, ItemCreator.of(kits ? Material.IRON_CHESTPLATE : Material.LEATHER_CHESTPLATE,
					kits ? "&a&lKits: On" : "&7&lKits: Off",
					"", "&7For the 1v1 challenges you send:", kits ? "&7both of you pick a kit," : "&7you both fight with",
					kits ? "&7your own items are kept safe." : "&7your own gear.",
					"", "&eClick to turn " + (kits ? "off" : "on")).make());

		if (Settings.DuelSection.TEAMS_ENABLED) {
			boolean inLobby = TeamDuelManager.getInstance().getLobby(viewer) != null;
			inventory.setItem(SLOT_TEAM, ItemCreator.of(Material.RED_BANNER, "&9&lTeam Duel",
					"", "&72v2, 3v3 or a custom format like 1v3,", "&7up to " + Settings.DuelSection.MAX_TEAM_SIZE + "v"
							+ Settings.DuelSection.MAX_TEAM_SIZE + ".",
					"&7Invite players from a list,", "&7teams fill up automatically.",
					Settings.DuelSection.MOBS_ENABLED ? "&7Add mobs to fight for either team." : null,
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
			case SLOT_KITS -> {
				if (DuelManager.kitsAvailable()) {
					DuelManager.getInstance().setWantsKits(player, !DuelManager.getInstance().wantsKits(player));
					render();
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
