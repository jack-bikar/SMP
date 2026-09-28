package games.coob.smp.duel;

import games.coob.smp.PlayerCache;
import games.coob.smp.SMPPlugin;
import games.coob.smp.duel.model.DuelStatistics;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A duel between two sides (one player each for a 1v1, or teams), from preparing
 * the arena to sending everyone home.
 * <p>
 * Each player's location, game mode, health and hunger are saved when the duel
 * is created, before anyone is moved. The return location is also written to
 * the player's data file, so players who disconnect (or a crash) are still sent
 * back on their next join.
 */
public final class ActiveDuel {

	public enum DuelState {
		PREPARING, // Finding the arena and bringing players in
		COUNTDOWN, // Pre-fight countdown
		ACTIVE, // Fight in progress
		ENDING, // Duel decided, waiting before sending players back
		ENDED
	}

	private record SavedState(Location location, GameMode gameMode, double health, int food, float saturation) {
	}

	@Getter
	private final UUID duelId = UUID.randomUUID();
	@Getter
	private DuelState state = DuelState.PREPARING;
	@Getter
	private DuelArena arena;
	@Getter
	private DuelBorder border;
	@Getter
	private DuelSide winningSide;

	private final Map<UUID, Player> players = new LinkedHashMap<>();
	private final Map<UUID, DuelSide> sides = new HashMap<>();
	private final Map<UUID, SavedState> savedStates = new HashMap<>();

	/** Knocked out of the fight (spectating until the duel ends). */
	private final Set<UUID> eliminated = new HashSet<>();
	/** Disconnected during the duel; they are sent back when they join again. */
	private final Set<UUID> left = new HashSet<>();
	/** Already sent back (or will be on join/respawn). */
	private final Set<UUID> returned = new HashSet<>();
	/** Landed in the arena after the fly-down. */
	private final Set<UUID> landed = new HashSet<>();

	// Tracked for cleanup
	/** Block location -> what was there before the duel changed it, and who placed there first. */
	private final Map<Location, PlacedBlock> placedBlocks = new HashMap<>();

	private record PlacedBlock(BlockState original, UUID placer) {
	}

	/** Half the arena's width, set when the arena is known. */
	private int arenaRadius;
	private final Set<UUID> droppedItems = new HashSet<>();
	private final Set<UUID> spawnedEntities = new HashSet<>();

	private final List<BukkitTask> tasks = new ArrayList<>();

	ActiveDuel(List<Player> red, List<Player> blue) {
		add(red, DuelSide.RED);
		add(blue, DuelSide.BLUE);
	}

	private void add(List<Player> team, DuelSide side) {
		for (Player player : team) {
			players.put(player.getUniqueId(), player);
			sides.put(player.getUniqueId(), side);
			savedStates.put(player.getUniqueId(), new SavedState(player.getLocation(), player.getGameMode(),
					player.getHealth(), player.getFoodLevel(), player.getSaturation()));
			PlayerCache.from(player).setDuelReturn(player.getLocation(), player.getGameMode());
		}
	}

	// -------------------------------------------------------------------------
	// Lifecycle
	// -------------------------------------------------------------------------

	/**
	 * The arena is ready: bring every player in from the sky.
	 */
	void begin(DuelArena arena) {
		if (state != DuelState.PREPARING)
			return;
		this.arena = arena;
		this.arenaRadius = arena.borderRadius(Settings.DuelSection.BORDER_RADIUS, getLargestTeamSize());

		for (DuelSide side : DuelSide.values()) {
			List<Player> team = getTeam(side);
			List<Location> spots = arena.spots(side, team.size());
			for (int i = 0; i < team.size(); i++) {
				Player player = team.get(i);
				if (left.contains(player.getUniqueId()))
					continue;
				tasks.add(DuelFlyDownTeleporter.descend(player, spots.get(i), () -> {
					landed.add(player.getUniqueId());
					checkEveryoneLanded();
				}));
			}
		}
	}

	private void checkEveryoneLanded() {
		if (state != DuelState.PREPARING || arena == null)
			return;
		for (UUID id : players.keySet()) {
			if (!left.contains(id) && !landed.contains(id))
				return;
		}
		startCountdown();
	}

	private void startCountdown() {
		state = DuelState.COUNTDOWN;

		if (Settings.DuelSection.BORDER_ENABLED) {
			border = new DuelBorder(arena.center(), arenaRadius);
			border.start(() -> state == DuelState.ACTIVE, activePlayers());
		}

		if (isTeamDuel()) {
			for (Player player : activePlayers()) {
				DuelSide side = getSide(player);
				ColorUtil.sendMessage(player, "&7You are on the " + side.coloredName() + " &7team with: &f"
						+ names(getTeam(side), player));
			}
		}

		final int[] remaining = { Settings.DuelSection.COUNTDOWN_SECONDS };
		final BukkitTask[] task = new BukkitTask[1];
		task[0] = SchedulerUtil.runTimer(0, 20, () -> {
			if (state != DuelState.COUNTDOWN) {
				task[0].cancel();
				return;
			}
			if (remaining[0] <= 0) {
				task[0].cancel();
				startFight();
				return;
			}
			NamedTextColor color = remaining[0] <= 3 ? NamedTextColor.RED : NamedTextColor.YELLOW;
			showTitle(activePlayers(), Component.text(remaining[0], color), Component.empty(), 0, 1000, 200);
			remaining[0]--;
		});
		tasks.add(task[0]);
	}

	private void startFight() {
		state = DuelState.ACTIVE;
		showTitle(activePlayers(), Component.text("FIGHT!", NamedTextColor.GREEN), Component.empty(), 0, 1000, 300);

		int minutes = Settings.DuelSection.MAX_FIGHT_MINUTES;
		if (minutes > 0)
			tasks.add(SchedulerUtil.runLater(20L * 60 * minutes, () -> {
				if (state == DuelState.ACTIVE)
					cancel("&eTime's up! The duel ended in a draw after " + minutes + " minutes.");
			}));
	}

	/**
	 * A player is out of the fight: their hit would have killed them, they died,
	 * or they left. When a whole side is out, the other side wins.
	 */
	void eliminate(Player victim, Player killer) {
		UUID id = victim.getUniqueId();
		if (state != DuelState.ACTIVE || eliminated.contains(id))
			return;
		eliminated.add(id);

		// A player who actually died already dropped their items through the death event
		if (Settings.DuelSection.LOOT_MODE == Settings.DuelSection.LootMode.DROP_ITEMS && !victim.isDead())
			dropInventory(victim);

		// Knocked-out players stay inside the border (as spectators) until they are sent back
		DuelSide side = getSide(victim);
		boolean sideOut = getTeam(side).stream().allMatch(p -> eliminated.contains(p.getUniqueId()));

		if (victim.isOnline() && !victim.isDead() && !left.contains(id)) {
			// Spectators can't pick things up (a Loyalty trident would be lost), so hand them back now
			returnProjectilesOf(victim);
			victim.setHealth(maxHealth(victim));
			victim.setFireTicks(0);
			victim.setGameMode(GameMode.SPECTATOR);
			if (isTeamDuel() && !sideOut)
				victim.showTitle(Title.title(Component.text("Eliminated", NamedTextColor.RED),
						Component.text("Spectating until the duel ends", NamedTextColor.GRAY)));
		}

		if (isTeamDuel()) {
			String how = left.contains(id) ? " &7left the duel"
					: killer != null && getSide(killer) != null
							? " &7was eliminated by " + getSide(killer).getColorCode() + killer.getName()
							: " &7was eliminated";
			broadcast(side.getColorCode() + victim.getName() + how + " &8(" + scoreLine() + "&8)");
		}

		if (sideOut)
			finish(side.other());
	}

	/**
	 * One side won: stats, titles and the countdown before everyone is sent back.
	 */
	private void finish(DuelSide winners) {
		state = DuelState.ENDING;
		winningSide = winners;

		for (Player player : players.values()) {
			if (getSide(player) == winners) {
				DuelStatistics.getInstance().addWin(player.getUniqueId());
			} else {
				DuelStatistics.getInstance().addLoss(player.getUniqueId());
			}
		}

		// The border stays up until everyone is sent back, so spectators can't fly off
		Title.Times times = Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500));
		for (Player player : onlinePlayers()) {
			boolean won = getSide(player) == winners;
			Component subtitle = isTeamDuel()
					? Component.text(winners.getDisplayName() + " team wins", winners.getColor())
					: Component.text("against " + names(getTeam(getSide(player).other()), null), NamedTextColor.GRAY);
			Component title = won
					? Component.text(isTeamDuel() ? "Victory!" : "You won!", NamedTextColor.GREEN)
					: Component.text(isTeamDuel() ? "Defeat" : "You lost", NamedTextColor.RED);
			player.showTitle(Title.title(title, subtitle, times));
		}

		int seconds = Settings.DuelSection.END_RETURN_COUNTDOWN_SECONDS;
		Component returnButton = Component.text("[Return now]", NamedTextColor.GREEN)
				.hoverEvent(HoverEvent.showText(Component.text("Teleport back to where you were")))
				.clickEvent(ClickEvent.runCommand("/duel return"));
		for (Player player : onlinePlayers()) {
			if (!returned.contains(player.getUniqueId())) {
				ColorUtil.sendMessage(player, "&eYou will be sent back in &6" + seconds + " &eseconds.");
				player.sendMessage(returnButton);
			}
		}

		if (everyoneReturned()) {
			end();
			return;
		}
		tasks.add(SchedulerUtil.runLater(20L * seconds, this::end));
	}

	/**
	 * Stops a duel that hasn't been decided (arena not found, a side left...).
	 * No stats are recorded.
	 */
	void cancel(String reason) {
		if (state == DuelState.ENDED)
			return;
		for (Player player : onlinePlayers())
			ColorUtil.sendMessage(player, reason);
		end();
	}

	/**
	 * Sends everyone back, removes the border and cleans up the arena.
	 */
	void end() {
		if (state == DuelState.ENDED)
			return;
		state = DuelState.ENDED;

		for (BukkitTask task : tasks)
			task.cancel();
		tasks.clear();

		if (border != null)
			border.stop();

		boolean shuttingDown = !SMPPlugin.getInstance().isEnabled();
		for (Player player : onlinePlayers())
			returnPlayer(player, shuttingDown);

		DuelManager.getInstance().onDuelEnded(this);

		if (shuttingDown) {
			performCleanup();
		} else {
			SchedulerUtil.runLater(20, this::performCleanup);
		}
	}

	// -------------------------------------------------------------------------
	// Player events
	// -------------------------------------------------------------------------

	/**
	 * A duelist disconnected. They are sent back when they next join.
	 */
	void handleQuit(Player player) {
		UUID id = player.getUniqueId();
		left.add(id);
		returned.add(id);
		if (border != null)
			border.remove(player);

		switch (state) {
			case PREPARING, COUNTDOWN -> {
				if (sideEmpty(DuelSide.RED) || sideEmpty(DuelSide.BLUE)) {
					cancel("&c" + player.getName() + " left, the duel was cancelled.");
				} else {
					// Counts as out, so their team can still lose once the others are knocked out
					eliminated.add(id);
					broadcast("&c" + player.getName() + " left the duel.");
					checkEveryoneLanded();
				}
			}
			case ACTIVE -> {
				if (!isTeamDuel()) {
					for (Player other : getTeam(getSide(player).other())) {
						if (other.isOnline())
							ColorUtil.sendMessage(other, "&a" + player.getName() + " left the duel. You win!");
					}
				}
				eliminate(player, null);
			}
			case ENDING -> {
				if (everyoneReturned())
					end();
			}
			default -> {
			}
		}
	}

	/** Whether a side has nobody left in the duel. */
	private boolean sideEmpty(DuelSide side) {
		return getTeam(side).stream().allMatch(p -> left.contains(p.getUniqueId()));
	}

	/**
	 * A duelist respawned after dying and was sent back by the respawn handler.
	 */
	void markReturned(Player player) {
		returned.add(player.getUniqueId());
		if (border != null)
			border.remove(player);
		DuelManager.getInstance().detach(player, duelId);
		if (state == DuelState.ENDING && everyoneReturned())
			end();
	}

	/**
	 * /duel return during the end countdown.
	 */
	boolean returnNow(Player player) {
		if (state != DuelState.ENDING || returned.contains(player.getUniqueId()))
			return false;

		returnPlayer(player, false);
		if (everyoneReturned())
			end();
		return true;
	}

	private boolean everyoneReturned() {
		return returned.size() >= players.size();
	}

	/**
	 * Sends a player back to where they were before the duel and restores their
	 * game mode, health and hunger.
	 */
	void returnPlayer(Player player, boolean immediately) {
		if (!returned.add(player.getUniqueId()))
			return;
		if (border != null)
			border.remove(player);
		// No longer part of the duel: their blocks and commands at home are their own again
		DuelManager.getInstance().detach(player, duelId);
		// A pearl still in flight would pull them back to the arena
		for (Entity pearl : new ArrayList<>(player.getEnderPearls()))
			pearl.remove();

		// Dead players are sent back when they respawn, using the saved return location
		if (player.isDead())
			return;

		SavedState saved = savedStates.get(player.getUniqueId());
		if (saved == null)
			return;

		Runnable restore = () -> {
			if (!player.isOnline())
				return;
			// Only forget the return spot once they are actually home (kept for next join otherwise)
			PlayerCache.from(player).setDuelReturn(null, null);
			player.setGameMode(saved.gameMode());
			if (!player.isDead()) {
				player.setHealth(Math.min(saved.health(), maxHealth(player)));
				player.setFoodLevel(saved.food());
				player.setSaturation(saved.saturation());
			}
			player.setFireTicks(0);
			player.setFallDistance(0);
		};

		// If the teleport fails, at least don't leave them in spectator; they are sent back on next join
		Runnable failed = () -> {
			if (!player.isOnline())
				return;
			player.setGameMode(saved.gameMode());
			ColorUtil.sendMessage(player, "&cCouldn't send you back right now; you'll be returned when you rejoin.");
		};

		if (immediately) {
			if (player.teleport(saved.location())) {
				restore.run();
			} else {
				failed.run();
			}
		} else {
			player.teleportAsync(saved.location()).thenAccept(success -> {
				if (success) {
					restore.run();
				} else {
					failed.run();
				}
			});
		}
	}

	private static double maxHealth(Player player) {
		AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
		return attribute != null ? attribute.getValue() : 20.0;
	}

	private void dropInventory(Player player) {
		Location location = player.getLocation();
		for (ItemStack item : player.getInventory().getContents()) {
			if (item != null && !item.getType().isAir())
				location.getWorld().dropItemNaturally(location, item);
		}
		player.getInventory().clear();
	}

	// -------------------------------------------------------------------------
	// Messages
	// -------------------------------------------------------------------------

	private void broadcast(String message) {
		for (Player player : onlinePlayers())
			ColorUtil.sendMessage(player, message);
	}

	/** e.g. "Red 2 - 1 Blue" (players still fighting). */
	private String scoreLine() {
		return DuelSide.RED.coloredName() + " " + fighting(DuelSide.RED) + " &7- " + DuelSide.BLUE.getColorCode()
				+ fighting(DuelSide.BLUE) + " " + DuelSide.BLUE.getDisplayName();
	}

	private long fighting(DuelSide side) {
		return getTeam(side).stream().filter(p -> !eliminated.contains(p.getUniqueId())).count();
	}

	private static String names(List<Player> team, Player except) {
		List<String> names = new ArrayList<>();
		for (Player player : team) {
			if (!player.equals(except))
				names.add(player.getName());
		}
		return names.isEmpty() ? "nobody" : String.join(", ", names);
	}

	private static void showTitle(Collection<Player> to, Component title, Component subtitle, long fadeIn, long stay,
			long fadeOut) {
		Title shown = Title.title(title, subtitle,
				Title.Times.times(Duration.ofMillis(fadeIn), Duration.ofMillis(stay), Duration.ofMillis(fadeOut)));
		for (Player player : to)
			player.showTitle(shown);
	}

	// -------------------------------------------------------------------------
	// Cleanup
	// -------------------------------------------------------------------------

	private void performCleanup() {
		if (Settings.DuelSection.CLEANUP_REMOVE_PLACED_BLOCKS) {
			for (Map.Entry<Location, PlacedBlock> entry : placedBlocks.entrySet()) {
				if (entry.getKey().isChunkLoaded())
					restoreBlock(entry.getKey().getBlock(), entry.getValue());
			}
		}
		if (Settings.DuelSection.CLEANUP_REMOVE_DROPPED_ITEMS)
			returnDroppedItems();
		if (Settings.DuelSection.CLEANUP_REMOVE_ENTITIES)
			removeProjectiles();

		placedBlocks.clear();
		droppedItems.clear();
		spawnedEntities.clear();
	}

	/**
	 * Puts back what was there before the duel. A container placed during the
	 * duel (chest, shulker box...) goes back to whoever placed it, with its contents.
	 */
	private void restoreBlock(Block block, PlacedBlock placed) {
		if (block.getType() != placed.original().getType() && block.getState() instanceof Container container) {
			List<ItemStack> items = new ArrayList<>();
			for (ItemStack item : container.getInventory().getContents()) {
				if (item != null && !item.isEmpty())
					items.add(item.clone());
			}
			container.getInventory().clear();
			items.add(new ItemStack(block.getType()));

			Player placer = Bukkit.getPlayer(placed.placer());
			for (ItemStack item : items) {
				if (placer != null) {
					give(placer, item);
				} else {
					block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), item);
				}
			}
		}
		placed.original().update(true, false);
	}

	/** Arrows and tridents a player shot go back to them (and are removed from the world). */
	private void returnProjectilesOf(Player player) {
		for (UUID id : spawnedEntities) {
			if (Bukkit.getEntity(id) instanceof AbstractArrow arrow && arrow.isValid()
					&& arrow.getPickupStatus() == AbstractArrow.PickupStatus.ALLOWED
					&& arrow.getShooter() instanceof Player shooter && shooter.equals(player)) {
				give(player, arrow.getItemStack());
				arrow.remove();
			}
		}
	}

	/** Items thrown on the ground during the fight go back to whoever threw them. */
	private void returnDroppedItems() {
		for (UUID id : droppedItems) {
			if (Bukkit.getEntity(id) instanceof Item item && item.isValid() && item.getThrower() != null) {
				Player owner = Bukkit.getPlayer(item.getThrower());
				if (owner != null) {
					give(owner, item.getItemStack());
					item.remove();
				}
			}
		}
	}

	/**
	 * Removes leftover projectiles. Ones that can be picked up (arrows, tridents)
	 * are given back to the shooter instead of being deleted.
	 */
	private void removeProjectiles() {
		for (UUID id : spawnedEntities) {
			Entity entity = Bukkit.getEntity(id);
			if (entity == null || entity instanceof Player || !entity.isValid())
				continue;
			if (entity instanceof AbstractArrow arrow && arrow.getPickupStatus() == AbstractArrow.PickupStatus.ALLOWED
					&& arrow.getShooter() instanceof Player shooter && shooter.isOnline())
				give(shooter, arrow.getItemStack());
			entity.remove();
		}
	}

	private static void give(Player player, ItemStack item) {
		for (ItemStack leftover : player.getInventory().addItem(item).values())
			player.getWorld().dropItemNaturally(player.getLocation(), leftover);
	}

	/**
	 * Remembers what was at a block before a duelist changed it, so cleanup can
	 * put it back. Only the first change counts, and only inside the arena.
	 */
	public void trackPlacedBlock(BlockState original, Player placer) {
		Location location = original.getLocation().toBlockLocation();
		if (arenaContains(location))
			placedBlocks.putIfAbsent(location, new PlacedBlock(original, placer.getUniqueId()));
	}

	public boolean isPlacedBlock(Location location) {
		return placedBlocks.containsKey(location.toBlockLocation());
	}

	/** Whether a location is inside this duel's arena (works with the border turned off too). */
	public boolean arenaContains(Location location) {
		if (arena == null || location.getWorld() == null || !location.getWorld().equals(arena.center().getWorld()))
			return false;
		return Math.abs(location.getX() - arena.center().getX()) <= arenaRadius
				&& Math.abs(location.getZ() - arena.center().getZ()) <= arenaRadius;
	}

	public void trackDroppedItem(UUID itemId) {
		droppedItems.add(itemId);
	}

	public void trackSpawnedEntity(UUID entityId) {
		spawnedEntities.add(entityId);
	}

	// -------------------------------------------------------------------------
	// Queries
	// -------------------------------------------------------------------------

	public Collection<Player> getPlayers() {
		return Collections.unmodifiableCollection(players.values());
	}

	public List<Player> getTeam(DuelSide side) {
		List<Player> team = new ArrayList<>();
		for (Player player : players.values()) {
			if (sides.get(player.getUniqueId()) == side)
				team.add(player);
		}
		return team;
	}

	public DuelSide getSide(Player player) {
		return sides.get(player.getUniqueId());
	}

	public boolean areTeammates(Player first, Player second) {
		DuelSide side = sides.get(first.getUniqueId());
		return side != null && side == sides.get(second.getUniqueId());
	}

	public boolean isTeamDuel() {
		return players.size() > 2;
	}

	public int getLargestTeamSize() {
		return Math.max(getTeam(DuelSide.RED).size(), getTeam(DuelSide.BLUE).size());
	}

	public boolean isEliminated(Player player) {
		return eliminated.contains(player.getUniqueId());
	}

	/** Online players who haven't left. */
	private List<Player> onlinePlayers() {
		List<Player> online = new ArrayList<>();
		for (Player player : players.values()) {
			if (player.isOnline() && !left.contains(player.getUniqueId()))
				online.add(player);
		}
		return online;
	}

	/** Online players still in the fight. */
	private List<Player> activePlayers() {
		List<Player> active = onlinePlayers();
		active.removeIf(p -> eliminated.contains(p.getUniqueId()));
		return active;
	}

	/** Players are building/fighting (blocks and projectiles are tracked for cleanup). */
	public boolean isInArena() {
		return state == DuelState.COUNTDOWN || state == DuelState.ACTIVE || state == DuelState.ENDING;
	}

	public boolean isFighting() {
		return state == DuelState.ACTIVE;
	}

	public boolean hasEnded() {
		return state == DuelState.ENDED;
	}
}
