package games.coob.smp.combat;

import games.coob.smp.settings.Settings;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players are in PvP combat. Each hit pushes the expiry forward,
 * so no timers need to be scheduled.
 */
public final class CombatTracker {

	private static final Map<UUID, Long> combatExpiry = new ConcurrentHashMap<>();

	private CombatTracker() {
	}

	public static void tag(Player player) {
		combatExpiry.put(player.getUniqueId(),
				System.currentTimeMillis() + Settings.CombatSection.SECONDS_TILL_PLAYER_LEAVES_COMBAT * 1000L);
	}

	public static boolean isInCombat(Player player) {
		Long expiry = combatExpiry.get(player.getUniqueId());
		if (expiry == null)
			return false;
		if (expiry <= System.currentTimeMillis()) {
			combatExpiry.remove(player.getUniqueId());
			return false;
		}
		return true;
	}

	public static void clear(Player player) {
		combatExpiry.remove(player.getUniqueId());
	}

	public static void clearAll() {
		combatExpiry.clear();
	}
}
