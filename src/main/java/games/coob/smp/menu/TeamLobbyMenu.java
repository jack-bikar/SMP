package games.coob.smp.menu;

import games.coob.smp.duel.DuelManager;
import games.coob.smp.duel.DuelMobs;
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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Team duel setup: red team on the left, blue team on the right, the format and
 * each team's mobs between them, and buttons to invite, balance and start along
 * the bottom.
 */
public final class TeamLobbyMenu extends SimpleMenu {

	/** Head slots per side, 4 columns x 4 rows. */
	private static final int[] RED_SLOTS = { 0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30 };
	private static final int[] BLUE_SLOTS = { 5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35 };
	private static final int[] DIVIDER_SLOTS = { 4, 13, 22, 31 };

	private static final int SLOT_RED_MOBS = 37;
	private static final int SLOT_RED_BANNER = 38;
	private static final int SLOT_FORMAT = 40;
	private static final int SLOT_BLUE_BANNER = 42;
	private static final int SLOT_BLUE_MOBS = 43;
	private static final int SLOT_INVITE = 45;
	private static final int SLOT_BALANCE = 47;
	private static final int SLOT_KITS = 48;
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

		placeTeam(DuelSide.RED, RED_SLOTS, leader);
		placeTeam(DuelSide.BLUE, BLUE_SLOTS, leader);

		ItemStack divider = ItemCreator.of(Material.GRAY_STAINED_GLASS_PANE, " ").make();
		for (int slot : DIVIDER_SLOTS)
			inventory.setItem(slot, divider);

		for (DuelSide side : DuelSide.values()) {
			boolean mine = lobby.getSide(viewer.getUniqueId()) == side;
			inventory.setItem(side == DuelSide.RED ? SLOT_RED_BANNER : SLOT_BLUE_BANNER, ItemCreator.of(side.getBanner(),
					side.getColorCode() + "&l" + side.getDisplayName() + " team &7(" + lobby.size(side) + "/"
							+ lobby.getMaxSize(side) + ")",
					"",
					mine ? "&aYou are on this team" : lobby.isTeamFull(side) ? "&cThis team is full" : "&eClick to join this team")
					.make());

			if (Settings.DuelSection.MOBS_ENABLED)
				inventory.setItem(side == DuelSide.RED ? SLOT_RED_MOBS : SLOT_BLUE_MOBS, mobsItem(side, leader));
		}

		inventory.setItem(SLOT_FORMAT, formatItem(leader));

		inventory.setItem(SLOT_INVITE, ItemCreator.of(Material.WRITABLE_BOOK, "&a&lInvite players",
				"", "&7Pick from everyone online.", "&7They join the smaller team.", "", "&eClick to choose").make());

		// Also shown when kits are on but no longer available, so the leader can turn them off
		if (DuelManager.kitsAvailable() || lobby.isKits())
			inventory.setItem(SLOT_KITS, kitsItem(leader));

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

	private ItemStack kitsItem(boolean leader) {
		boolean on = lobby.isKits();
		List<String> lore = new ArrayList<>();
		lore.add("");
		if (on) {
			lore.add("&7Everyone picks a kit when the");
			lore.add("&7duel starts (random if too slow).");
			lore.add("&7Your own items are kept safe.");
		} else {
			lore.add("&7Everyone fights with their own gear.");
		}
		lore.add("");
		lore.add(leader ? "&eClick to turn " + (on ? "off" : "on") : "&7Only the leader can change this.");
		return ItemCreator.of(on ? Material.IRON_CHESTPLATE : Material.LEATHER_CHESTPLATE,
				on ? "&a&lKits: On" : "&7&lKits: Off", lore.toArray(new String[0])).make();
	}

	private ItemStack formatItem(boolean leader) {
		int max = Settings.DuelSection.MAX_TEAM_SIZE;
		List<String> lore = new ArrayList<>();
		lore.add("");
		if (lobby.hasFixedFormat()) {
			lore.add("&7Red team: &f" + lobby.getMaxSize(DuelSide.RED) + " player" + (lobby.getMaxSize(DuelSide.RED) != 1 ? "s" : ""));
			lore.add("&7Blue team: &f" + lobby.getMaxSize(DuelSide.BLUE) + " player" + (lobby.getMaxSize(DuelSide.BLUE) != 1 ? "s" : ""));
		} else {
			lore.add("&7Open: any size up to " + max + "v" + max + ".");
			if (!Settings.DuelSection.ALLOW_UNEVEN_TEAMS)
				lore.add("&7Teams must be even to start.");
		}
		lore.add("");
		lore.add(leader ? "&eClick to pick a format, like 1v3" : "&7Only the leader can change this.");

		String name = lobby.hasFixedFormat() ? "&f&lFormat: &e" + lobby.getTargetFormat() : "&f&lFormat: &eOpen";
		return ItemCreator.of(Material.ARMOR_STAND, name, lore.toArray(new String[0])).make();
	}

	private ItemStack mobsItem(DuelSide side, boolean leader) {
		List<String> lore = new ArrayList<>();
		lore.add("");
		Map<EntityType, Integer> mobs = lobby.getMobs(side);
		if (mobs.isEmpty()) {
			lore.add("&7No mobs yet.");
		} else {
			for (Map.Entry<EntityType, Integer> entry : mobs.entrySet())
				lore.add("&7- &f" + entry.getValue() + "x " + DuelMobs.displayName(entry.getKey()));
		}
		lore.add("");
		lore.add("&7Mobs fight for this team. The duel");
		lore.add("&7ends when the players are out.");
		lore.add("");
		lore.add(leader ? "&eClick to choose mobs" : "&7Only the leader can change this.");

		return ItemCreator.of(Material.ZOMBIE_HEAD, side.getColorCode() + "&l" + side.getDisplayName() + " mobs &7("
				+ lobby.getMobCount(side) + "/" + Settings.DuelSection.MAX_MOBS_PER_TEAM + ")", lore.toArray(new String[0]))
				.make();
	}

	private void placeTeam(DuelSide side, int[] slots, boolean viewerIsLeader) {
		List<UUID> team = lobby.getTeam(side);
		DuelStatistics stats = DuelStatistics.getInstance();

		for (int i = 0; i < slots.length; i++) {
			if (i >= team.size()) {
				if (i < lobby.getMaxSize(side))
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
			case SLOT_FORMAT -> {
				if (lobby.isLeader(player.getUniqueId()))
					new DuelFormatMenu(player, lobby).displayTo(player);
			}
			case SLOT_RED_MOBS, SLOT_BLUE_MOBS -> {
				if (Settings.DuelSection.MOBS_ENABLED && lobby.isLeader(player.getUniqueId()))
					new DuelMobsMenu(player, lobby, slot == SLOT_RED_MOBS ? DuelSide.RED : DuelSide.BLUE).displayTo(player);
			}
			// Stays open: invite as many players as you like, then go back
			case SLOT_INVITE -> new PlayerPickerMenu(player, "&8Invite to team duel",
					target -> !lobby.isMember(target.getUniqueId())
							&& !DuelManager.getInstance().isInDuel(target),
					"Click to invite",
					target -> {
						// They may have left or been removed while picking
						if (manager.getLobby(player) != lobby) {
							player.closeInventory();
							return;
						}
						manager.invite(player, target);
					},
					() -> new TeamLobbyMenu(player, lobby).displayTo(player))
					.stayOpen(target -> lobby.hasInvite(target.getUniqueId()) ? "&aInvited, waiting for an answer" : null,
							"Invite everyone", () -> lobby.getPendingInvites().size() >= lobby.openSpots())
					.displayTo(player);
			case SLOT_KITS -> {
				if ((DuelManager.kitsAvailable() || lobby.isKits()) && lobby.isLeader(player.getUniqueId()))
					manager.setKits(player, !lobby.isKits());
			}
			case SLOT_BALANCE -> manager.balance(player);
			case SLOT_START -> manager.start(player);
			case SLOT_LEAVE -> manager.leave(player, false);
			default -> {
			}
		}
	}
}
