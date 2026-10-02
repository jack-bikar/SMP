package games.coob.smp.trade;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Ends trades when a player can't go on with them.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TradeListener implements Listener {

	@Getter
	private static final TradeListener instance = new TradeListener();

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		TradeManager.getInstance().handleQuit(event.getPlayer());
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent event) {
		TradeManager.getInstance().cancelTrade(event.getPlayer(),
				"&cThe trade was cancelled: " + event.getPlayer().getName() + " died.");
	}
}
