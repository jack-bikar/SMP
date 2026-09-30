package games.coob.smp.duel;

import games.coob.smp.PlayerCache;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import io.papermc.paper.datacomponent.DataComponentTypes;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;


/**
 * Handles duel-related events.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DuelListener implements Listener {

	private static final DuelListener instance = new DuelListener();

	public static DuelListener getInstance() {
		return instance;
	}

	// -------------------------------------------------------------------------
	// Joining and leaving
	// -------------------------------------------------------------------------

	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerQuit(final PlayerQuitEvent event) {
		Player player = event.getPlayer();
		DuelManager.getInstance().handlePlayerQuit(player);
		DuelQueueManager.getInstance().remove(player);
		TeamDuelManager.getInstance().handleQuit(player);
		ArenaManager.getInstance().handlePlayerQuit(player);
	}

	/**
	 * Players who left (or were online during a crash) mid-duel are sent back to
	 * where they were before it.
	 */
	@EventHandler
	public void onPlayerJoin(final PlayerJoinEvent event) {
		Player player = event.getPlayer();
		PlayerCache cache = PlayerCache.from(player);
		if (!cache.hasDuelReturn() || DuelManager.getInstance().isInDuel(player))
			return;

		Location location = cache.getDuelReturnLocation();
		GameMode gameMode = cache.getDuelReturnGameMode();

		SchedulerUtil.runLater(1, () -> {
			if (!player.isOnline())
				return;
			player.teleportAsync(location).thenAccept(success -> {
				// Keep the return spot for the next join if the teleport didn't happen
				if (!success || !player.isOnline())
					return;
				cache.setDuelReturn(null, null);
				if (gameMode != null)
					player.setGameMode(gameMode);
				ColorUtil.sendMessage(player, "&eYou were sent back to where you were before your duel.");
			});
		});
	}

	// -------------------------------------------------------------------------
	// Damage and death
	// -------------------------------------------------------------------------

	/**
	 * Duelists only take damage while the fight is on. A hit that would kill
	 * them ends the duel instead, so nobody actually dies.
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityDamage(final EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player victim))
			return;

		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(victim);
		if (duel == null)
			return;

		// While the arena is still being found, damage is normal: accepting a duel is no escape from lava or mobs
		if (duel.getState() == ActiveDuel.DuelState.PREPARING && duel.getArena() == null)
			return;

		if (!duel.isFighting() || duel.isEliminated(victim)) {
			event.setCancelled(true);
			return;
		}

		if (victim.getHealth() - event.getFinalDamage() > 0 || hasDeathProtection(victim.getInventory()))
			return;

		event.setCancelled(true);
		Entity killer = event instanceof EntityDamageByEntityEvent byEntity ? getKiller(byEntity.getDamager()) : null;
		DuelManager.getInstance().handlePlayerDeath(victim, killer);
	}

	private static boolean hasDeathProtection(PlayerInventory inventory) {
		return hasDeathProtection(inventory.getItemInMainHand()) || hasDeathProtection(inventory.getItemInOffHand());
	}

	private static boolean hasDeathProtection(ItemStack item) {
		return !item.isEmpty() && item.hasData(DataComponentTypes.DEATH_PROTECTION);
	}

	/**
	 * Duelists can't hit or be hit by players outside their duel, and can't hurt
	 * their own teammates.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityDamageByEntity(final EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player victim))
			return;

		Player attacker = getAttacker(event.getDamager());
		if (attacker == null || attacker.equals(victim))
			return;

		ActiveDuel victimDuel = DuelManager.getInstance().getActiveDuel(victim);
		ActiveDuel attackerDuel = DuelManager.getInstance().getActiveDuel(attacker);
		if (victimDuel != attackerDuel || (victimDuel != null && victimDuel.areTeammates(victim, attacker)))
			event.setCancelled(true);
	}

	/**
	 * Fallback for deaths that skip the damage check (void, /kill).
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerDeath(final PlayerDeathEvent event) {
		Player player = event.getEntity();
		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(player);
		if (duel == null)
			return;

		if (!duel.isFighting() || Settings.DuelSection.LOOT_MODE == Settings.DuelSection.LootMode.KEEP_INVENTORY) {
			event.setKeepInventory(true);
			event.setKeepLevel(true);
			event.getDrops().clear();
			event.setDroppedExp(0);
		}
		DuelManager.getInstance().handlePlayerDeath(player, player.getKiller());
	}

	/**
	 * Duelists who died respawn where they were before the duel.
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerRespawn(final PlayerRespawnEvent event) {
		Player player = event.getPlayer();
		PlayerCache cache = PlayerCache.from(player);
		if (!cache.hasDuelReturn())
			return;

		// Only players who are out of the fight (or whose duel is over) go home
		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(player);
		if (duel != null && !duel.hasEnded() && duel.getState() != ActiveDuel.DuelState.ENDING
				&& !duel.isEliminated(player))
			return;

		event.setRespawnLocation(cache.getDuelReturnLocation());
		GameMode gameMode = cache.getDuelReturnGameMode();
		cache.setDuelReturn(null, null);
		if (duel != null)
			duel.markReturned(player);

		if (gameMode != null) {
			SchedulerUtil.runLater(1, () -> {
				if (player.isOnline())
					player.setGameMode(gameMode);
			});
		}
	}

	/**
	 * No leaving the arena with commands, and no pearling through the border.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPlayerTeleport(final PlayerTeleportEvent event) {
		Player player = event.getPlayer();
		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(player);
		if (duel == null || !duel.isInArena())
			return;

		switch (event.getCause()) {
			case COMMAND -> {
				event.setCancelled(true);
				ColorUtil.sendMessage(player, "&cYou cannot teleport during a duel. Use &e/duel return &cwhen it ends.");
			}
			// No hiding from the fight in another dimension
			case NETHER_PORTAL, END_PORTAL, END_GATEWAY -> {
				event.setCancelled(true);
				ColorUtil.sendMessage(player, "&cYou cannot use portals during a duel.");
			}
			// Eliminated players may spectate teammates, but not leave the arena
			case SPECTATE -> {
				if (duel.getBorder() == null || !duel.getBorder().contains(event.getTo()))
					event.setCancelled(true);
			}
			case ENDER_PEARL, CONSUMABLE_EFFECT -> {
				if (duel.getBorder() != null && !duel.getBorder().contains(event.getTo()))
					event.setCancelled(true);
			}
			default -> {
			}
		}
	}

	// -------------------------------------------------------------------------
	// Arena tracking (for cleanup)
	// -------------------------------------------------------------------------

	/**
	 * Duelists only build inside their arena, and nobody else can build in an
	 * arena while a duel uses it.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockPlaceGuard(final BlockPlaceEvent event) {
		if (!canChange(event.getPlayer(), event.getBlock().getLocation()))
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBucketEmptyGuard(final PlayerBucketEmptyEvent event) {
		if (!canChange(event.getPlayer(), event.getBlock().getLocation()))
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBucketFillGuard(final PlayerBucketFillEvent event) {
		if (!canChange(event.getPlayer(), event.getBlock().getLocation()))
			event.setCancelled(true);
	}

	/** Water or lava scooped up from the arena is put back afterwards. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBucketFill(final PlayerBucketFillEvent event) {
		ActiveDuel duel = DuelManager.getInstance().getDuelAt(event.getBlock().getLocation());
		if (duel != null)
			duel.trackChangedBlock(event.getBlock());
	}

	/**
	 * Whether a player may change the block here, telling them why not.
	 * Players outside a duel can't touch its arena; duelists can't build outside it.
	 */
	private static boolean canChange(Player player, Location location) {
		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(player);
		ActiveDuel here = DuelManager.getInstance().getDuelAt(location.getWorld(), location.getX(), location.getZ());
		if (here != null && here != duel) {
			ColorUtil.sendMessage(player, "&cA duel is taking place here.");
			return false;
		}
		if (duel != null && duel.isInArena() && !duel.arenaContains(location)) {
			ColorUtil.sendMessage(player, "&cYou can only change blocks inside the arena.");
			return false;
		}
		return true;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockPlace(final BlockPlaceEvent event) {
		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(event.getPlayer());
		if (duel != null && duel.isInArena())
			duel.trackPlacedBlock(event.getBlockReplacedState(), event.getPlayer());
	}

	/** Runs before the liquid is placed, so the block's current state is the original. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBucketEmpty(final PlayerBucketEmptyEvent event) {
		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(event.getPlayer());
		if (duel != null && duel.isInArena())
			duel.trackPlacedBlock(event.getBlock().getState(), event.getPlayer());
	}

	/** Boats, minecarts, end crystals and armor stands placed in a duel are cleaned up too. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityPlace(final EntityPlaceEvent event) {
		if (event.getPlayer() == null)
			return;
		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(event.getPlayer());
		if (duel != null && duel.isInArena())
			duel.trackSpawnedEntity(event.getEntity().getUniqueId());
	}

	/**
	 * Breaking blocks during a duel:
	 * <ul>
	 * <li>Blocks made during the duel break normally.</li>
	 * <li>In admin-built arenas nothing else can be broken.</li>
	 * <li>In natural arenas terrain can be broken, but drops nothing and comes
	 * back after the duel. Chests and other block entities can't be broken.</li>
	 * </ul>
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockBreak(final BlockBreakEvent event) {
		Player player = event.getPlayer();
		Block block = event.getBlock();
		if (!canChange(player, block.getLocation())) {
			event.setCancelled(true);
			return;
		}

		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(player);
		if (duel == null || duel.getArena() == null || !duel.arenaContains(block.getLocation())
				|| duel.isDuelMade(block.getLocation()))
			return;

		if (duel.getArena().created()) {
			event.setCancelled(true);
			ColorUtil.sendMessage(player, "&cYou cannot break arena blocks!");
			return;
		}
		if (block.getState(false) instanceof TileState) {
			event.setCancelled(true);
			ColorUtil.sendMessage(player, "&cYou can't break that during a duel.");
			return;
		}

		duel.trackChangedBlock(block);
		event.setDropItems(false);
		event.setExpToDrop(0);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onItemDrop(final PlayerDropItemEvent event) {
		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(event.getPlayer());
		if (duel != null && duel.isFighting())
			duel.trackDroppedItem(event.getItemDrop().getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onProjectileLaunch(final ProjectileLaunchEvent event) {
		if (!(event.getEntity().getShooter() instanceof Player player))
			return;

		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(player);
		if (duel != null && duel.isInArena())
			duel.trackSpawnedEntity(event.getEntity().getUniqueId());
	}

	/** The player behind a hit, or the duel mob (or the duel mob that shot the arrow). */
	private static Entity getKiller(Entity damager) {
		Player player = getAttacker(damager);
		if (player != null)
			return player;
		Entity mob = damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
				? shooter
				: damager;
		return DuelManager.getInstance().getDuelOfMob(mob) != null ? mob : null;
	}

	private static Player getAttacker(Entity damager) {
		if (damager instanceof Player player)
			return player;
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter)
			return shooter;
		if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player source)
			return source;
		return null;
	}
}
