package games.coob.smp.duel;

import games.coob.smp.PlayerCache;
import games.coob.smp.SMPPlugin;
import games.coob.smp.combat.CombatPunishmentManager;
import games.coob.smp.combat.CombatTracker;
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
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Manages duel requests and active duels.
 */
public final class DuelManager {

	@Getter
	private static final DuelManager instance = new DuelManager();

	// Pending duel requests: targetId -> request
	private final Map<UUID, DuelRequest> pendingRequests = new ConcurrentHashMap<>();

	// Active duels: duelId -> ActiveDuel
	private final Map<UUID, ActiveDuel> activeDuels = new ConcurrentHashMap<>();

	// Player to duel mapping for quick lookup
	private final Map<UUID, UUID> playerToDuel = new ConcurrentHashMap<>();

	private static final int PREPARE_TIMEOUT_SECONDS = 90;

	// Admin-built arenas currently in use (lower-case names)
	private final Set<String> busyArenas = new HashSet<>();

	// Duels whose arena is protected from griefing: from the moment the arena is
	// picked until every change to it is undone (a little after the duel ends)
	private final Set<ActiveDuel> protectedArenas = ConcurrentHashMap.newKeySet();
	/** Team mobs of every duel -> their duel. */
	private final Map<UUID, ActiveDuel> duelMobs = new ConcurrentHashMap<>();
	/** Players who send their 1v1 challenges with kits. */
	private final Set<UUID> kitChallengers = ConcurrentHashMap.newKeySet();

	private DuelManager() {
	}

	/**
	 * Sends a duel request from challenger to target.
	 */
	public void sendRequest(Player challenger, Player target) {
		sendRequest(challenger, target, wantsKits(challenger));
	}

	/** Sends a duel request, with kits if {@code kits}. */
	public void sendRequest(Player challenger, Player target, boolean kits) {
		if (!Settings.DuelSection.ENABLE_DUELS) {
			ColorUtil.sendMessage(challenger, "&cDuels are currently disabled.");
			return;
		}

		UUID challengerId = challenger.getUniqueId();
		UUID targetId = target.getUniqueId();

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

		DuelRequest request = new DuelRequest(challengerId, challenger.getName(), targetId, target.getName(),
				Settings.DuelSection.REQUEST_TIMEOUT_SECONDS, kits && kitsAvailable());
		pendingRequests.put(targetId, request);

		ColorUtil.sendMessage(challenger, "&aYou sent a " + (request.isKits() ? "kit " : "") + "duel request to &e"
				+ target.getName() + "&a.");

		Component acceptButton = Component.text("[ACCEPT]", NamedTextColor.GREEN)
				.hoverEvent(HoverEvent.showText(Component.text("Click to accept the duel")))
				.clickEvent(ClickEvent.runCommand("/duel accept " + challenger.getName()));
		Component denyButton = Component.text("[DENY]", NamedTextColor.RED)
				.hoverEvent(HoverEvent.showText(Component.text("Click to deny the duel")))
				.clickEvent(ClickEvent.runCommand("/duel deny " + challenger.getName()));
		target.sendMessage(Component.text()
				.append(Component.text(challenger.getName(), NamedTextColor.YELLOW))
				.append(Component.text(" has challenged you to a " + (request.isKits() ? "kit duel" : "duel") + "! ",
						NamedTextColor.GOLD))
				.append(acceptButton)
				.append(Component.text(" "))
				.append(denyButton)
				.build());

		SchedulerUtil.runLater(20L * Settings.DuelSection.REQUEST_TIMEOUT_SECONDS, () -> {
			if (pendingRequests.remove(targetId, request)) {
				Player c = Bukkit.getPlayer(challengerId);
				Player t = Bukkit.getPlayer(targetId);
				if (c != null)
					ColorUtil.sendMessage(c, "&cYour duel request to " + request.getTargetName() + " has expired.");
				if (t != null)
					ColorUtil.sendMessage(t, "&cThe duel request from " + request.getChallengerName() + " has expired.");
			}
		});
	}

	/**
	 * Accepts a duel request.
	 */
	public void acceptRequest(Player accepter, String challengerName) {
		DuelRequest request = pendingRequests.get(accepter.getUniqueId());
		if (request == null) {
			ColorUtil.sendMessage(accepter, "&cYou don't have any pending duel requests.");
			return;
		}
		if (!request.getChallengerName().equalsIgnoreCase(challengerName)) {
			ColorUtil.sendMessage(accepter, "&cNo duel request from " + challengerName + ".");
			return;
		}

		pendingRequests.remove(accepter.getUniqueId());

		if (request.isExpired()) {
			ColorUtil.sendMessage(accepter, "&cThat duel request has expired.");
			return;
		}

		Player challenger = Bukkit.getPlayer(request.getChallengerId());
		if (challenger == null) {
			ColorUtil.sendMessage(accepter, "&cThe challenger is no longer online.");
			return;
		}
		// Accepted as a kit duel ("your own items are kept safe"): never start it without kits
		if (request.isKits() && !kitsAvailable()) {
			ColorUtil.sendMessage(accepter, "&cKit duels are turned off right now, so this kit duel can't start.");
			ColorUtil.sendMessage(challenger, "&c" + accepter.getName() + " accepted, but kit duels are turned off right now.");
			return;
		}

		startDuel(challenger, accepter, request.isKits());
	}

	/**
	 * Denies a duel request.
	 */
	public void denyRequest(Player denier, String challengerName) {
		DuelRequest request = pendingRequests.get(denier.getUniqueId());
		if (request == null) {
			ColorUtil.sendMessage(denier, "&cYou don't have any pending duel requests.");
			return;
		}
		if (!request.getChallengerName().equalsIgnoreCase(challengerName)) {
			ColorUtil.sendMessage(denier, "&cNo duel request from " + challengerName + ".");
			return;
		}

		pendingRequests.remove(denier.getUniqueId());
		ColorUtil.sendMessage(denier, "&cYou denied the duel request from " + challengerName + ".");

		Player challenger = Bukkit.getPlayer(request.getChallengerId());
		if (challenger != null)
			ColorUtil.sendMessage(challenger, "&c" + denier.getName() + " denied your duel request.");
	}

	/**
	 * Starts a 1v1 duel (from an accepted request or the queue).
	 */
	public void startDuel(Player challenger, Player opponent) {
		startDuel(challenger, opponent, false);
	}

	/** A 1v1, with kits if {@code kits} (and kits are enabled). */
	public void startDuel(Player challenger, Player opponent, boolean kits) {
		startDuel(List.of(challenger), List.of(opponent), Map.of(), kits);
	}

	/**
	 * Starts a duel between two teams (a 1v1 is two teams of one). Everyone is
	 * registered straight away, so they are protected and handled correctly if
	 * they leave while the arena is being prepared.
	 *
	 * @return false if the duel couldn't start (players were told why)
	 */
	public boolean startDuel(List<Player> red, List<Player> blue) {
		return startDuel(red, blue, Map.of());
	}

	/**
	 * Starts a duel between two teams, with mobs fighting for either side.
	 *
	 * @param mobs side -> mob type -> how many
	 * @return false if the duel couldn't start (players were told why)
	 */
	public boolean startDuel(List<Player> red, List<Player> blue, Map<DuelSide, Map<EntityType, Integer>> mobs) {
		return startDuel(red, blue, mobs, false);
	}

	/**
	 * Starts a duel between two teams, with mobs fighting for either side, and
	 * kits if {@code kits} (and kits are enabled and there are any).
	 *
	 * @return false if the duel couldn't start (players were told why)
	 */
	public boolean startDuel(List<Player> red, List<Player> blue, Map<DuelSide, Map<EntityType, Integer>> mobs,
			boolean kits) {
		List<Player> everyone = new ArrayList<>(red);
		everyone.addAll(blue);

		for (Player player : everyone) {
			String problem = !player.isOnline() ? player.getName() + " is no longer online."
					: isInDuel(player) ? player.getName() + " is already in a duel."
							: player.isDead() ? player.getName() + " needs to respawn first."
									: CombatTracker.isInCombat(player) ? player.getName() + " is in combat right now."
											: kits && PlayerCache.from(player).hasKitStash()
													? player.getName() + " still has items waiting from an earlier kit duel (an admin can help)."
											: CombatPunishmentManager.isPvpLocked(player) ? player.getName() + " is locked out of PvP."
													: player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
															? player.getName() + " needs to be in survival mode."
															: null;
			if (problem != null) {
				for (Player other : everyone)
					ColorUtil.sendMessage(other, "&cThe duel couldn't start: " + problem);
				return false;
			}
		}

		for (Player player : everyone) {
			DuelQueueManager.getInstance().remove(player);
			TeamDuelManager.getInstance().removeSilently(player);
			removeRequestsInvolving(player.getUniqueId());
		}

		ActiveDuel duel = new ActiveDuel(red, blue, mobs, kits && kitsAvailable());
		activeDuels.put(duel.getDuelId(), duel);
		for (Player player : everyone) {
			playerToDuel.put(player.getUniqueId(), duel.getDuelId());
			ColorUtil.sendMessage(player, "&aDuel starting! Preparing the arena...");
		}
		// Kit duels: everyone picks while the arena is prepared
		duel.startKitSelection();

		CompletableFuture<DuelArena> search;
		try {
			search = findArena(duel);
		} catch (RuntimeException e) {
			SMPPlugin.getInstance().getLogger().log(Level.WARNING, "Could not pick a duel arena", e);
			search = CompletableFuture.completedFuture(null);
		}

		search.whenComplete((arena, error) -> SchedulerUtil.runTask(() -> {
			if (duel.getState() != ActiveDuel.DuelState.PREPARING) {
				releaseArena(arena);
				return;
			}
			if (arena == null) {
				duel.cancel("&cCouldn't find a safe spot for the duel. Please try again.");
				return;
			}
			// Load the landing area in the background before anyone is moved
			preloadLandingArea(arena, duel.getLargestTeamSize(), duel.hasMobs()).whenComplete((ignored, loadError) ->
					SchedulerUtil.runTask(() -> {
						// Cancelled while loading (e.g. someone left): free the arena again
						if (duel.getState() != ActiveDuel.DuelState.PREPARING) {
							releaseArena(arena);
							return;
						}
						duel.begin(arena);
					}));
		}));

		// Safety net: never leave players stuck "in a duel" if preparing hangs
		SchedulerUtil.runLater(20L * PREPARE_TIMEOUT_SECONDS, () -> {
			if (duel.getState() == ActiveDuel.DuelState.PREPARING)
				duel.cancel("&cThe duel took too long to prepare and was cancelled.");
		});
		return true;
	}

	/**
	 * Picks where the duel happens. Admin-built arenas are reserved while a duel
	 * uses them, so two duels never share one; when none is free, a natural spot
	 * is used instead.
	 */
	private CompletableFuture<Void> preloadLandingArea(DuelArena arena, int teamSize, boolean mobs) {
		// Mobs line up a few blocks behind their team
		int spread = mobs ? 12 : teamSize > 1 ? 9 : 0;
		List<CompletableFuture<?>> loads = new ArrayList<>();
		for (Location spawn : new Location[] { arena.spawn1(), arena.spawn2() }) {
			Set<Long> seen = new HashSet<>();
			for (int dx : new int[] { -spread, 0, spread }) {
				for (int dz : new int[] { -spread, 0, spread }) {
					int chunkX = (spawn.getBlockX() + dx) >> 4;
					int chunkZ = (spawn.getBlockZ() + dz) >> 4;
					if (seen.add((long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL)))
						loads.add(spawn.getWorld().getChunkAtAsync(chunkX, chunkZ, true));
				}
			}
		}
		return CompletableFuture.allOf(loads.toArray(new CompletableFuture[0]));
	}

	private CompletableFuture<DuelArena> findArena(ActiveDuel duel) {
		int teamSize = duel.getLargestTeamSize();
		boolean useCreated = switch (Settings.DuelSection.ARENA_MODE) {
			case NATURAL -> false;
			case CREATED -> true;
			case RANDOM -> ThreadLocalRandom.current().nextBoolean();
		};

		if (useCreated) {
			ArenaData arena = ArenaRegistry.getInstance().getRandomArena(busyArenas);
			if (arena != null && arena.getSpawn1().getWorld() != null) {
				busyArenas.add(arena.getName().toLowerCase());
				return CompletableFuture.completedFuture(
						new DuelArena(arena.getCenter(), arena.getSpawn1(), arena.getSpawn2(), arena.getName()));
			}
		}
		return NaturalArenaFinder.find(teamSize, () -> duel.getState() != ActiveDuel.DuelState.PREPARING);
	}

	private void releaseArena(DuelArena arena) {
		if (arena != null && arena.created())
			busyArenas.remove(arena.arenaName().toLowerCase());
	}

	/** A player was sent home before the duel ended; they are no longer part of it. */
	void detach(Player player, UUID duelId) {
		playerToDuel.remove(player.getUniqueId(), duelId);
	}

	void onDuelEnded(ActiveDuel duel) {
		activeDuels.remove(duel.getDuelId());
		for (Player player : duel.getPlayers())
			playerToDuel.remove(player.getUniqueId(), duel.getDuelId());
		releaseArena(duel.getArena());
	}

	public ActiveDuel getActiveDuel(Player player) {
		return getActiveDuel(player.getUniqueId());
	}

	public ActiveDuel getActiveDuel(UUID playerId) {
		UUID duelId = playerToDuel.get(playerId);
		return duelId == null ? null : activeDuels.get(duelId);
	}

	public boolean isInDuel(Player player) {
		return playerToDuel.containsKey(player.getUniqueId());
	}

	public boolean isInDuel(UUID playerId) {
		return playerToDuel.containsKey(playerId);
	}

	private boolean hasPendingRequest(Player player) {
		for (DuelRequest request : pendingRequests.values()) {
			if (request.getChallengerId().equals(player.getUniqueId()))
				return true;
		}
		return false;
	}

	private void removeRequestsInvolving(UUID playerId) {
		pendingRequests.entrySet().removeIf(entry -> entry.getKey().equals(playerId)
				|| entry.getValue().getChallengerId().equals(playerId));
	}

	/**
	 * Handles player logout: forfeits or cancels their duel and drops their requests.
	 */
	public void handlePlayerQuit(Player player) {
		ActiveDuel duel = getActiveDuel(player);
		if (duel != null) {
			playerToDuel.remove(player.getUniqueId());
			duel.handleQuit(player);
		}
		removeRequestsInvolving(player.getUniqueId());
	}

	/**
	 * Called when a duelist would die (lethal damage was cancelled) or actually died.
	 *
	 * @param killer the player or duel mob that dealt the final hit, if any
	 */
	public void handlePlayerDeath(Player player, Entity killer) {
		ActiveDuel duel = getActiveDuel(player);
		if (duel == null)
			return;

		if (duel.isFighting()) {
			duel.eliminate(player, killer);
		} else if (duel.getState() == ActiveDuel.DuelState.PREPARING
				|| duel.getState() == ActiveDuel.DuelState.COUNTDOWN) {
			duel.cancel("&c" + player.getName() + " died before the fight started, the duel was cancelled.");
		}
	}

	/**
	 * Admin: ends a player's duel without a winner and sends everyone back.
	 */
	public boolean forceEnd(Player player) {
		ActiveDuel duel = getActiveDuel(player);
		if (duel == null)
			return false;
		duel.cancel("&cAn admin ended the duel.");
		return true;
	}

	/**
	 * Teleports the player back immediately during the end countdown (/duel return).
	 */
	public boolean returnNow(Player player) {
		ActiveDuel duel = getActiveDuel(player);
		return duel != null && duel.returnNow(player);
	}

	void protect(ActiveDuel duel) {
		protectedArenas.add(duel);
	}

	void unprotect(ActiveDuel duel) {
		protectedArenas.remove(duel);
	}

	/** Duels whose arena is still protected (including ones waiting for cleanup). */
	public Collection<ActiveDuel> getProtectedDuels() {
		return Collections.unmodifiableCollection(protectedArenas);
	}

	/** Kit duels can be played (turned on in settings.yml and duel-kits.yml has kits). */
	public static boolean kitsAvailable() {
		return Settings.DuelSection.KITS_ENABLED && !games.coob.smp.duel.kit.DuelKits.getInstance().isEmpty();
	}

	/** Whether this player's 1v1 challenges come with kits (their choice in /duel). */
	public boolean wantsKits(Player player) {
		return kitsAvailable() && kitChallengers.contains(player.getUniqueId());
	}

	public void setWantsKits(Player player, boolean kits) {
		if (kits) {
			kitChallengers.add(player.getUniqueId());
		} else {
			kitChallengers.remove(player.getUniqueId());
		}
	}

	/** Whether any arena is protected right now (most events can stop here). */
	public boolean hasProtectedArenas() {
		return !protectedArenas.isEmpty();
	}

	/** The duel whose arena contains this location, or null. */
	public ActiveDuel getDuelAt(Location location) {
		return getDuelAt(location.getWorld(), location.getX(), location.getZ());
	}

	/** The duel whose arena contains this point, or null. No objects are made, for the busy block events. */
	public ActiveDuel getDuelAt(World world, double x, double z) {
		if (protectedArenas.isEmpty())
			return null;
		for (ActiveDuel duel : protectedArenas) {
			if (duel.arenaContains(world, x, z))
				return duel;
		}
		return null;
	}

	/** The duel this entity fights in as a team mob, or null. */
	public ActiveDuel getDuelOfMob(Entity entity) {
		return duelMobs.isEmpty() ? null : duelMobs.get(entity.getUniqueId());
	}

	void registerMob(UUID mobId, ActiveDuel duel) {
		duelMobs.put(mobId, duel);
	}

	void unregisterMob(UUID mobId) {
		duelMobs.remove(mobId);
	}

	public Collection<ActiveDuel> getActiveDuels() {
		return Collections.unmodifiableCollection(activeDuels.values());
	}

	/**
	 * Ends all duels and sends everyone back (plugin disable).
	 */
	public void cleanup() {
		for (ActiveDuel duel : new ArrayList<>(activeDuels.values()))
			duel.end();
		// Duels that ended but are still putting their arena back
		for (ActiveDuel duel : new ArrayList<>(protectedArenas))
			duel.finishRestore();
		activeDuels.clear();
		playerToDuel.clear();
		pendingRequests.clear();
		busyArenas.clear();
		protectedArenas.clear();
		duelMobs.clear();
	}
}
