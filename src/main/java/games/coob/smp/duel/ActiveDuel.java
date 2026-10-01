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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import games.coob.smp.duel.kit.DuelKit;
import games.coob.smp.duel.kit.DuelKits;
import games.coob.smp.duel.kit.KitStash;
import games.coob.smp.menu.DuelKitMenu;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * A duel between two sides (one player each for a 1v1, or teams), from preparing
 * the arena to sending everyone home. Each side may also have mobs fighting for
 * it ({@link DuelMobs}); they never decide the duel, only players do.
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
	/**
	 * Block location -> what was there before the duel changed it, and who placed
	 * there first (null when the change wasn't a duelist placing a block: lava
	 * flowing, terrain broken or blown up...).
	 */
	private final Map<Location, PlacedBlock> placedBlocks = new HashMap<>();

	private record PlacedBlock(BlockState original, UUID placer) {
	}

	/** Half the arena's width, set when the arena is known. */
	private int arenaRadius;
	private final Set<UUID> droppedItems = new HashSet<>();
	private final Set<UUID> spawnedEntities = new HashSet<>();

	private final List<BukkitTask> tasks = new ArrayList<>();

	// Restoring the arena after the duel
	/** Blocks put back per tick. */
	private static final int RESTORE_PER_TICK = 800;
	private final ArrayDeque<Map.Entry<Location, PlacedBlock>> restoreQueue = new ArrayDeque<>();
	/** Blocks in chunks being loaded, by chunk. */
	private final Map<Long, List<Map.Entry<Location, PlacedBlock>>> waitingForChunk = new HashMap<>();
	private BukkitTask restoreTask;
	private final DuelMobs mobs;

	// Kits
	/** Everyone fights with a kit (duel-kits.yml) instead of their own gear. */
	@Getter
	private final boolean kits;
	private final Map<UUID, DuelKit> kitChoices = new HashMap<>();
	/** The time to pick is up, or everyone picked: kits can't change any more. */
	private boolean kitsLocked;

	ActiveDuel(List<Player> red, List<Player> blue, Map<DuelSide, Map<EntityType, Integer>> mobs, boolean kits) {
		add(red, DuelSide.RED);
		add(blue, DuelSide.BLUE);
		this.mobs = new DuelMobs(this, mobs);
		this.kits = kits;
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
		// Protected from griefing until every change is undone (see DuelArenaListener)
		DuelManager.getInstance().protect(this);

		for (DuelSide side : DuelSide.values()) {
			List<Player> team = getTeam(side);
			List<Location> spots = arena.spots(side, team.size());
			for (int i = 0; i < team.size(); i++) {
				Player player = team.get(i);
				if (left.contains(player.getUniqueId()))
					continue;
				tasks.add(DuelFlyDownTeleporter.descend(player, spots.get(i), () -> {
					landed.add(player.getUniqueId());
					// Still choosing and no kit yet (the menu may have been closed): show it again
					if (isChoosingKits() && !kitChoices.containsKey(player.getUniqueId()) && player.isOnline()
							&& !(player.getOpenInventory().getTopInventory().getHolder(false) instanceof DuelKitMenu))
						new DuelKitMenu(player, this).displayTo(player);
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
		// Still picking kits: the countdown starts once everyone picked or the time is up
		if (kits && !kitsLocked)
			return;
		startCountdown();
	}

	// -------------------------------------------------------------------------
	// Kits
	// -------------------------------------------------------------------------

	/** Opens the kit menu for everyone and starts the time to pick (right when the duel is created). */
	void startKitSelection() {
		if (!kits)
			return;
		int seconds = Settings.DuelSection.KIT_CHOOSE_SECONDS;
		Component reopen = Component.text("[Pick kit]", NamedTextColor.GREEN)
				.hoverEvent(HoverEvent.showText(Component.text("Open the kit menu again")))
				.clickEvent(ClickEvent.runCommand("/duel kit"));
		for (Player player : onlinePlayers()) {
			new DuelKitMenu(player, this).displayTo(player);
			ColorUtil.sendMessage(player, "&eThis is a kit duel: pick your kit within &6" + seconds
					+ " &eseconds, or you get a random one. Your own items are kept safe.");
			player.sendMessage(reopen);
		}
		tasks.add(SchedulerUtil.runLater(20L * seconds, this::lockKits));
	}

	/** Whether players can still pick (or change) their kit. */
	public boolean isChoosingKits() {
		return kits && !kitsLocked && state == DuelState.PREPARING;
	}

	public DuelKit getKitChoice(Player player) {
		return kitChoices.get(player.getUniqueId());
	}

	/** @return whether the pick counted (the time to pick may be over) */
	public boolean chooseKit(Player player, DuelKit kit) {
		if (!isChoosingKits() || !players.containsKey(player.getUniqueId()) || kit == null)
			return false;
		kitChoices.put(player.getUniqueId(), kit);
		ColorUtil.sendMessage(player, "&aYour kit: " + kit.getDisplayName() + "&a.");
		// Called from a menu click: closing menus and handing out kits must wait for the next tick
		if (everyoneChoseKit())
			SchedulerUtil.runTask(this::lockKits);
		return true;
	}

	private boolean everyoneChoseKit() {
		for (UUID id : players.keySet()) {
			if (!left.contains(id) && !kitChoices.containsKey(id))
				return false;
		}
		return true;
	}

	/** Everyone picked, or the time is up: whoever hasn't picked gets a random kit. */
	private void lockKits() {
		if (!kits || kitsLocked || state != DuelState.PREPARING)
			return;
		kitsLocked = true;
		for (Player player : onlinePlayers()) {
			if (kitChoices.containsKey(player.getUniqueId()))
				continue;
			DuelKit kit = DuelKits.getInstance().random();
			if (kit == null)
				continue;
			kitChoices.put(player.getUniqueId(), kit);
			ColorUtil.sendMessage(player, "&eYou didn't pick a kit in time, so you get " + kit.getDisplayName() + "&e.");
		}
		for (Player player : onlinePlayers()) {
			if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof DuelKitMenu menu
					&& menu.getDuel() == this)
				player.closeInventory();
		}
		checkEveryoneLanded();
	}

	/**
	 * Stores everyone's own gear and hands out the kits, with full health and
	 * hunger so everyone starts even.
	 */
	private boolean giveKits() {
		for (Player player : activePlayers()) {
			DuelKit kit = kitChoices.get(player.getUniqueId());
			if (kit == null)
				kit = DuelKits.getInstance().random();
			if (kit == null)
				continue;
			kitChoices.put(player.getUniqueId(), kit);
			// An older copy of their gear is still waiting to be given back: a kit would wipe what they have now
			if (!KitStash.store(player)) {
				cancel("&cThe duel couldn't start: " + player.getName() + " still has items waiting to be given back"
						+ " from an earlier kit duel (an admin can help).");
				return false;
			}
			kit.apply(player);
			player.setHealth(maxHealth(player));
			player.setFoodLevel(20);
			player.setSaturation(5);
			player.setFireTicks(0);
			ColorUtil.sendMessage(player, "&7You fight as " + kit.getDisplayName() + "&7.");
		}
		return true;
	}

	private void startCountdown() {
		state = DuelState.COUNTDOWN;
		if (kits && !giveKits())
			return;

		if (Settings.DuelSection.BORDER_ENABLED) {
			border = new DuelBorder(arena.center(), arenaRadius);
			border.start(() -> state == DuelState.ACTIVE, activePlayers());
		}

		// Frozen until the fight starts
		mobs.spawn(arena);

		if (isTeamDuel()) {
			for (Player player : activePlayers()) {
				DuelSide side = getSide(player);
				ColorUtil.sendMessage(player, "&7You are on the " + side.coloredName() + " &7team with: &f"
						+ names(getTeam(side), player));
			}
		}
		if (!mobs.isEmpty()) {
			for (Player player : activePlayers()) {
				DuelSide side = getSide(player);
				String ours = DuelMobs.describe(mobs.getRequested(side));
				String theirs = DuelMobs.describe(mobs.getRequested(side.other()));
				ColorUtil.sendMessage(player, "&7Mobs on your side: " + side.getColorCode() + (ours != null ? ours : "none")
						+ "&7. Against you: " + side.other().getColorCode() + (theirs != null ? theirs : "none") + "&7.");
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
		if (kits) {
			for (Player player : activePlayers()) {
				DuelKit kit = kitChoices.get(player.getUniqueId());
				if (kit != null)
					kit.giveEffects(player);
			}
		}

		if (!mobs.isEmpty()) {
			mobs.activate();
			tasks.add(SchedulerUtil.runTimer(DuelMobs.TICK_PERIOD, DuelMobs.TICK_PERIOD, () -> {
				if (state == DuelState.ACTIVE)
					mobs.tick();
			}));
		}

		int minutes = Settings.DuelSection.MAX_FIGHT_MINUTES;
		if (minutes > 0)
			tasks.add(SchedulerUtil.runLater(20L * 60 * minutes, () -> {
				if (state == DuelState.ACTIVE)
					cancel("&eTime's up! The duel ended in a draw after " + minutes + " minutes.");
			}));
	}

	/**
	 * A player is out of the fight: their hit would have killed them, they died,
	 * or they left. When a whole side's players are out, the other side wins
	 * (mobs don't count).
	 *
	 * @param killer the player or duel mob that dealt the final hit, if any
	 */
	void eliminate(Player victim, Entity killer) {
		UUID id = victim.getUniqueId();
		if (state != DuelState.ACTIVE || eliminated.contains(id))
			return;
		eliminated.add(id);

		// A player who actually died already dropped their items through the death event.
		// Kit duels never drop anything: the items are only the kit.
		if (!kits && Settings.DuelSection.LOOT_MODE == Settings.DuelSection.LootMode.DROP_ITEMS && !victim.isDead())
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
			String by = describeKiller(killer);
			String how = left.contains(id) ? " &7left the duel"
					: by != null ? " &7was eliminated by " + by
							: " &7was eliminated";
			broadcast(side.getColorCode() + victim.getName() + how + " &8(" + scoreLine() + "&8)");
		}

		if (sideOut)
			finish(side.other());
	}

	/** e.g. "&cSteve" or "&cRed Zombie", or null if the killer isn't part of this duel. */
	private String describeKiller(Entity killer) {
		if (killer instanceof Player player && getSide(player) != null)
			return getSide(player).getColorCode() + player.getName();
		DuelSide mobSide = killer != null ? mobs.getSide(killer) : null;
		if (mobSide != null)
			return mobSide.getColorCode() + mobSide.getDisplayName() + " " + DuelMobs.displayName(killer.getType());
		return null;
	}

	/**
	 * One side won: stats, titles and the countdown before everyone is sent back.
	 */
	private void finish(DuelSide winners) {
		state = DuelState.ENDING;
		winningSide = winners;
		mobs.removeAll();

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

		mobs.removeAll();
		if (border != null)
			border.stop();

		boolean shuttingDown = !SMPPlugin.getInstance().isEnabled();
		for (Player player : onlinePlayers())
			returnPlayer(player, shuttingDown);

		DuelManager.getInstance().onDuelEnded(this);

		if (shuttingDown) {
			performCleanup(true);
		} else {
			SchedulerUtil.runLater(20, () -> performCleanup(false));
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
					// They may have been the last one still picking a kit
					if (isChoosingKits() && everyoneChoseKit())
						lockKits();
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

		// Their own gear instead of the kit
		KitStash.restore(player);

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

	private void performCleanup(boolean immediately) {
		try {
			// A kit duel always cleans up: nothing from a kit may stay in the world
			if (kits || Settings.DuelSection.CLEANUP_REMOVE_DROPPED_ITEMS)
				returnDroppedItems();
			if (kits || Settings.DuelSection.CLEANUP_REMOVE_ENTITIES)
				removeProjectiles();
		} catch (RuntimeException e) {
			SMPPlugin.getInstance().getLogger().log(Level.WARNING, "Problem cleaning up a duel's items", e);
		}
		droppedItems.clear();
		spawnedEntities.clear();

		// Always runs, so the arena can't stay protected forever
		restoreBlocks(immediately);
	}

	/**
	 * Undoes every change to the arena: terrain broken or blown up, lava and water
	 * that flowed, fire, and (if enabled) the blocks duelists placed. Chunks that
	 * unloaded after everyone left are loaded in the background first. The arena
	 * stays protected until all of it is back.
	 */
	private void restoreBlocks(boolean immediately) {
		for (Map.Entry<Location, PlacedBlock> entry : placedBlocks.entrySet())
			restoreQueue.add(Map.entry(entry.getKey(), entry.getValue()));
		placedBlocks.clear();

		if (immediately) {
			finishRestore();
			return;
		}
		// A big fight (crystals, TNT) can leave thousands of blocks: spread them over a few ticks
		restoreTask = SchedulerUtil.runTimer(1, 1, this::restoreSome);
	}

	private void restoreSome() {
		for (int i = 0; i < RESTORE_PER_TICK && !restoreQueue.isEmpty(); i++) {
			Map.Entry<Location, PlacedBlock> entry = restoreQueue.poll();
			if (entry.getKey().getWorld() == null)
				continue;
			if (entry.getKey().isChunkLoaded()) {
				restoreEntry(entry);
			} else {
				waitForChunk(entry);
			}
		}
		checkRestored();
	}

	/** The chunk unloaded after everyone left: load it in the background and restore it then. */
	private void waitForChunk(Map.Entry<Location, PlacedBlock> entry) {
		Location location = entry.getKey();
		long key = (long) (location.getBlockX() >> 4) << 32 | ((location.getBlockZ() >> 4) & 0xFFFFFFFFL);
		List<Map.Entry<Location, PlacedBlock>> waiting = waitingForChunk.get(key);
		if (waiting != null) {
			waiting.add(entry);
			return;
		}
		waiting = new ArrayList<>();
		waiting.add(entry);
		waitingForChunk.put(key, waiting);
		// Completes on the main thread, with the chunk loaded
		location.getWorld().getChunkAtAsync(location.getBlockX() >> 4, location.getBlockZ() >> 4)
				.whenComplete((chunk, error) -> {
					List<Map.Entry<Location, PlacedBlock>> entries = waitingForChunk.remove(key);
					if (entries != null) {
						for (Map.Entry<Location, PlacedBlock> waited : entries)
							restoreEntry(waited);
					}
					checkRestored();
				});
	}

	private void restoreEntry(Map.Entry<Location, PlacedBlock> entry) {
		try {
			Block block = entry.getKey().getBlock();
			// Placed blocks may stay if the server wants that, but never lava, water or fire,
			// and nothing from a kit duel (kit blocks could be mined and kept)
			if (entry.getValue().placer() != null && !kits && !Settings.DuelSection.CLEANUP_REMOVE_PLACED_BLOCKS
					&& !isHazard(block.getType()))
				return;
			restoreBlock(block, entry.getValue());
		} catch (RuntimeException e) {
			SMPPlugin.getInstance().getLogger().log(Level.WARNING, "Could not restore a duel block at " + entry.getKey(), e);
		}
	}

	/** Everything is back: the arena is no longer protected. */
	private void checkRestored() {
		if (!restoreQueue.isEmpty() || !waitingForChunk.isEmpty())
			return;
		if (restoreTask != null) {
			restoreTask.cancel();
			restoreTask = null;
		}
		DuelManager.getInstance().unprotect(this);
	}

	/** Puts everything back right now, loading chunks if needed (plugin disable). */
	void finishRestore() {
		while (!restoreQueue.isEmpty())
			restoreEntry(restoreQueue.poll());
		for (List<Map.Entry<Location, PlacedBlock>> entries : waitingForChunk.values()) {
			for (Map.Entry<Location, PlacedBlock> entry : entries)
				restoreEntry(entry);
		}
		waitingForChunk.clear();
		checkRestored();
	}

	private static boolean isHazard(Material material) {
		return material == Material.LAVA || material == Material.WATER || material == Material.FIRE
				|| material == Material.SOUL_FIRE;
	}

	/**
	 * Puts back what was there before the duel. A container placed during the
	 * duel (chest, shulker box...) goes back to whoever placed it, with its contents.
	 */
	private void restoreBlock(Block block, PlacedBlock placed) {
		// The live state (no copy): only containers need a look inside
		if (block.getType() != placed.original().getType() && block.getState(false) instanceof Container container) {
			List<ItemStack> items = new ArrayList<>();
			for (ItemStack item : container.getInventory().getContents()) {
				if (item != null && !item.isEmpty())
					items.add(item.clone());
			}
			container.getInventory().clear();
			items.add(new ItemStack(block.getType()));
			// In a kit duel everything came from the kits: nothing is handed out
			if (kits)
				items.clear();

			Player placer = placed.placer() != null ? Bukkit.getPlayer(placed.placer()) : null;
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
		Iterator<UUID> iterator = spawnedEntities.iterator();
		while (iterator.hasNext()) {
			Entity entity = Bukkit.getEntity(iterator.next());
			// Gone already (arrows despawn, mob arrows are removed): forget it
			if (entity == null || !entity.isValid()) {
				iterator.remove();
				continue;
			}
			if (entity instanceof AbstractArrow arrow && arrow.getPickupStatus() == AbstractArrow.PickupStatus.ALLOWED
					&& arrow.getShooter() instanceof Player shooter && shooter.equals(player)) {
				give(player, arrow.getItemStack());
				arrow.remove();
				iterator.remove();
			}
		}
	}

	/** Items thrown on the ground during the fight go back to whoever threw them. */
	private void returnDroppedItems() {
		for (UUID id : droppedItems) {
			// Kit items stay in the duel
			if (kits) {
				if (Bukkit.getEntity(id) instanceof Item item)
					item.remove();
				continue;
			}
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
			// Kit arrows stay in the duel
			if (!kits && entity instanceof AbstractArrow arrow && arrow.getPickupStatus() == AbstractArrow.PickupStatus.ALLOWED
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

	/**
	 * Remembers what a block was before something other than a duelist placing
	 * it changed it (lava flowing, fire, a block broken or blown up), so cleanup
	 * can put it back. Only the first change counts, and only inside the arena.
	 */
	public void trackChangedBlock(BlockState original) {
		Location location = original.getLocation().toBlockLocation();
		if (arenaContains(location))
			placedBlocks.putIfAbsent(location, new PlacedBlock(original, null));
	}

	/** {@link #trackChangedBlock(BlockState)}, taking the block's snapshot only the first time it changes. */
	public void trackChangedBlock(Block block) {
		if (!arenaContains(block.getWorld(), block.getX() + 0.5, block.getZ() + 0.5))
			return;
		Location location = block.getLocation();
		if (!placedBlocks.containsKey(location))
			placedBlocks.put(location, new PlacedBlock(block.getState(), null));
	}

	/**
	 * Whether the block here was made during the duel (a duelist placed it, or
	 * lava or water flowed there), rather than being part of the arena.
	 */
	public boolean isDuelMade(Location location) {
		PlacedBlock placed = placedBlocks.get(location.toBlockLocation());
		return placed != null && location.getBlock().getType() != placed.original().getType();
	}

	/** Whether a location is inside this duel's arena (works with the border turned off too). */
	public boolean arenaContains(Location location) {
		return arenaContains(location.getWorld(), location.getX(), location.getZ());
	}

	public boolean arenaContains(org.bukkit.World world, double x, double z) {
		if (arena == null || world == null || !world.equals(arena.center().getWorld()))
			return false;
		return Math.abs(x - arena.center().getX()) <= arenaRadius && Math.abs(z - arena.center().getZ()) <= arenaRadius;
	}

	/** Whether the arena overlaps the square from (minX, minZ) to (maxX, maxZ). */
	public boolean arenaOverlaps(org.bukkit.World world, double minX, double minZ, double maxX, double maxZ) {
		if (arena == null || world == null || !world.equals(arena.center().getWorld()))
			return false;
		double cx = arena.center().getX();
		double cz = arena.center().getZ();
		return maxX >= cx - arenaRadius && minX <= cx + arenaRadius && maxZ >= cz - arenaRadius && minZ <= cz + arenaRadius;
	}

	public void trackDroppedItem(UUID itemId) {
		droppedItems.add(itemId);
	}

	/** Whether this item on the ground was dropped in this duel. */
	public boolean isDroppedHere(UUID itemId) {
		return droppedItems.contains(itemId);
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

	/** Whether this player is one of the duelists and still in the fight. */
	boolean isFightingPlayer(Player player) {
		UUID id = player.getUniqueId();
		return players.containsKey(id) && player.isOnline() && !eliminated.contains(id) && !left.contains(id)
				&& player.getGameMode() != GameMode.SPECTATOR;
	}

	boolean hasMobs() {
		return !mobs.isEmpty();
	}

	/** The side a mob fights for in this duel, or null if it isn't one of this duel's mobs. */
	public DuelSide getMobSide(Entity entity) {
		return mobs.getSide(entity);
	}

	/** Whether a mob of {@code side} may attack this entity (an enemy player still fighting, or an enemy mob). */
	public boolean isMobEnemy(DuelSide side, Entity entity) {
		return mobs.isEnemy(side, entity);
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

	/**
	 * The player is at the arena: landed (maybe still waiting for the others or
	 * for kits), or the countdown or fight is on.
	 */
	public boolean hasArrived(Player player) {
		return isInArena() || (state == DuelState.PREPARING && landed.contains(player.getUniqueId()));
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
