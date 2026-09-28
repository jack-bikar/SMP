package games.coob.smp.menu;

import games.coob.smp.duel.DuelManager;
import games.coob.smp.duel.DuelSide;
import games.coob.smp.duel.TeamDuelManager;
import games.coob.smp.duel.TeamLobby;
import games.coob.smp.duel.model.DuelStatistics;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ItemCreator;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Team duel setup: red team on the left, blue team on the right, and buttons to
 * invite, balance and start along the bottom.
 */
public final class TeamLobbyMenu extends SimpleMenu {

	/** Head slots per side, 4 columns x 4 rows. */
	private static final int[] RED_SLOTS = { 0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30 };
	private static final int[] BLUE_SLOTS = { 5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35 };
	private static final int[] DIVIDER_SLOTS = { 4, 13, 22, 31, 40 };

	private static final int SLOT_RED_BANNER = 38;
	private static final int SLOT_BLUE_BANNER = 42;
	private static final int SLOT_INVITE = 45;
	private static final int SLOT_BALANCE = 47;
	private static final int SLOT_START = 49;
	private static final int SLOT_INVITES = 51;
	private static final int SLOT_LEAVE = 53;

	@Getter
	private final TeamLobby lobby;
	private final Map<Integer, UUID> headSlots = new HashMap<>();

	public TeamLobbyMenu(Player viewer, TeamLobby lobby) {
		super(viewer, 54, "&8Team Duel");
		this.lobby = lobby;
		render();
	}

	public void render() {
		inventory.clear();
		headSlots.clear();

		boolean leader = lobby.isLeader(viewer.getUniqueId());
		int max = Settings.DuelSection.MAX_TEAM_SIZE;

		placeTeam(DuelSide.RED, RED_SLOTS, leader);
		placeTeam(DuelSide.BLUE, BLUE_SLOTS, leader);

		ItemStack divider = ItemCreator.of(Material.GRAY_STAINED_GLASS_PANE, " ").make();
		for (int slot : DIVIDER_SLOTS)
			inventory.setItem(slot, divider);

		for (DuelSide side : DuelSide.values()) {
			boolean mine = lobby.getSide(viewer.getUniqueId()) == side;
			inventory.setItem(side == DuelSide.RED ? SLOT_RED_BANNER : SLOT_BLUE_BANNER, ItemCreator.of(side.getBanner(),
					side.getColorCode() + "&l" + side.getDisplayName() + " team &7(" + lobby.size(side) + "/" + max + ")",
					"",
					mine ? "&aYou are on this team" : lobby.isTeamFull(side) ? "&cThis team is full" : "&eClick to join this team")
					.make());
		}

		inventory.setItem(SLOT_INVITE, ItemCreator.of(Material.WRITABLE_BOOK, "&a&lInvite players",
				"", "&7Pick from everyone online.", "&7They join the smaller team.", "", "&eClick to choose").make());

		inventory.setItem(SLOT_BALANCE, leader
				? ItemCreator.of(Material.COMPARATOR, "&e&lBalance teams", "",
						"&7Splits players by duel record", "&7so both teams are fair.", "", "&eClick to balance").make()
				: ItemCreator.of(Material.COMPARATOR, "&7Balance teams", "", "&7Only the leader can do this.").make());

		String problem = lobby.getStartProblem();
		if (!leader) {
			inventory.setItem(SLOT_START, ItemCreator.of(Material.CLOCK, "&7Waiting for the leader",
					"", "&7Format: &f" + lobby.getFormat(), "&7Only the leader can start.").make());
		} else if (problem != null) {
			inventory.setItem(SLOT_START, ItemCreator.of(Material.RED_CONCRETE, "&c&lNot ready",
					"", "&7" + problem).make());
		} else {
			inventory.setItem(SLOT_START, ItemCreator.of(Material.LIME_CONCRETE, "&a&lStart " + lobby.getFormat() + " duel",
					"", "&7Everyone is teleported to the arena.", "", "&eClick to start").make());
		}

		List<String> invited = new ArrayList<>();
		invited.add("");
		for (UUID id : lobby.getPendingInvites()) {
			OfflinePlayer player = Bukkit.getOfflinePlayer(id);
			invited.add("&7- &f" + (player.getName() != null ? player.getName() : "?"));
		}
		if (invited.size() == 1)
			invited.add("&7Nobody is invited right now.");
		inventory.setItem(SLOT_INVITES, ItemCreator.of(Material.PAPER, "&f&lPending invites",
				invited.toArray(new String[0])).make());

		inventory.setItem(SLOT_LEAVE, ItemCreator.of(Material.BARRIER, "&c&lLeave lobby").make());
	}

	private void placeTeam(DuelSide side, int[] slots, boolean viewerIsLeader) {
		List<UUID> team = lobby.getTeam(side);
		DuelStatistics stats = DuelStatistics.getInstance();

		for (int i = 0; i < slots.length; i++) {
			if (i >= team.size()) {
				if (i < Settings.DuelSection.MAX_TEAM_SIZE)
					inventory.setItem(slots[i], ItemCreator.of(side.getPane(), side.getColorCode() + "Open spot").make());
				continue;
			}

			UUID id = team.get(i);
			OfflinePlayer member = Bukkit.getOfflinePlayer(id);
			List<String> lore = new ArrayList<>();
			lore.add("");
			if (lobby.isLeader(id))
				lore.add("&6Leader");
			lore.add("&7Duels: &a" + stats.getWins(id) + "W &c" + stats.getLosses(id) + "L");
			lore.add("");
			if (id.equals(viewer.getUniqueId())) {
				lore.add("&eClick to switch team");
			} else if (viewerIsLeader) {
				lore.add("&eLeft-click to move to the other team");
				lore.add("&cRight-click to remove from the lobby");
			}

			inventory.setItem(slots[i], ItemCreator.of(Material.PLAYER_HEAD,
					side.getColorCode() + "&l" + member.getName(), lore.toArray(new String[0])).skullOwner(member).make());
			headSlots.put(slots[i], id);
		}
	}

	@Override
	protected void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
		TeamDuelManager manager = TeamDuelManager.getInstance();
		if (!lobby.isMember(player.getUniqueId())) {
			player.closeInventory();
			return;
		}

		UUID member = headSlots.get(slot);
		if (member != null) {
			if (clickType.isRightClick() && !member.equals(player.getUniqueId())) {
				manager.kick(player, member);
			} else {
				manager.switchSide(player, member, lobby.getSide(member).other());
			}
			return;
		}

		switch (slot) {
			case SLOT_RED_BANNER -> manager.switchSide(player, player.getUniqueId(), DuelSide.RED);
			case SLOT_BLUE_BANNER -> manager.switchSide(player, player.getUniqueId(), DuelSide.BLUE);
			case SLOT_INVITE -> new PlayerPickerMenu(player, "&8Invite to team duel",
					target -> !lobby.isMember(target.getUniqueId())
							&& !DuelManager.getInstance().isInDuel(target),
					"Click to invite",
					target -> {
						manager.invite(player, target);
						new TeamLobbyMenu(player, lobby).displayTo(player);
					},
					() -> new TeamLobbyMenu(player, lobby).displayTo(player)).displayTo(player);
			case SLOT_BALANCE -> manager.balance(player);
			case SLOT_START -> manager.start(player);
			case SLOT_LEAVE -> manager.leave(player, false);
			default -> {
			}
		}
	}
}
