package games.coob.smp.duel;

import games.coob.smp.settings.Settings;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
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
	/** Team sizes the leader picked, e.g. 1 and 3 for a 1v3. 0 means open: any size up to the max. */
	private int redSize;
	private int blueSize;
	/** Everyone fights with a kit instead of their own gear. */
	@Getter
	@Setter
	private boolean kits;
	/** Mobs that fight for each side: type -> how many, in the order they were added. */
	private final Map<DuelSide, Map<EntityType, Integer>> mobs = new EnumMap<>(DuelSide.class);

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
		return size(side) >= getMaxSize(side);
	}

	public boolean isFull() {
		return isTeamFull(DuelSide.RED) && isTeamFull(DuelSide.BLUE);
	}

	/** Free spots on both teams together. */
	public int openSpots() {
		return Math.max(0, getMaxSize(DuelSide.RED) - size(DuelSide.RED)) + Math.max(0, getMaxSize(DuelSide.BLUE) - size(DuelSide.BLUE));
	}

	/** The side a new player should join: the one with the most open spots (red on a tie). */
	public DuelSide smallerSide() {
		int redOpen = getMaxSize(DuelSide.RED) - size(DuelSide.RED);
		int blueOpen = getMaxSize(DuelSide.BLUE) - size(DuelSide.BLUE);
		return blueOpen > redOpen ? DuelSide.BLUE : DuelSide.RED;
	}

	// Format

	/** Whether the leader picked the team sizes (e.g. 1v3), instead of leaving them open. */
	public boolean hasFixedFormat() {
		return redSize > 0 && blueSize > 0;
	}

	/** How many players this side can have: the picked size, or the server max when open. */
	public int getMaxSize(DuelSide side) {
		if (!hasFixedFormat())
			return Settings.DuelSection.MAX_TEAM_SIZE;
		return side == DuelSide.RED ? redSize : blueSize;
	}

	/** Pass 0 for both to go back to an open format. */
	void setFormat(int redSize, int blueSize) {
		this.redSize = redSize;
		this.blueSize = blueSize;
	}

	// Mobs

	public Map<EntityType, Integer> getMobs(DuelSide side) {
		return Collections.unmodifiableMap(mobs.getOrDefault(side, Map.of()));
	}

	public int getMobCount(DuelSide side) {
		int count = 0;
		for (int amount : getMobs(side).values())
			count += amount;
		return count;
	}

	void setMobs(DuelSide side, EntityType type, int count) {
		Map<EntityType, Integer> team = mobs.computeIfAbsent(side, s -> new LinkedHashMap<>());
		if (count <= 0) {
			team.remove(type);
		} else {
			team.put(type, count);
		}
	}

	void clearMobs(DuelSide side) {
		mobs.remove(side);
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
		if (hasFixedFormat()) {
			List<String> missing = new ArrayList<>();
			for (DuelSide side : DuelSide.values()) {
				int needed = getMaxSize(side) - size(side);
				if (needed > 0)
					missing.add(side.getDisplayName() + " needs " + needed + " more player" + (needed != 1 ? "s" : ""));
			}
			return missing.isEmpty() ? null : String.join(", ", missing) + " (" + getTargetFormat() + ").";
		}
		if (red == 0 || blue == 0)
			return "Both teams need at least one player.";
		// A format the leader picked may be uneven; this only applies to open lobbies
		if (red != blue && !Settings.DuelSection.ALLOW_UNEVEN_TEAMS)
			return "Teams must be even (" + red + "v" + blue + "). Move a player or invite one more.";
		return null;
	}

	/** Current team sizes, e.g. "2v2" */
	public String getFormat() {
		return size(DuelSide.RED) + "v" + size(DuelSide.BLUE);
	}

	/** The picked format (e.g. "1v3"), or the current sizes when the format is open. */
	public String getTargetFormat() {
		return hasFixedFormat() ? redSize + "v" + blueSize : getFormat();
	}
}
