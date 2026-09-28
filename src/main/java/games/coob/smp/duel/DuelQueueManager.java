package games.coob.smp.duel;

import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Random matchmaking: the first two players in the queue are matched as soon as
 * the second one joins, so no timer is needed.
 */
public final class DuelQueueManager {

	@Getter
	private static final DuelQueueManager instance = new DuelQueueManager();

	private final Set<UUID> queue = new LinkedHashSet<>();

	private DuelQueueManager() {
	}

	/**
	 * Adds a player to the queue, or matches them with someone already waiting.
	 */
	public void joinQueue(Player player) {
		if (!Settings.DuelSection.ENABLE_DUELS) {
			ColorUtil.sendMessage(player, "&cDuels are currently disabled.");
			return;
		}
		if (DuelManager.getInstance().isInDuel(player)) {
			ColorUtil.sendMessage(player, "&cYou are already in a duel.");
			return;
		}
		if (TeamDuelManager.getInstance().getLobby(player) != null) {
			ColorUtil.sendMessage(player, "&cLeave your team duel lobby first (&e/duel leave&c).");
			return;
		}
		if (queue.contains(player.getUniqueId())) {
			ColorUtil.sendMessage(player, "&cYou are already in the queue.");
			return;
		}

		Player match = pollWaitingPlayer();
		if (match == null) {
			queue.add(player.getUniqueId());
			ColorUtil.sendMessage(player, "&aYou joined the duel queue. Waiting for an opponent...");
			return;
		}

		ColorUtil.sendMessage(match, "&a&lMatch found! &eYou will be dueling &6" + player.getName() + "&e!");
		ColorUtil.sendMessage(player, "&a&lMatch found! &eYou will be dueling &6" + match.getName() + "&e!");
		DuelManager.getInstance().startDuel(match, player);
	}

	/** First queued player who is still online and free, removed from the queue. */
	private Player pollWaitingPlayer() {
		Iterator<UUID> iterator = queue.iterator();
		while (iterator.hasNext()) {
			UUID id = iterator.next();
			iterator.remove();
			Player waiting = Bukkit.getPlayer(id);
			if (waiting != null && !DuelManager.getInstance().isInDuel(waiting))
				return waiting;
		}
		return null;
	}

	public void leaveQueue(Player player) {
		if (queue.remove(player.getUniqueId())) {
			ColorUtil.sendMessage(player, "&cYou left the duel queue.");
		} else {
			ColorUtil.sendMessage(player, "&cYou are not in the queue.");
		}
	}

	public boolean isInQueue(Player player) {
		return queue.contains(player.getUniqueId());
	}

	/** Silently removes a player (they started a duel or quit). */
	public void remove(Player player) {
		queue.remove(player.getUniqueId());
	}

	public void clear() {
		queue.clear();
	}
}
