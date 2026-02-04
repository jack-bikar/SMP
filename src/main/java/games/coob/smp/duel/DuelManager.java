package games.coob.smp.duel;

import games.coob.smp.PlayerCache;
import games.coob.smp.duel.model.ArenaData;
import games.coob.smp.duel.model.ArenaRegistry;
import games.coob.smp.duel.model.DuelRequest;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages duel requests and active duels.
 * Singleton pattern for global access.
 */
public class DuelManager {

	@Getter
	private static final DuelManager instance = new DuelManager();

	// Pending duel requests: targetId -> request
	private final Map<UUID, DuelRequest> pendingRequests = new ConcurrentHashMap<>();

	// Active duels: duelId -> ActiveDuel
	private final Map<UUID, ActiveDuel> activeDuels = new ConcurrentHashMap<>();

	// Player to duel mapping for quick lookup
	private final Map<UUID, UUID> playerToDuel = new ConcurrentHashMap<>();

	private DuelManager() {
	}

	/**
	 * Sends a duel request from challenger to target.
	 */
	public void sendRequest(Player challenger, Player target) {
		if (!Settings.DuelSection.ENABLE_DUELS) {
			ColorUtil.sendMessage(challenger, "&cDuels are currently disabled.");
			return;
		}

		UUID challengerId = challenger.getUniqueId();
		UUID targetId = target.getUniqueId();

		// Validation checks
		if (challengerId.equals(targetId)) {
			ColorUtil.sendMessage(challenger, "&cYou cannot duel yourself.");
			return;
		}

		if (isInDuel(challenger)) {
			ColorUtil.sendMessage(challenger, "&cYou are already in a duel.");
			return;
		}

		if (isInDuel(target)) {
			ColorUtil.sendMessage(challenger, "&c" + target.getName() + " is already in a duel.");
			return;
		}

		if (hasPendingRequest(challenger)) {
			ColorUtil.sendMessage(challenger,
					"&cYou already have a pending duel request. Wait for it to expire or be declined.");
			return;
		}

		if (pendingRequests.containsKey(targetId)) {
			ColorUtil.sendMessage(challenger, "&c" + target.getName() + " already has a pending duel request.");
			return;
		}

		// Create request
		DuelRequest request = new DuelRequest(
				challengerId,
				challenger.getName(),
				targetId,
				target.getName(),
				Settings.DuelSection.REQUEST_TIMEOUT_SECONDS);

		pendingRequests.put(targetId, request);

		// Notify players
		ColorUtil.sendMessage(challenger, "&aYou sent a duel request to &e" + target.getName() + "&a.");

		// Send clickable accept/deny message to target
		Component acceptButton = Component.text("[ACCEPT]")
				.color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
				.hoverEvent(HoverEvent.showText(Component.text("Click to accept the duel")))
				.clickEvent(ClickEvent.runCommand("/duel accept " + challenger.getName()));

		Component denyButton = Component.text("[DENY]")
				.color(net.kyori.adventure.text.format.NamedTextColor.RED)
				.hoverEvent(HoverEvent.showText(Component.text("Click to deny the duel")))
				.clickEvent(ClickEvent.runCommand("/duel deny " + challenger.getName()));

		Component message = Component.text("")
				.append(Component.text(challenger.getName())
						.color(net.kyori.adventure.text.format.NamedTextColor.YELLOW))
				.append(Component.text(" has challenged you to a duel! ")
						.color(net.kyori.adventure.text.format.NamedTextColor.GOLD))
				.append(acceptButton)
				.append(Component.text(" "))
				.append(denyButton);

		target.sendMessage(message);

		// Schedule expiry
		int timeout = Settings.DuelSection.REQUEST_TIMEOUT_SECONDS;
		SchedulerUtil.runLater(20L * timeout, () -> {
			DuelRequest pending = pendingRequests.get(targetId);
			if (pending != null && pending.getChallengerId().equals(challengerId) && pending.isExpired()) {
				pendingRequests.remove(targetId);
				Player c = Bukkit.getPlayer(challengerId);
				Player t = Bukkit.getPlayer(targetId);
				if (c != null && c.isOnline()) {
					ColorUtil.sendMessage(c, "&cYour duel request to " + request.getTargetName() + " has expired.");
				}
				if (t != null && t.isOnline()) {
					ColorUtil.sendMessage(t,
							"&cThe duel request from " + request.getChallengerName() + " has expired.");
				}
			}
		});
	}

	/**
	 * Accepts a duel request.
	 */
	public void acceptRequest(Player accepter, String challengerName) {
		UUID accepterId = accepter.getUniqueId();

		DuelRequest request = pendingRequests.get(accepterId);
		if (request == null) {
			ColorUtil.sendMessage(accepter, "&cYou don't have any pending duel requests.");
			return;
		}

		if (!request.getChallengerName().equalsIgnoreCase(challengerName)) {
			ColorUtil.sendMessage(accepter, "&cNo duel request from " + challengerName + ".");
			return;
		}

		if (request.isExpired()) {
			pendingRequests.remove(accepterId);
			ColorUtil.sendMessage(accepter, "&cThat duel request has expired.");
			return;
		}

		Player challenger = Bukkit.getPlayer(request.getChallengerId());
		if (challenger == null || !challenger.isOnline()) {
			pendingRequests.remove(accepterId);
			ColorUtil.sendMessage(accepter, "&cThe challenger is no longer online.");
			return;
		}

		// Remove the request
		pendingRequests.remove(accepterId);

		// Start the duel
		startDuel(challenger, accepter);
	}

	/**
	 * Denies a duel request.
	 */
	public void denyRequest(Player denier, String challengerName) {
		UUID denierId = denier.getUniqueId();

		DuelRequest request = pendingRequests.get(denierId);
		if (request == null) {
			ColorUtil.sendMessage(denier, "&cYou don't have any pending duel requests.");
			return;
		}

		if (!request.getChallengerName().equalsIgnoreCase(challengerName)) {
			ColorUtil.sendMessage(denier, "&cNo duel request from " + challengerName + ".");
			return;
		}

		pendingRequests.remove(denierId);

		ColorUtil.sendMessage(denier, "&cYou denied the duel request from " + challengerName + ".");

		Player challenger = Bukkit.getPlayer(request.getChallengerId());
		if (challenger != null && challenger.isOnline()) {
			ColorUtil.sendMessage(challenger, "&c" + denier.getName() + " denied your duel request.");
		}
	}

	/**
	 * Starts a duel between two players.
	 */
	private void startDuel(Player challenger, Player opponent) {
		ColorUtil.sendMessage(challenger, "&aDuel accepted! Preparing arena...");
		ColorUtil.sendMessage(opponent, "&aDuel accepted! Preparing arena...");

		Settings.DuelSection.ArenaMode mode = Settings.DuelSection.ARENA_MODE;

		switch (mode) {
			case NATURAL -> startNaturalDuel(challenger, opponent);
			case CREATED -> startCreatedArenaDuel(challenger, opponent);
			case RANDOM -> {
				// Pick randomly between natural and created (if arenas exist)
				if (!ArenaRegistry.getInstance().getReadyArenas().isEmpty() && Math.random() < 0.5) {
					startCreatedArenaDuel(challenger, opponent);
				} else {
					startNaturalDuel(challenger, opponent);
				}
			}
		}
	}

	/**
	 * Starts a duel in a natural (randomly teleported) arena.
	 */
	private void startNaturalDuel(Player challenger, Player opponent) {
		NaturalTeleporter.teleportPlayers(challenger, opponent).thenAccept(result -> {
			if (!result.success) {
				ColorUtil.sendMessage(challenger, "&cFailed to find arena location: " + result.errorMessage);
				ColorUtil.sendMessage(opponent, "&cFailed to find arena location: " + result.errorMessage);
				return;
			}

			// Create the active duel
			ActiveDuel duel = new ActiveDuel(challenger, opponent, null, result.center, result.usedChunks);

			// Register the duel
			registerDuel(duel);

			// Start countdown
			duel.startCountdown();
		});
	}

	/**
	 * Starts a duel in a created arena.
	 */
	private void startCreatedArenaDuel(Player challenger, Player opponent) {
		ArenaData arena = ArenaRegistry.getInstance().getRandomArena();

		if (arena == null) {
			// Fallback to natural if no arenas available
			ColorUtil.sendMessage(challenger, "&eNo arenas available. Using natural arena...");
			ColorUtil.sendMessage(opponent, "&eNo arenas available. Using natural arena...");
			startNaturalDuel(challenger, opponent);
			return;
		}

		// Spawn locations with yaw so players face each other
		Location spawn1 = arena.getSpawn1().clone();
		Location spawn2 = arena.getSpawn2().clone();
		spawn1.setYaw(NaturalTeleporter.getYawToFace(spawn1, spawn2));
		spawn1.setPitch(0);
		spawn2.setYaw(NaturalTeleporter.getYawToFace(spawn2, spawn1));
		spawn2.setPitch(0);

		// Fly down from sky to arena spawn points (cosmic-style)
		final int[] landed = { 0 };
		Runnable onBothLanded = () -> {
			landed[0]++;
			if (landed[0] == 2) {
				ActiveDuel duel = new ActiveDuel(challenger, opponent, arena, arena.getCenter(), null);
				registerDuel(duel);
				duel.startCountdown();
			}
		};

		DuelFlyDownTeleporter.teleportToGround(challenger, spawn1, onBothLanded);
		DuelFlyDownTeleporter.teleportToGround(opponent, spawn2, onBothLanded);
	}

	/**
	 * Registers an active duel.
	 * Package-private for use by DuelQueueManager.
	 */
	void registerDuel(ActiveDuel duel) {
		activeDuels.put(duel.getDuelId(), duel);
		playerToDuel.put(duel.getChallenger().getUniqueId(), duel.getDuelId());
		playerToDuel.put(duel.getOpponent().getUniqueId(), duel.getDuelId());
	}

	/**
	 * Called when a duel ends.
	 */
	public void onDuelEnded(ActiveDuel duel) {
		activeDuels.remove(duel.getDuelId());
		playerToDuel.remove(duel.getChallenger().getUniqueId());
		playerToDuel.remove(duel.getOpponent().getUniqueId());
	}

	/**
	 * Gets the active duel for a player.
	 */
	public ActiveDuel getActiveDuel(Player player) {
		UUID duelId = playerToDuel.get(player.getUniqueId());
		if (duelId == null)
			return null;
		return activeDuels.get(duelId);
	}

	/**
	 * Gets the active duel for a player by UUID.
	 */
	public ActiveDuel getActiveDuel(UUID playerId) {
		UUID duelId = playerToDuel.get(playerId);
		if (duelId == null)
			return null;
		return activeDuels.get(duelId);
	}

	/**
	 * Checks if a player is in a duel.
	 */
	public boolean isInDuel(Player player) {
		return playerToDuel.containsKey(player.getUniqueId());
	}

	/**
	 * Checks if a player is in a duel by UUID.
	 */
	public boolean isInDuel(UUID playerId) {
		return playerToDuel.containsKey(playerId);
	}

	/**
	 * Checks if a player has a pending outgoing request.
	 */
	private boolean hasPendingRequest(Player player) {
		for (DuelRequest request : pendingRequests.values()) {
			if (request.getChallengerId().equals(player.getUniqueId())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Handles player logout during a duel.
	 */
	public void handlePlayerQuit(Player player) {
		ActiveDuel duel = getActiveDuel(player);
		if (duel != null && !duel.hasEnded()) {
			duel.forfeit(player);
		}

		// Also cancel any pending requests involving this player
		pendingRequests.entrySet().removeIf(entry -> entry.getValue().getChallengerId().equals(player.getUniqueId()) ||
				entry.getKey().equals(player.getUniqueId()));
	}

	/**
	 * Handles player death during a duel (or "virtual" death when lethal damage was cancelled).
	 */
	public void handlePlayerDeath(Player player, Player killer) {
		ActiveDuel duel = getActiveDuel(player);
		if (duel != null && (duel.isFighting() || duel.getState() == ActiveDuel.DuelState.ACTIVE)) {
			Player opponent = duel.getOpponent(player);
			if (opponent != null) {
				duel.declareWinner(opponent, player);
			}
		}
	}

	/**
	 * Teleports the player back immediately (from return countdown). Used by /duel return or clickable message.
	 */
	public boolean returnNow(Player player) {
		ActiveDuel duel = getActiveDuel(player);
		return duel != null && duel.returnNow(player);
	}

	/**
	 * Gets all active duels.
	 */
	public Collection<ActiveDuel> getActiveDuels() {
		return Collections.unmodifiableCollection(activeDuels.values());
	}

	/**
	 * Gets the number of active duels.
	 */
	public int getActiveDuelCount() {
		return activeDuels.size();
	}

	/**
	 * Cleans up all active duels (for plugin disable).
	 */
	public void cleanup() {
		for (ActiveDuel duel : new ArrayList<>(activeDuels.values())) {
			if (!duel.hasEnded()) {
				duel.endDuel();
			}
		}
		activeDuels.clear();
		playerToDuel.clear();
		pendingRequests.clear();
	}
}
