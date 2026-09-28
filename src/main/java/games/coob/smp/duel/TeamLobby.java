package games.coob.smp.duel;

import games.coob.smp.settings.Settings;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A group of players setting up a team duel: who is on which side, and who has
 * been invited. Managed by {@link TeamDuelManager}.
 */
public final class TeamLobby {

	@Getter
	@Setter
	private UUID leaderId;

	/** Members in the order they joined. */
	private final Map<UUID, DuelSide> members = new LinkedHashMap<>();
	/** Invited player -> when the invitation expires (millis). */
	private final Map<UUID, Long> invites = new HashMap<>();

	TeamLobby(UUID leaderId) {
		this.leaderId = leaderId;
		members.put(leaderId, DuelSide.RED);
	}

	public boolean isLeader(UUID playerId) {
		return leaderId.equals(playerId);
	}

	public boolean isMember(UUID playerId) {
		return members.containsKey(playerId);
	}

	public DuelSide getSide(UUID playerId) {
		return members.get(playerId);
	}

	void setSide(UUID playerId, DuelSide side) {
		members.put(playerId, side);
	}

	void remove(UUID playerId) {
		members.remove(playerId);
	}

	public List<UUID> getMembers() {
		return new ArrayList<>(members.keySet());
	}

	public List<UUID> getTeam(DuelSide side) {
		List<UUID> team = new ArrayList<>();
		for (Map.Entry<UUID, DuelSide> entry : members.entrySet()) {
			if (entry.getValue() == side)
				team.add(entry.getKey());
		}
		return team;
	}

	public int size(DuelSide side) {
		return getTeam(side).size();
	}

	public int size() {
		return members.size();
	}

	public boolean isEmpty() {
		return members.isEmpty();
	}

	public boolean isTeamFull(DuelSide side) {
		return size(side) >= Settings.DuelSection.MAX_TEAM_SIZE;
	}

	public boolean isFull() {
		return isTeamFull(DuelSide.RED) && isTeamFull(DuelSide.BLUE);
	}

	/** The side a new player should join: the smaller one (red on a tie). */
	public DuelSide smallerSide() {
		return size(DuelSide.BLUE) < size(DuelSide.RED) ? DuelSide.BLUE : DuelSide.RED;
	}

	// Invites

	void invite(UUID playerId) {
		invites.put(playerId, System.currentTimeMillis() + Settings.DuelSection.TEAM_INVITE_TIMEOUT_SECONDS * 1000L);
	}

	public boolean hasInvite(UUID playerId) {
		Long expiry = invites.get(playerId);
		if (expiry == null)
			return false;
		if (expiry < System.currentTimeMillis()) {
			invites.remove(playerId);
			return false;
		}
		return true;
	}

	void removeInvite(UUID playerId) {
		invites.remove(playerId);
	}

	public List<UUID> getPendingInvites() {
		invites.values().removeIf(expiry -> expiry < System.currentTimeMillis());
		return new ArrayList<>(invites.keySet());
	}

	/**
	 * Why the duel can't start yet, or null when it can.
	 */
	public String getStartProblem() {
		int red = size(DuelSide.RED);
		int blue = size(DuelSide.BLUE);
		if (red == 0 || blue == 0)
			return "Both teams need at least one player.";
		if (red != blue && !Settings.DuelSection.ALLOW_UNEVEN_TEAMS)
			return "Teams must be even (" + red + "v" + blue + "). Move a player or invite one more.";
		return null;
	}

	/** e.g. "2v2" */
	public String getFormat() {
		return size(DuelSide.RED) + "v" + size(DuelSide.BLUE);
	}
}
