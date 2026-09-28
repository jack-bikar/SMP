package games.coob.smp.combat;

import games.coob.smp.duel.DuelManager;
import games.coob.smp.util.ColorUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles combat-related events including PvP combat tracking, combat logging
 * punishment, and ghost body NPCs.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CombatListener implements Listener {

	private static final CombatListener instance = new CombatListener();

	public static CombatListener getInstance() {
		return instance;
	}

	@EventHandler
	public void onPlayerJoin(final PlayerJoinEvent event) {
		// Apply combat punishments for rejoining after combat-log
		CombatPunishmentManager.applyRejoinPunishments(event.getPlayer());
	}

	@EventHandler
	public void onPlayerQuit(final PlayerQuitEvent event) {
		final Player player = event.getPlayer();

		if (CombatTracker.isInCombat(player)) {
			CombatPunishmentManager.applyPunishment(player);
		}
		CombatTracker.clear(player);
	}

	/** Dying ends the fight, so leaving right after respawning isn't combat logging. */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDeath(final PlayerDeathEvent event) {
		CombatTracker.clear(event.getEntity());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerDamage(final EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof final Player victim))
			return;

		// Snowballs, eggs and fishing hooks only do knockback, they don't start a fight
		if (event.getDamager() instanceof Snowball || event.getDamager() instanceof Egg
				|| event.getDamager() instanceof FishHook)
			return;

		final Player attacker = getAttacker(event.getDamager());
		if (attacker == null || attacker.equals(victim))
			return;

		// Duel fights are handled by the duel system, not combat logging
		if (DuelManager.getInstance().isInDuel(victim) || DuelManager.getInstance().isInDuel(attacker))
			return;

		CombatTracker.tag(victim);
		CombatTracker.tag(attacker);
	}

	/**
	 * Stop PvP-locked players from dealing damage. Runs early so other plugins see
	 * the hit as cancelled.
	 */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onLockedPlayerAttack(final EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player))
			return;

		final Player attacker = getAttacker(event.getDamager());
		if (attacker != null && CombatPunishmentManager.isPvpLocked(attacker)) {
			event.setCancelled(true);
			long minutesLeft = CombatPunishmentManager.getRemainingLockoutMinutes(attacker);
			ColorUtil.sendMessage(attacker, "&cYou are locked out of PvP for &e" + minutesLeft + " &cmore minutes!");
		}
	}

	@EventHandler
	public void onCombatNPCDeath(final EntityDeathEvent event) {
		if (event.getEntity() instanceof Zombie zombie && CombatNPC.isCombatNPC(zombie)) {
			event.getDrops().clear(); // Loot is dropped by CombatNPC itself
			event.setDroppedExp(0);
			CombatNPC.onNPCKilled(zombie, zombie.getKiller());
		}
	}

	private static Player getAttacker(Entity damager) {
		if (damager instanceof Player player)
			return player;
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter)
			return shooter;
		return null;
	}
}
