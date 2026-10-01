package games.coob.smp.duel;

import games.coob.smp.duel.model.DuelStatistics;
import games.coob.smp.menu.TeamLobbyMenu;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Setting up team duels. A player creates a lobby, invites people (from a menu
 * of online players or with /duel invite), and the leader starts the duel.
 * <ul>
 * <li>New members join the smaller team automatically.</li>
 * <li>"Balance" spreads players by their duel record so teams are fair.</li>
 * <li>The leader can pick the format (e.g. 1v3) and add mobs that fight for
 * either team.</li>
 * <li>Anyone in the lobby can invite; only the leader can start, balance, move
 * other players, remove them, or change the format and mobs.</li>
 * </ul>
 */
public final class TeamDuelManager {

	@Getter
	private static final TeamDuelManager instance = new TeamDuelManager();

	private final Map<UUID, TeamLobby> lobbyByMember = new HashMap<>();

	private TeamDuelManager() {
	}

	public TeamLobby getLobby(Player player) {
		return lobbyByMember.get(player.getUniqueId());
	}

	/**
	 * Opens the player's lobby, creating one (with them as leader) if needed.
	 */
	public void openLobby(Player player) {
		if (!checkEnabled(player))
			return;
		if (DuelManager.getInstance().isInDuel(player)) {
			ColorUtil.sendMessage(player, "&cYou are already in a duel.");
			return;
		}

		TeamLobby lobby = lobbyByMember.get(player.getUniqueId());
		if (lobby == null) {
			lobby = new TeamLobby(player.getUniqueId());
			lobbyByMember.put(player.getUniqueId(), lobby);
			ColorUtil.sendMessage(player, "&aTeam duel lobby created. Invite players to fill both teams.");
		}
		new TeamLobbyMenu(player, lobby).displayTo(player);
	}

	/**
	 * Invites a player to the inviter's lobby (creating the lobby if needed).
	 */
	public void invite(Player inviter, Player target) {
		if (!checkEnabled(inviter))
			return;
		if (inviter.equals(target)) {
			ColorUtil.sendMessage(inviter, "&cYou can't invite yourself.");
			return;
		}
		if (DuelManager.getInstance().isInDuel(inviter)) {
			ColorUtil.sendMessage(inviter, "&cYou are in a duel right now.");
			return;
		}

		TeamLobby lobby = lobbyByMember.get(inviter.getUniqueId());
		if (lobby == null) {
			lobby = new TeamLobby(inviter.getUniqueId());
			lobbyByMember.put(inviter.getUniqueId(), lobby);
		}

		if (lobby.isMember(target.getUniqueId())) {
			ColorUtil.sendMessage(inviter, "&c" + target.getName() + " is already in your lobby.");
			return;
		}
		if (DuelManager.getInstance().isInDuel(target)) {
			ColorUtil.sendMessage(inviter, "&c" + target.getName() + " is in a duel right now.");
			return;
		}
		if (lobby.isFull()) {
			ColorUtil.sendMessage(inviter, "&cBoth teams are full.");
			return;
		}
		if (lobby.hasInvite(target.getUniqueId())) {
			ColorUtil.sendMessage(inviter, "&e" + target.getName() + " already has an invite.");
			return;
		}

		lobby.invite(target.getUniqueId());
		Player leader = Bukkit.getPlayer(lobby.getLeaderId());
		String leaderName = leader != null ? leader.getName() : inviter.getName();
		int timeout = Settings.DuelSection.TEAM_INVITE_TIMEOUT_SECONDS;

		Component join = Component.text("[JOIN]", NamedTextColor.GREEN)
				.hoverEvent(HoverEvent.showText(Component.text("Join " + leaderName + "'s team duel")))
				.clickEvent(ClickEvent.runCommand("/duel join " + leaderName));
		Component decline = Component.text("[DECLINE]", NamedTextColor.RED)
				.hoverEvent(HoverEvent.showText(Component.text("Decline the invite")))
				.clickEvent(ClickEvent.runCommand("/duel decline " + leaderName));
		String kind = lobby.isKits() && DuelManager.kitsAvailable() ? "kit duel" : "team duel";
		String what = lobby.hasFixedFormat() ? "a " + lobby.getTargetFormat() + " " + kind + ". "
				: "a " + kind + " (" + lobby.getFormat() + " so far). ";
		target.sendMessage(Component.text()
				.append(Component.text(inviter.getName(), NamedTextColor.YELLOW))
				.append(Component.text(" invited you to " + what, NamedTextColor.GOLD))
				.append(join)
				.append(Component.text(" "))
				.append(decline)
				.append(Component.text(" (" + timeout + "s)", NamedTextColor.GRAY))
				.build());
		broadcast(lobby, "&e" + inviter.getName() + " invited &6" + target.getName() + "&e.");

		final TeamLobby invitedTo = lobby;
		SchedulerUtil.runLater(20L * timeout + 1, () -> refresh(invitedTo));
		refresh(lobby);
	}

	/**
	 * Accepts an invite from the lobby led by {@code leaderName}.
	 */
	public void join(Player player, String leaderName) {
		if (!checkEnabled(player))
			return;

		TeamLobby lobby = findLobbyByLeaderName(leaderName);
		if (lobby == null || !lobby.hasInvite(player.getUniqueId())) {
			ColorUtil.sendMessage(player, "&cYou don't have a team duel invite from " + leaderName + ".");
			return;
		}
		if (DuelManager.getInstance().isInDuel(player)) {
			ColorUtil.sendMessage(player, "&cYou are already in a duel.");
			return;
		}
		if (lobby.isFull()) {
			lobby.removeInvite(player.getUniqueId());
			ColorUtil.sendMessage(player, "&cThat team duel is already full.");
			return;
		}

		TeamLobby current = lobbyByMember.get(player.getUniqueId());
		if (current != null)
			leave(player, false);

		DuelSide side = lobby.smallerSide();
		lobby.removeInvite(player.getUniqueId());
		lobby.setSide(player.getUniqueId(), side);
		lobbyByMember.put(player.getUniqueId(), lobby);
		DuelQueueManager.getInstance().remove(player);

		broadcast(lobby, "&a" + player.getName() + " joined the " + side.coloredName() + " &ateam. &7(" + lobby.getFormat() + ")");
		refresh(lobby);
		new TeamLobbyMenu(player, lobby).displayTo(player);
	}

	public void decline(Player player, String leaderName) {
		TeamLobby lobby = findLobbyByLeaderName(leaderName);
		if (lobby == null || !lobby.hasInvite(player.getUniqueId())) {
			ColorUtil.sendMessage(player, "&cYou don't have a team duel invite from " + leaderName + ".");
			return;
		}
		lobby.removeInvite(player.getUniqueId());
		ColorUtil.sendMessage(player, "&7You declined the invite.");
		broadcast(lobby, "&c" + player.getName() + " declined the invite.");
		refresh(lobby);
	}

	/**
	 * Removes a player from their lobby. The next member becomes leader if the
	 * leader leaves; an empty lobby is removed.
	 */
	public void leave(Player player, boolean quit) {
		TeamLobby lobby = lobbyByMember.remove(player.getUniqueId());
		if (lobby == null) {
			if (!quit)
				ColorUtil.sendMessage(player, "&cYou are not in a team duel lobby.");
			return;
		}

		lobby.remove(player.getUniqueId());
		if (!quit) {
			ColorUtil.sendMessage(player, "&7You left the team duel lobby.");
			closeMenu(player, lobby);
		}

		if (lobby.isEmpty())
			return;

		broadcast(lobby, "&c" + player.getName() + " left the lobby. &7(" + lobby.getFormat() + ")");
		if (lobby.isLeader(player.getUniqueId())) {
			UUID next = lobby.getMembers().getFirst();
			lobby.setLeaderId(next);
			Player newLeader = Bukkit.getPlayer(next);
			broadcast(lobby, "&e" + (newLeader != null ? newLeader.getName() : "Someone") + " is now the leader.");
		}
		refresh(lobby);
	}

	public void handleQuit(Player player) {
		leave(player, true);
	}

	/** The player started another duel: take them out of their lobby. */
	public void removeSilently(Player player) {
		TeamLobby lobby = lobbyByMember.get(player.getUniqueId());
		if (lobby == null)
			return;
		lobbyByMember.remove(player.getUniqueId());
		lobby.remove(player.getUniqueId());
		closeMenu(player, lobby);
		if (!lobby.isEmpty() && lobby.isLeader(player.getUniqueId()))
			lobby.setLeaderId(lobby.getMembers().getFirst());
		if (!lobby.isEmpty())
			refresh(lobby);
	}

	/**
	 * Moves a player to the other team. Leaders can move anyone; other players can
	 * only move themselves.
	 */
	public void switchSide(Player actor, UUID targetId, DuelSide to) {
		TeamLobby lobby = lobbyByMember.get(actor.getUniqueId());
		if (lobby == null || !lobby.isMember(targetId))
			return;
		if (!actor.getUniqueId().equals(targetId) && !lobby.isLeader(actor.getUniqueId())) {
			ColorUtil.sendMessage(actor, "&cOnly the leader can move other players.");
			return;
		}
		if (lobby.getSide(targetId) == to)
			return;
		if (lobby.isTeamFull(to)) {
			ColorUtil.sendMessage(actor, "&cThe " + to.getDisplayName() + " team is full.");
			return;
		}

		lobby.setSide(targetId, to);
		Player target = Bukkit.getPlayer(targetId);
		broadcast(lobby, "&7" + (target != null ? target.getName() : "A player") + " moved to the " + to.coloredName()
				+ " &7team. (" + lobby.getFormat() + ")");
		refresh(lobby);
	}

	public void kick(Player leader, UUID targetId) {
		TeamLobby lobby = lobbyByMember.get(leader.getUniqueId());
		if (lobby == null || !lobby.isLeader(leader.getUniqueId()) || leader.getUniqueId().equals(targetId))
			return;
		Player target = Bukkit.getPlayer(targetId);
		if (target == null || !lobby.isMember(targetId))
			return;

		leave(target, false);
		ColorUtil.sendMessage(target, "&cYou were removed from the team duel lobby.");
	}

	/**
	 * Spreads players over the two teams by duel record (best players split
	 * across teams, snake order), keeping team sizes as even as possible.
	 */
	public void balance(Player leader) {
		TeamLobby lobby = lobbyByMember.get(leader.getUniqueId());
		if (lobby == null)
			return;
		if (!lobby.isLeader(leader.getUniqueId())) {
			ColorUtil.sendMessage(leader, "&cOnly the leader can balance the teams.");
			return;
		}

		List<UUID> members = new ArrayList<>(lobby.getMembers());
		DuelStatistics stats = DuelStatistics.getInstance();
		// Smoothed win rate, so a single win doesn't outrank a long good record
		Map<UUID, Double> rating = new HashMap<>();
		for (UUID id : members)
			rating.put(id, (stats.getWins(id) + 1.0) / (stats.getWins(id) + stats.getLosses(id) + 2.0));
		members.sort(Comparator.comparingDouble((UUID id) -> -rating.get(id)));

		if (lobby.hasFixedFormat()) {
			// Best players first, each to the weaker team that still has room. On a tie the
			// smaller team gets the stronger player, so in a 1v3 the best player goes solo.
			Map<DuelSide, Double> total = new EnumMap<>(DuelSide.class);
			Map<DuelSide, Integer> count = new EnumMap<>(DuelSide.class);
			for (DuelSide side : DuelSide.values()) {
				total.put(side, 0.0);
				count.put(side, 0);
			}
			for (UUID id : members) {
				DuelSide pick = null;
				for (DuelSide side : DuelSide.values()) {
					if (count.get(side) >= lobby.getMaxSize(side))
						continue;
					if (pick == null || total.get(side) < total.get(pick)
							|| (total.get(side).equals(total.get(pick)) && lobby.getMaxSize(side) < lobby.getMaxSize(pick)))
						pick = side;
				}
				if (pick == null)
					pick = lobby.smallerSide();
				total.merge(pick, rating.get(id), Double::sum);
				count.merge(pick, 1, Integer::sum);
				lobby.setSide(id, pick);
			}
		} else {
			// Snake draft: R B B R R B B R ...
			for (int i = 0; i < members.size(); i++) {
				boolean red = (i % 4 == 0) || (i % 4 == 3);
				lobby.setSide(members.get(i), red ? DuelSide.RED : DuelSide.BLUE);
			}
		}

		broadcast(lobby, "&eTeams balanced by duel record. &7(" + lobby.getFormat() + ")");
		refresh(lobby);
	}

	/**
	 * Starts the duel (leader only).
	 */
	public void start(Player leader) {
		TeamLobby lobby = lobbyByMember.get(leader.getUniqueId());
		if (lobby == null) {
			ColorUtil.sendMessage(leader, "&cYou are not in a team duel lobby. Use &e/duel team &cto create one.");
			return;
		}
		if (!lobby.isLeader(leader.getUniqueId())) {
			ColorUtil.sendMessage(leader, "&cOnly the leader can start the duel.");
			return;
		}
		String problem = lobby.getStartProblem();
		if (problem != null) {
			ColorUtil.sendMessage(leader, "&c" + problem);
			return;
		}

		// Everyone joined a kit duel: if kits went away (turned off, duel-kits.yml emptied), say so
		if (lobby.isKits() && !DuelManager.kitsAvailable()) {
			lobby.setKits(false);
			broadcast(lobby, "&cKit duels are turned off right now, so kits were turned off for this lobby."
					+ " Start again to fight with your own gear.");
			refresh(lobby);
			return;
		}

		List<Player> red = onlinePlayers(lobby.getTeam(DuelSide.RED));
		List<Player> blue = onlinePlayers(lobby.getTeam(DuelSide.BLUE));
		if (!DuelManager.getInstance().startDuel(red, blue, allowedMobs(lobby), lobby.isKits()))
			return;

		for (UUID member : lobby.getMembers()) {
			lobbyByMember.remove(member);
			Player player = Bukkit.getPlayer(member);
			if (player != null)
				closeMenu(player, lobby);
		}
	}

	// -------------------------------------------------------------------------
	// Format and mobs (leader only)
	// -------------------------------------------------------------------------

	/** Turns kits on or off for the lobby's duel. */
	public void setKits(Player leader, boolean kits) {
		TeamLobby lobby = leaderLobby(leader, "change the kits");
		if (lobby == null)
			return;
		if (kits && !DuelManager.kitsAvailable()) {
			ColorUtil.sendMessage(leader, "&cKit duels are turned off on this server.");
			return;
		}
		if (lobby.isKits() == kits)
			return;
		lobby.setKits(kits);
		broadcast(lobby, kits ? "&eKits are &aon&e: everyone picks a kit, your own items are kept safe."
				: "&eKits are &coff&e: everyone fights with their own gear.");
		refresh(lobby);
	}

	/**
	 * Sets the team sizes, e.g. 1 and 3 for a 1v3. Pass 0 for both to leave the
	 * sizes open. Players on a team that is now too big move to the other team.
	 *
	 * @return whether the format was changed
	 */
	public boolean setFormat(Player leader, int redSize, int blueSize) {
		TeamLobby lobby = leaderLobby(leader, "change the format");
		if (lobby == null)
			return false;

		boolean open = redSize <= 0 || blueSize <= 0;
		int max = Settings.DuelSection.MAX_TEAM_SIZE;
		if (!open && (redSize > max || blueSize > max)) {
			ColorUtil.sendMessage(leader, "&cTeams can have at most " + max + " players.");
			return false;
		}
		if (!open && lobby.size() > redSize + blueSize) {
			ColorUtil.sendMessage(leader, "&cThere are " + lobby.size() + " players in the lobby, too many for a "
					+ redSize + "v" + blueSize + ". Remove someone first.");
			return false;
		}

		lobby.setFormat(open ? 0 : redSize, open ? 0 : blueSize);

		// Latest arrivals move first
		for (DuelSide side : DuelSide.values()) {
			List<UUID> team = lobby.getTeam(side);
			for (int i = team.size() - 1; i >= 0 && lobby.size(side) > lobby.getMaxSize(side); i--)
				lobby.setSide(team.get(i), side.other());
		}

		broadcast(lobby, open ? "&eFormat set to open &7(any size up to " + max + "v" + max + ")"
				: "&eFormat set to &6" + lobby.getTargetFormat() + "&e.");
		refresh(lobby);
		return true;
	}

	/**
	 * Sets how many of a mob fight for a side (0 removes it).
	 *
	 * @return whether anything changed
	 */
	public boolean setMobCount(Player leader, DuelSide side, EntityType type, int count) {
		TeamLobby lobby = leaderLobby(leader, "choose mobs");
		if (lobby == null)
			return false;
		if (!Settings.DuelSection.MOBS_ENABLED || !Settings.DuelSection.ALLOWED_MOBS.contains(type)) {
			ColorUtil.sendMessage(leader, "&cThat mob can't be used in duels.");
			return false;
		}

		int current = lobby.getMobs(side).getOrDefault(type, 0);
		count = Math.max(0, count);
		int max = Settings.DuelSection.MAX_MOBS_PER_TEAM;
		if (count > current && lobby.getMobCount(side) - current + count > max) {
			ColorUtil.sendMessage(leader, "&cA team can have at most " + max + " mobs.");
			return false;
		}
		if (count == current)
			return false;

		lobby.setMobs(side, type, count);
		refresh(lobby);
		return true;
	}

	public void clearMobs(Player leader, DuelSide side) {
		TeamLobby lobby = leaderLobby(leader, "choose mobs");
		if (lobby == null || lobby.getMobCount(side) == 0)
			return;
		lobby.clearMobs(side);
		refresh(lobby);
	}

	/** The mobs to spawn: only types that are still allowed, and no more than the limit per team. */
	private static Map<DuelSide, Map<EntityType, Integer>> allowedMobs(TeamLobby lobby) {
		Map<DuelSide, Map<EntityType, Integer>> result = new EnumMap<>(DuelSide.class);
		if (!Settings.DuelSection.MOBS_ENABLED)
			return result;

		for (DuelSide side : DuelSide.values()) {
			Map<EntityType, Integer> mobs = new LinkedHashMap<>();
			int room = Settings.DuelSection.MAX_MOBS_PER_TEAM;
			for (Map.Entry<EntityType, Integer> entry : lobby.getMobs(side).entrySet()) {
				int count = Math.min(entry.getValue(), room);
				if (count > 0 && Settings.DuelSection.ALLOWED_MOBS.contains(entry.getKey())) {
					mobs.put(entry.getKey(), count);
					room -= count;
				}
			}
			if (!mobs.isEmpty())
				result.put(side, mobs);
		}
		return result;
	}

	/** The player's lobby if they lead it, otherwise tells them why not and returns null. */
	private TeamLobby leaderLobby(Player player, String action) {
		TeamLobby lobby = lobbyByMember.get(player.getUniqueId());
		if (lobby == null) {
			ColorUtil.sendMessage(player, "&cYou are not in a team duel lobby.");
			return null;
		}
		if (!lobby.isLeader(player.getUniqueId())) {
			ColorUtil.sendMessage(player, "&cOnly the leader can " + action + ".");
			return null;
		}
		return lobby;
	}

	/** The lobby that invited this player most recently, if the invite is still valid. */
	public TeamLobby findInvite(Player player) {
		for (TeamLobby lobby : lobbyByMember.values()) {
			if (lobby.hasInvite(player.getUniqueId()))
				return lobby;
		}
		return null;
	}

	private TeamLobby findLobbyByLeaderName(String leaderName) {
		Player leader = Bukkit.getPlayerExact(leaderName);
		if (leader == null)
			return null;
		TeamLobby lobby = lobbyByMember.get(leader.getUniqueId());
		return lobby != null && lobby.isLeader(leader.getUniqueId()) ? lobby : null;
	}

	private static List<Player> onlinePlayers(List<UUID> ids) {
		List<Player> players = new ArrayList<>();
		for (UUID id : ids) {
			Player player = Bukkit.getPlayer(id);
			if (player != null)
				players.add(player);
		}
		return players;
	}

	private void broadcast(TeamLobby lobby, String message) {
		for (Player player : onlinePlayers(lobby.getMembers()))
			ColorUtil.sendMessage(player, message);
	}

	/** Redraws the lobby menu for every member who has it open. */
	public void refresh(TeamLobby lobby) {
		for (Player player : onlinePlayers(lobby.getMembers())) {
			if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof TeamLobbyMenu menu
					&& menu.getLobby() == lobby)
				menu.render();
		}
	}

	private static void closeMenu(Player player, TeamLobby lobby) {
		if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof TeamLobbyMenu menu
				&& menu.getLobby() == lobby)
			player.closeInventory();
	}

	private static boolean checkEnabled(Player player) {
		if (!Settings.DuelSection.ENABLE_DUELS || !Settings.DuelSection.TEAMS_ENABLED) {
			ColorUtil.sendMessage(player, "&cTeam duels are currently disabled.");
			return false;
		}
		return true;
	}

	public void clear() {
		lobbyByMember.clear();
	}
}
