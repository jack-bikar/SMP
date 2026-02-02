package games.coob.smp.combat;

import games.coob.smp.PlayerCache;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
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
		final Player player = event.getPlayer();

		// Apply combat punishments for rejoining after combat-log
		CombatPunishmentManager.applyRejoinPunishments(player);
	}

	@EventHandler
	public void onPlayerQuit(final PlayerQuitEvent event) {
		final Player player = event.getPlayer();
		final PlayerCache cache = PlayerCache.from(player);

		// Apply combat punishment if player is in combat
		if (cache.isInCombat()) {
			CombatPunishmentManager.applyPunishment(player);
		}
	}

	@EventHandler
	public void onPlayerDamage(final EntityDamageByEntityEvent event) {
		final Entity entity = event.getEntity();
		final Entity damager = event.getDamager();

		// Only track PvP combat (player vs player)
		if (entity instanceof final Player victim && damager instanceof final Player attacker) {
			// Check if attacker is PvP locked
			if (CombatPunishmentManager.isPvpLocked(attacker)) {
				event.setCancelled(true);
				long minutesLeft = CombatPunishmentManager.getRemainingLockoutMinutes(attacker);
				ColorUtil.sendMessage(attacker,
						"&cYou are locked out of PvP for &e" + minutesLeft + " &cmore minutes!");
				return;
			}

			// Mark both players as in combat
			final PlayerCache victimCache = PlayerCache.from(victim);
			final PlayerCache attackerCache = PlayerCache.from(attacker);

			victimCache.setInCombat(true);
			attackerCache.setInCombat(true);

			SchedulerUtil.runLater(20L * Settings.CombatSection.SECONDS_TILL_PLAYER_LEAVES_COMBAT, () -> {
				victimCache.setInCombat(false);
				attackerCache.setInCombat(false);
			});
		}
	}

	@EventHandler
	public void onCombatNPCDeath(final EntityDeathEvent event) {
		if (event.getEntity() instanceof Zombie zombie) {
			if (CombatNPC.isCombatNPC(zombie)) {
				event.getDrops().clear(); // Prevent zombie drops
				event.setDroppedExp(0);

				Player killer = zombie.getKiller();
				CombatNPC.onNPCKilled(zombie, killer);
			}
		}
	}
}
