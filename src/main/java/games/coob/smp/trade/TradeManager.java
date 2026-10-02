package games.coob.smp.trade;

import games.coob.smp.PlayerCache;
import games.coob.smp.combat.CombatTracker;
import games.coob.smp.duel.DuelManager;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Trade requests and the trades going on. A trade starts when the other
 * player accepts a request (/trade accept, or the button in chat).
 */
public final class TradeManager {

	@Getter
	private static final TradeManager instance = new TradeManager();

	/** Requester -> their request (one at a time; a new one replaces it). */
	private final Map<UUID, Request> requests = new HashMap<>();
	/** Both players of each trade -> the trade. */
	private final Map<UUID, TradeSession> sessions = new HashMap<>();

	/** Compared by identity: a timer only expires the request it was started for. */
	private static final class Request {
		private final UUID targetId;

		private Request(UUID targetId) {
			this.targetId = targetId;
		}
	}

	private TradeManager() {
	}

	public void sendRequest(Player sender, Player target) {
		if (!Settings.TradeSection.ENABLED) {
			ColorUtil.sendMessage(sender, "&cTrading is disabled.");
			return;
		}
		if (sender.equals(target)) {
			ColorUtil.sendMessage(sender, "&cYou can't trade with yourself.");
			return;
		}
		String problem = whyCantStart(sender, target);
		if (problem != null) {
			ColorUtil.sendMessage(sender, problem);
			return;
		}
		Request existing = requests.get(sender.getUniqueId());
		if (existing != null && existing.targetId.equals(target.getUniqueId())) {
			ColorUtil.sendMessage(sender, "&eYou already sent &f" + target.getName() + " &ea trade request.");
			return;
		}

		Request request = new Request(target.getUniqueId());
		requests.put(sender.getUniqueId(), request);

		Component accept = Component.text("[ACCEPT]", NamedTextColor.GREEN)
				.hoverEvent(HoverEvent.showText(Component.text("Click to open the trade")))
				.clickEvent(ClickEvent.runCommand("/trade accept " + sender.getName()));
		Component deny = Component.text("[DENY]", NamedTextColor.RED)
				.hoverEvent(HoverEvent.showText(Component.text("Click to decline")))
				.clickEvent(ClickEvent.runCommand("/trade deny " + sender.getName()));
		target.sendMessage(Component.text()
				.append(Component.text(sender.getName(), NamedTextColor.YELLOW))
				.append(Component.text(" wants to trade with you. ", NamedTextColor.GOLD))
				.append(accept)
				.append(Component.text(" "))
				.append(deny)
				.build());
		ColorUtil.sendMessage(sender, "&aTrade request sent to &e" + target.getName() + "&a.");

		UUID senderId = sender.getUniqueId();
		String targetName = target.getName();
		SchedulerUtil.runLater(20L * Settings.TradeSection.REQUEST_TIMEOUT_SECONDS, () -> {
			if (requests.remove(senderId, request)) {
				Player stillOnline = Bukkit.getPlayer(senderId);
				if (stillOnline != null)
					ColorUtil.sendMessage(stillOnline, "&7Your trade request to " + targetName + " expired.");
			}
		});
	}

	public void accept(Player target, String senderName) {
		if (!Settings.TradeSection.ENABLED) {
			ColorUtil.sendMessage(target, "&cTrading is disabled.");
			return;
		}
		Player sender = Bukkit.getPlayerExact(senderName);
		if (!hasRequest(sender, target)) {
			ColorUtil.sendMessage(target, "&cYou have no trade request from " + senderName + ".");
			return;
		}
		// The request stays, so it can be accepted once the problem (e.g. combat) is over
		String problem = whyCantStart(target, sender);
		if (problem != null) {
			ColorUtil.sendMessage(target, problem);
			return;
		}

		requests.remove(sender.getUniqueId());
		TradeSession session = new TradeSession(sender, target);
		sessions.put(sender.getUniqueId(), session);
		sessions.put(target.getUniqueId(), session);
		session.open();
	}

	public void deny(Player target, String senderName) {
		Player sender = Bukkit.getPlayerExact(senderName);
		if (!hasRequest(sender, target)) {
			ColorUtil.sendMessage(target, "&cYou have no trade request from " + senderName + ".");
			return;
		}
		requests.remove(sender.getUniqueId());
		ColorUtil.sendMessage(target, "&7You declined " + sender.getName() + "'s trade request.");
		ColorUtil.sendMessage(sender, "&c" + target.getName() + " declined your trade request.");
	}

	private boolean hasRequest(@Nullable Player sender, Player target) {
		Request request = sender != null ? requests.get(sender.getUniqueId()) : null;
		return request != null && request.targetId.equals(target.getUniqueId());
	}

	/** Why a new trade can't start, as a message for {@code player}, or null if it can. */
	private @Nullable String whyCantStart(Player player, Player other) {
		if (sessions.containsKey(player.getUniqueId()))
			return "&cYou are already trading.";
		if (sessions.containsKey(other.getUniqueId()))
			return "&c" + other.getName() + " is already trading with someone.";
		return whyCantTrade(player, other);
	}

	/**
	 * Why the two players can't trade right now (a duel, combat, too far
	 * apart), as a message both can be shown, or null if they can.
	 */
	public static @Nullable String whyCantTrade(Player player, Player other) {
		for (Player each : new Player[] { player, other }) {
			if (DuelManager.getInstance().isInDuel(each))
				return "&c" + each.getName() + " is in a duel; trading has to wait.";
			if (CombatTracker.isInCombat(each))
				return "&c" + each.getName() + " is in combat; trading has to wait.";
			// Their own gear comes back from a kit duel after they respawn, replacing the whole inventory
			if (each.isDead())
				return "&c" + each.getName() + " is dead; trading has to wait.";
			if (PlayerCache.from(each).hasKitStash())
				return "&c" + each.getName() + "'s own items haven't come back from a kit duel yet; trading has to wait.";
		}
		int maxDistance = Settings.TradeSection.MAX_DISTANCE;
		if (maxDistance > 0 && (!player.getWorld().equals(other.getWorld())
				|| player.getLocation().distanceSquared(other.getLocation()) > (double) maxDistance * maxDistance))
			return "&cYou need to be within " + maxDistance + " blocks of each other to trade.";
		return null;
	}

	public @Nullable TradeSession getSession(Player player) {
		return sessions.get(player.getUniqueId());
	}

	/** Called by a trade when it ends, however it ends. */
	void forget(TradeSession session) {
		sessions.values().removeIf(each -> each == session);
	}

	/** The player's trade (if any) ends, e.g. because they died. */
	public void cancelTrade(Player player, String message) {
		TradeSession session = sessions.get(player.getUniqueId());
		if (session != null)
			session.cancel(message);
	}

	/** The player left: their trade and every request from or to them end. */
	public void handleQuit(Player player) {
		cancelTrade(player, "&cThe trade was cancelled: " + player.getName() + " left.");
		UUID id = player.getUniqueId();
		requests.remove(id);
		requests.values().removeIf(request -> request.targetId.equals(id));
	}

	/** Plugin disable: nothing has moved yet in a running trade, so ending it is all it takes. */
	public void cancelAll() {
		for (TradeSession session : new HashSet<>(sessions.values()))
			session.cancel("&cThe trade was cancelled because the server is stopping.");
		requests.clear();
	}
}
