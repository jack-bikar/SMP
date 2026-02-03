package games.coob.smp.duel;

import games.coob.smp.PlayerCache;
import games.coob.smp.duel.model.ArenaData;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import lombok.Getter;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Represents an active duel between two players.
 * Manages the entire lifecycle of a duel from start to cleanup.
 */
@Getter
public class ActiveDuel {

	/**
	 * Duel states.
	 */
	public enum DuelState {
		COUNTDOWN, // Pre-fight countdown
		ACTIVE, // Fight in progress
		LOOT_PHASE, // Winner collecting loot
		ENDED // Duel has ended
	}

	private final UUID duelId;
	private final Player challenger;
	private final Player opponent;
	private final ArenaData arena; // null for natural arenas
	private final Location center;
	private final DuelBorder border;

	private DuelState state;
	private long startTime;
	private Player winner;
	private Player loser;

	// Tracking for cleanup
	private final Set<Location> placedBlocks = new HashSet<>();
	private final Set<UUID> droppedItems = new HashSet<>();
	private final Set<UUID> spawnedEntities = new HashSet<>();
	private final Set<Chunk> usedChunks = new HashSet<>();

	// Original player state for restoration
	private final Map<UUID, ItemStack[]> originalInventories = new HashMap<>();
	private final Map<UUID, ItemStack[]> originalArmor = new HashMap<>();
	private final Map<UUID, Location> originalLocations = new HashMap<>();
	private final Map<UUID, Double> originalHealth = new HashMap<>();

	// Tasks
	private BukkitTask countdownTask;
	private BukkitTask lootPhaseTask;

	/**
	 * Creates a new active duel.
	 *
	 * @param challenger The player who sent the duel request
	 * @param opponent   The player who accepted the duel
	 * @param arena      The arena (null for natural mode)
	 * @param center     The center location for the border
	 * @param usedChunks Chunks used by this duel (for natural arenas)
	 */
	public ActiveDuel(Player challenger, Player opponent, ArenaData arena, Location center, Set<Chunk> usedChunks) {
		this.duelId = UUID.randomUUID();
		this.challenger = challenger;
		this.opponent = opponent;
		this.arena = arena;
		this.center = center;
		this.border = new DuelBorder(center);
		this.state = DuelState.COUNTDOWN;
		this.startTime = System.currentTimeMillis();

		if (usedChunks != null) {
			this.usedChunks.addAll(usedChunks);
		}

		// Save original state
		savePlayerState(challenger);
		savePlayerState(opponent);

		// Mark players as in duel
		markPlayerInDuel(challenger, opponent);
		markPlayerInDuel(opponent, challenger);
	}

	/**
	 * Saves a player's state for potential restoration.
	 */
	private void savePlayerState(Player player) {
		originalInventories.put(player.getUniqueId(), player.getInventory().getContents().clone());
		originalArmor.put(player.getUniqueId(), player.getInventory().getArmorContents().clone());
		originalLocations.put(player.getUniqueId(), player.getLocation().clone());
		originalHealth.put(player.getUniqueId(), player.getHealth());
	}

	/**
	 * Marks a player as being in a duel.
	 */
	private void markPlayerInDuel(Player player, Player opponent) {
		PlayerCache cache = PlayerCache.from(player);
		cache.setInDuel(true);
		cache.setDuelOpponent(opponent.getUniqueId());
		cache.setActiveDuelId(duelId);
	}

	/**
	 * Clears a player's duel state.
	 */
	private void clearPlayerDuelState(Player player) {
		PlayerCache cache = PlayerCache.from(player);
		cache.setInDuel(false);
		cache.setDuelOpponent(null);
		cache.setActiveDuelId(null);
	}

	/**
	 * Starts the countdown before the duel begins.
	 */
	public void startCountdown() {
		this.state = DuelState.COUNTDOWN;
		int countdownSeconds = Settings.DuelSection.COUNTDOWN_SECONDS;

		countdownTask = SchedulerUtil.runTimer(0, 20, new Runnable() {
			private int remaining = countdownSeconds;

			@Override
			public void run() {
				if (remaining <= 0) {
					countdownTask.cancel();
					startFight();
					return;
				}

				String color = remaining <= 3 ? "&c" : "&e";
				ColorUtil.sendMessage(challenger, color + "&l" + remaining + "...");
				ColorUtil.sendMessage(opponent, color + "&l" + remaining + "...");
				remaining--;
			}
		});
	}

	/**
	 * Starts the actual fight.
	 */
	private void startFight() {
		this.state = DuelState.ACTIVE;
		this.startTime = System.currentTimeMillis();

		ColorUtil.sendMessage(challenger, "&a&lFIGHT!");
		ColorUtil.sendMessage(opponent, "&a&lFIGHT!");

		// Start border
		border.start(challenger, opponent);

		// Start border shrinking after a short delay
		SchedulerUtil.runLater(60, border::startShrinking); // Start shrinking after 3 seconds
	}

	/**
	 * Called when a player wins the duel.
	 *
	 * @param winner The winning player
	 * @param loser  The losing player
	 */
	public void declareWinner(Player winner, Player loser) {
		if (state == DuelState.ENDED || state == DuelState.LOOT_PHASE)
			return;

		this.winner = winner;
		this.loser = loser;

		// Stop countdown if still running
		if (countdownTask != null) {
			countdownTask.cancel();
		}

		// Handle based on loot mode
		Settings.DuelSection.LootMode lootMode = Settings.DuelSection.LOOT_MODE;

		switch (lootMode) {
			case DROP_ITEMS -> {
				// Items already dropped by death
				this.state = DuelState.LOOT_PHASE;
				startLootPhase();
			}
			case KEEP_INVENTORY -> {
				// Loser keeps their items (restore from saved state)
				restorePlayerInventory(loser);
				endDuel();
			}
			case LOOT_PHASE -> {
				this.state = DuelState.LOOT_PHASE;
				startLootPhase();
			}
		}

		// Winner announcement
		ColorUtil.sendMessage(winner, "&a&lYou won the duel against &e" + loser.getName() + "&a!");
		ColorUtil.sendMessage(loser, "&c&lYou lost the duel against &e" + winner.getName() + "&c!");
	}

	/**
	 * Starts the loot collection phase.
	 */
	private void startLootPhase() {
		int lootPhaseSeconds = Settings.DuelSection.LOOT_PHASE_SECONDS;

		ColorUtil.sendMessage(winner, "&e&lLoot phase! You have &6" + lootPhaseSeconds +
				" &e&lseconds to collect items. Type &6/duel leave &eto exit early.");

		lootPhaseTask = SchedulerUtil.runLater(20L * lootPhaseSeconds, this::endDuel);
	}

	/**
	 * Called when the winner wants to leave the loot phase early.
	 */
	public void leaveLootPhase(Player player) {
		if (state != DuelState.LOOT_PHASE)
			return;
		if (!player.equals(winner))
			return;

		if (lootPhaseTask != null) {
			lootPhaseTask.cancel();
		}

		endDuel();
	}

	/**
	 * Called when a player forfeits (disconnect, etc.).
	 *
	 * @param forfeitPlayer The player who forfeited
	 */
	public void forfeit(Player forfeitPlayer) {
		if (state == DuelState.ENDED)
			return;

		Player otherPlayer = forfeitPlayer.equals(challenger) ? opponent : challenger;

		ColorUtil.sendMessage(forfeitPlayer, "&c&lYou forfeited the duel!");
		if (otherPlayer.isOnline()) {
			ColorUtil.sendMessage(otherPlayer, "&a&l" + forfeitPlayer.getName() + " forfeited! You win!");
		}

		this.winner = otherPlayer;
		this.loser = forfeitPlayer;

		// Immediately end the duel on forfeit
		endDuel();
	}

	/**
	 * Ends the duel and performs cleanup.
	 */
	public void endDuel() {
		if (state == DuelState.ENDED)
			return;
		this.state = DuelState.ENDED;

		// Stop border
		border.stop();

		// Cancel any running tasks
		if (countdownTask != null)
			countdownTask.cancel();
		if (lootPhaseTask != null)
			lootPhaseTask.cancel();

		// Update statistics
		if (winner != null && loser != null) {
			games.coob.smp.duel.model.DuelStatistics.getInstance().addWin(winner.getUniqueId());
			games.coob.smp.duel.model.DuelStatistics.getInstance().addLoss(loser.getUniqueId());
		}

		// Teleport players to lobby or original location
		teleportPlayersBack();

		// Clear player states
		clearPlayerDuelState(challenger);
		clearPlayerDuelState(opponent);

		// Perform cleanup
		SchedulerUtil.runLater(20, this::performCleanup);

		// Notify DuelManager
		DuelManager.getInstance().onDuelEnded(this);
	}

	/**
	 * Teleports players back to lobby or original location.
	 */
	private void teleportPlayersBack() {
		Location lobby = games.coob.smp.duel.model.ArenaRegistry.getInstance().getLobbySpawn();

		for (Player player : new Player[] { challenger, opponent }) {
			if (player == null || !player.isOnline())
				continue;

			Location destination = lobby;
			if (destination == null) {
				destination = originalLocations.get(player.getUniqueId());
			}
			if (destination == null) {
				destination = player.getWorld().getSpawnLocation();
			}

			player.teleport(destination);

			// Restore health for winner (if keeping inventory)
			if (player.equals(winner) && Settings.DuelSection.WINNER_KEEPS_INVENTORY) {
				Double originalHp = originalHealth.get(player.getUniqueId());
				if (originalHp != null) {
					player.setHealth(Math.min(originalHp, player.getMaxHealth()));
				}
			}
		}
	}

	/**
	 * Restores a player's inventory from saved state.
	 */
	private void restorePlayerInventory(Player player) {
		ItemStack[] inventory = originalInventories.get(player.getUniqueId());
		ItemStack[] armor = originalArmor.get(player.getUniqueId());

		if (inventory != null) {
			player.getInventory().setContents(inventory);
		}
		if (armor != null) {
			player.getInventory().setArmorContents(armor);
		}
	}

	/**
	 * Performs cleanup after the duel ends.
	 */
	private void performCleanup() {
		// Remove placed blocks
		if (Settings.DuelSection.CLEANUP_REMOVE_PLACED_BLOCKS) {
			for (Location loc : placedBlocks) {
				Block block = loc.getBlock();
				if (block.getType() != Material.AIR) {
					block.setType(Material.AIR);
				}
			}
		}

		// Remove dropped items
		if (Settings.DuelSection.CLEANUP_REMOVE_DROPPED_ITEMS) {
			for (UUID itemId : droppedItems) {
				Entity entity = org.bukkit.Bukkit.getEntity(itemId);
				if (entity instanceof Item) {
					entity.remove();
				}
			}
		}

		// Remove spawned entities (arrows, etc.)
		if (Settings.DuelSection.CLEANUP_REMOVE_ENTITIES) {
			for (UUID entityId : spawnedEntities) {
				Entity entity = org.bukkit.Bukkit.getEntity(entityId);
				if (entity != null && !(entity instanceof Player)) {
					entity.remove();
				}
			}
		}

		// Unload natural arena chunks
		if (arena == null) {
			NaturalTeleporter.unloadChunks(usedChunks);
		}
	}

	/**
	 * Tracks a block placed during the duel.
	 */
	public void trackPlacedBlock(Location location) {
		placedBlocks.add(location.clone());
	}

	/**
	 * Tracks an item dropped during the duel.
	 */
	public void trackDroppedItem(UUID itemId) {
		droppedItems.add(itemId);
	}

	/**
	 * Tracks an entity spawned during the duel (arrows, potions, etc.).
	 */
	public void trackSpawnedEntity(UUID entityId) {
		spawnedEntities.add(entityId);
	}

	/**
	 * Checks if a player is part of this duel.
	 */
	public boolean hasPlayer(Player player) {
		return player.equals(challenger) || player.equals(opponent);
	}

	/**
	 * Checks if a player is part of this duel by UUID.
	 */
	public boolean hasPlayer(UUID playerId) {
		return playerId.equals(challenger.getUniqueId()) || playerId.equals(opponent.getUniqueId());
	}

	/**
	 * Gets the opponent of a player in this duel.
	 */
	public Player getOpponent(Player player) {
		if (player.equals(challenger))
			return opponent;
		if (player.equals(opponent))
			return challenger;
		return null;
	}

	/**
	 * Gets duel duration in seconds.
	 */
	public int getDurationSeconds() {
		return (int) ((System.currentTimeMillis() - startTime) / 1000);
	}

	/**
	 * Checks if the duel is in an active fighting state.
	 */
	public boolean isFighting() {
		return state == DuelState.ACTIVE;
	}

	/**
	 * Checks if the duel has ended.
	 */
	public boolean hasEnded() {
		return state == DuelState.ENDED;
	}
}
