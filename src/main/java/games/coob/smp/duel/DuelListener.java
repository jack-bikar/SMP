package games.coob.smp.duel;

import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Handles duel-related events.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DuelListener implements Listener {

	private static final DuelListener instance = new DuelListener();

	public static DuelListener getInstance() {
		return instance;
	}

	/**
	 * Handle player quit - forfeit if in duel.
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerQuit(final PlayerQuitEvent event) {
		Player player = event.getPlayer();

		// Handle duel forfeit
		DuelManager.getInstance().handlePlayerQuit(player);

		// Remove from queue
		DuelQueueManager.getInstance().handlePlayerQuit(player);

		// Cancel arena editing
		ArenaManager.getInstance().handlePlayerQuit(player);
	}

	/**
	 * Handle player death in duel.
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerDeath(final PlayerDeathEvent event) {
		Player player = event.getEntity();
		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(player);

		if (duel == null || !duel.isFighting())
			return;

		Player killer = player.getKiller();

		// Modify drops based on loot mode
		Settings.DuelSection.LootMode lootMode = Settings.DuelSection.LOOT_MODE;

		if (lootMode == Settings.DuelSection.LootMode.KEEP_INVENTORY) {
			event.setKeepInventory(true);
			event.setKeepLevel(true);
			event.getDrops().clear();
			event.setDroppedExp(0);
		}

		// Handle death
		DuelManager.getInstance().handlePlayerDeath(player, killer);
	}

	/**
	 * Handle player respawn in duel.
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerRespawn(final PlayerRespawnEvent event) {
		Player player = event.getPlayer();
		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(player);

		if (duel != null) {
			// Respawn at lobby or original location
			org.bukkit.Location lobby = games.coob.smp.duel.model.ArenaRegistry.getInstance().getLobbySpawn();
			if (lobby != null) {
				event.setRespawnLocation(lobby);
			}
		}
	}

	/**
	 * Handle damage during duels.
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onEntityDamageByEntity(final EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player victim))
			return;

		Player attacker = getAttacker(event.getDamager());
		if (attacker == null)
			return;

		ActiveDuel victimDuel = DuelManager.getInstance().getActiveDuel(victim);
		ActiveDuel attackerDuel = DuelManager.getInstance().getActiveDuel(attacker);

		// Both players must be in the same duel
		if (victimDuel != attackerDuel) {
			// One is in duel, other is not - cancel
			if (victimDuel != null || attackerDuel != null) {
				event.setCancelled(true);
				return;
			}
		}

		// If both in a duel, check if duel is active
		if (victimDuel != null) {
			if (victimDuel.getState() != ActiveDuel.DuelState.ACTIVE) {
				// Not in active fighting state
				event.setCancelled(true);
				return;
			}

			// Ensure attacker is the opponent
			if (!victimDuel.hasPlayer(attacker)) {
				event.setCancelled(true);
			}
		}
	}

	/**
	 * Track blocks placed during duel.
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockPlace(final BlockPlaceEvent event) {
		Player player = event.getPlayer();
		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(player);

		if (duel != null && duel.isFighting()) {
			duel.trackPlacedBlock(event.getBlock().getLocation());
		}
	}

	/**
	 * Prevent block breaking in created arenas (optional).
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onBlockBreak(final BlockBreakEvent event) {
		Player player = event.getPlayer();
		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(player);

		if (duel != null && duel.getArena() != null) {
			// In a created arena - only allow breaking blocks placed during this duel
			if (!duel.getPlacedBlocks().contains(event.getBlock().getLocation())) {
				event.setCancelled(true);
				ColorUtil.sendMessage(player, "&cYou cannot break arena blocks!");
			}
		}
	}

	/**
	 * Track items dropped during duel.
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onItemDrop(final PlayerDropItemEvent event) {
		Player player = event.getPlayer();
		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(player);

		if (duel != null && duel.isFighting()) {
			duel.trackDroppedItem(event.getItemDrop().getUniqueId());
		}
	}

	/**
	 * Track projectiles launched during duel.
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onProjectileLaunch(final ProjectileLaunchEvent event) {
		if (!(event.getEntity().getShooter() instanceof Player player))
			return;

		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(player);

		if (duel != null && duel.isFighting()) {
			duel.trackSpawnedEntity(event.getEntity().getUniqueId());
		}
	}

	/**
	 * Gets the attacking player from a damager entity.
	 */
	private Player getAttacker(Entity damager) {
		if (damager instanceof Player) {
			return (Player) damager;
		}
		if (damager instanceof Projectile projectile) {
			if (projectile.getShooter() instanceof Player) {
				return (Player) projectile.getShooter();
			}
		}
		if (damager instanceof TNTPrimed tnt) {
			if (tnt.getSource() instanceof Player) {
				return (Player) tnt.getSource();
			}
		}
		return null;
	}
}
