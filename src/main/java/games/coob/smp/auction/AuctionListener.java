package games.coob.smp.auction;

import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Tells players what happened at the auction house while they were away.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuctionListener implements Listener {

	@Getter
	private static final AuctionListener instance = new AuctionListener();

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		// After the join messages, so it isn't missed
		SchedulerUtil.runLater(60, () -> {
			if (!player.isOnline())
				return;
			int stacks = CollectionBox.of(player.getUniqueId()).size();
			if (stacks > 0)
				player.sendMessage(ColorUtil.toComponent("&6[Auction] &7You have &f" + stacks + " stack" + (stacks == 1 ? "" : "s")
						+ " &7waiting in your collection box. ").append(AuctionHouse.collectButton()));
			int offers = AuctionHouse.getInstance().countOffersFor(player.getUniqueId());
			if (offers > 0)
				player.sendMessage(ColorUtil.toComponent("&6[Auction] &7Your listings have &f" + offers + " offer" + (offers == 1 ? "" : "s")
						+ "&7. ").append(AuctionHouse.button("[View]", "/auction mine", "Click to see your listings")));
		});
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		CollectionBox.unload(event.getPlayer().getUniqueId());
	}
}
