package games.coob.smp.duel;

import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages the random matchmaking queue for duels.
 */
public class DuelQueueManager {

	@Getter
	private static final DuelQueueManager instance = new DuelQueueManager();

	private final Queue<UUID> queue = new ConcurrentLinkedQueue<>();
	private BukkitTask matchCheckTask;

	private DuelQueueManager() {
	}

	/**
	 * Starts the queue matching system.
	 */
	public void start() {
		if (matchCheckTask != null) {
			matchCheckTask.cancel();
		}

		int interval = Settings.DuelSection.QUEUE_MATCH_CHECK_INTERVAL;
		matchCheckTask = SchedulerUtil.runTimer(20L * interval, 20L * interval, this::checkForMatches);
	}

	/**
	 * Stops the queue matching system.
	 */
	public void stop() {
		if (matchCheckTask != null) {
			matchCheckTask.cancel();
			matchCheckTask = null;
		}
		queue.clear();
	}

	/**
	 * Adds a player to the queue.
	 */
	public void joinQueue(Player player) {
		if (!Settings.DuelSection.ENABLE_DUELS) {
			ColorUtil.sendMessage(player, "&cDuels are currently disabled.");
			return;
		}

		UUID playerId = player.getUniqueId();

		if (DuelManager.getInstance().isInDuel(player)) {
			ColorUtil.sendMessage(player, "&cYou are already in a duel.");
			return;
		}

		if (queue.contains(playerId)) {
			ColorUtil.sendMessage(player, "&cYou are already in the queue.");
			return;
		}

		queue.add(playerId);
		ColorUtil.sendMessage(player, "&aYou joined the duel queue! &7(" + queue.size() + " players in queue)");

		// Try to find a match immediately
		checkForMatches();
	}

	/**
	 * Removes a player from the queue.
	 */
	public void leaveQueue(Player player) {
		if (queue.remove(player.getUniqueId())) {
			ColorUtil.sendMessage(player, "&cYou left the duel queue.");
		} else {
			ColorUtil.sendMessage(player, "&cYou are not in the queue.");
		}
	}

	/**
	 * Checks if a player is in the queue.
	 */
	public boolean isInQueue(Player player) {
		return queue.contains(player.getUniqueId());
	}

	/**
	 * Checks if a player is in the queue by UUID.
	 */
	public boolean isInQueue(UUID playerId) {
		return queue.contains(playerId);
	}

	/**
	 * Gets the current queue size.
	 */
	public int getQueueSize() {
		return queue.size();
	}

	/**
	 * Gets the player's position in the queue (1-indexed).
	 */
	public int getQueuePosition(Player player) {
		int position = 1;
		for (UUID id : queue) {
			if (id.equals(player.getUniqueId())) {
				return position;
			}
			position++;
		}
		return -1;
	}

	/**
	 * Checks for possible matches and starts duels.
	 */
	private void checkForMatches() {
		int minPlayers = Settings.DuelSection.QUEUE_MIN_PLAYERS;

		// Clean up offline players from queue
		queue.removeIf(id -> {
			Player player = Bukkit.getPlayer(id);
			return player == null || !player.isOnline() || DuelManager.getInstance().isInDuel(id);
		});

		// Check if we have enough players
		while (queue.size() >= minPlayers) {
			// Get two players from queue
			UUID player1Id = queue.poll();
			UUID player2Id = queue.poll();

			if (player1Id == null || player2Id == null)
				break;

			Player player1 = Bukkit.getPlayer(player1Id);
			Player player2 = Bukkit.getPlayer(player2Id);

			// Validate players
			if (player1 == null || !player1.isOnline()) {
				if (player2Id != null)
					queue.add(player2Id); // Return player2 to queue
				continue;
			}

			if (player2 == null || !player2.isOnline()) {
				queue.add(player1Id); // Return player1 to queue
				continue;
			}

			// Check if either is already in a duel
			if (DuelManager.getInstance().isInDuel(player1)) {
				if (player2Id != null)
					queue.add(player2Id);
				continue;
			}

			if (DuelManager.getInstance().isInDuel(player2)) {
				queue.add(player1Id);
				continue;
			}

			// Start the match!
			ColorUtil.sendMessage(player1, "&a&lMatch found! &eYou will be dueling &6" + player2.getName() + "&e!");
			ColorUtil.sendMessage(player2, "&a&lMatch found! &eYou will be dueling &6" + player1.getName() + "&e!");

			// Use DuelManager to start the duel
			startQueueMatch(player1, player2);
		}
	}

	/**
	 * Starts a match between two queued players.
	 */
	private void startQueueMatch(Player player1, Player player2) {
		// Directly start a duel without the request flow
		Settings.DuelSection.ArenaMode mode = Settings.DuelSection.ARENA_MODE;

		switch (mode) {
			case NATURAL -> startNaturalDuel(player1, player2);
			case CREATED -> startCreatedArenaDuel(player1, player2);
			case RANDOM -> {
				if (!games.coob.smp.duel.model.ArenaRegistry.getInstance().getReadyArenas().isEmpty()
						&& Math.random() < 0.5) {
					startCreatedArenaDuel(player1, player2);
				} else {
					startNaturalDuel(player1, player2);
				}
			}
		}
	}

	private void startNaturalDuel(Player player1, Player player2) {
		NaturalTeleporter.teleportPlayers(player1, player2).thenAccept(result -> {
			if (!result.success) {
				ColorUtil.sendMessage(player1, "&cFailed to find arena location. Returned to queue.");
				ColorUtil.sendMessage(player2, "&cFailed to find arena location. Returned to queue.");
				// Return players to queue
				queue.add(player1.getUniqueId());
				queue.add(player2.getUniqueId());
				return;
			}

			ActiveDuel duel = new ActiveDuel(player1, player2, null, result.center, result.usedChunks);
			registerDuel(duel);
			duel.startCountdown();
		});
	}

	private void startCreatedArenaDuel(Player player1, Player player2) {
		games.coob.smp.duel.model.ArenaData arena = games.coob.smp.duel.model.ArenaRegistry.getInstance()
				.getRandomArena();

		if (arena == null) {
			startNaturalDuel(player1, player2);
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
				ActiveDuel duel = new ActiveDuel(player1, player2, arena, arena.getCenter(), null);
				registerDuel(duel);
				duel.startCountdown();
			}
		};

		DuelFlyDownTeleporter.teleportToGround(player1, spawn1, onBothLanded);
		DuelFlyDownTeleporter.teleportToGround(player2, spawn2, onBothLanded);
	}

	private void registerDuel(ActiveDuel duel) {
		DuelManager.getInstance().registerDuel(duel);
	}

	/**
	 * Removes a player from the queue when they disconnect.
	 */
	public void handlePlayerQuit(Player player) {
		queue.remove(player.getUniqueId());
	}
}
