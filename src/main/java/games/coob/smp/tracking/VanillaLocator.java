package games.coob.smp.tracking;

import games.coob.smp.PlayerCache;
import games.coob.smp.duel.DuelManager;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vanilla locator bar mode (Enable_Locator_Bar: true).
 * <ul>
 * <li>The game draws everyone on the bar as usual.</li>
 * <li>When you face a player's waypoint, their name appears above your hotbar in
 * the same colour as their waypoint.</li>
 * <li>/track death adds your death spot to the bar.</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VanillaLocator implements Listener, Runnable {

	private static final VanillaLocator instance = new VanillaLocator();

	/** How close (in degrees) your view has to be to a waypoint to count as facing it. */
	private static final double FOCUS_DEGREES = 6.0;
	/** The action bar fades after ~2s, so the name is re-sent at least this often (ticks). */
	private static final int RESEND_TICKS = 30;
	private static final double DEATH_REACHED_DISTANCE = 4.0;
	private static final UUID DEATH_TARGET = UUID.fromString("00000000-0000-0000-0000-000000000001");

	public static final int PERIOD_TICKS = 4;

	/** What each player currently sees in the action bar, so it is only sent when needed. */
	private final Map<UUID, Shown> shown = new HashMap<>();
	/** Players whose client currently has their death waypoint. */
	private final Set<UUID> deathWaypointSent = new HashSet<>();
	private int ticks;

	private record Shown(String key, int sentAt) {
	}

	public static VanillaLocator getInstance() {
		return instance;
	}

	private static boolean isActive() {
		return Settings.LocatorSection.ENABLE_LOCATOR_BAR;
	}

	// -------------------------------------------------------------------------
	// Events
	// -------------------------------------------------------------------------

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		if (!isActive())
			return;
		Player player = event.getPlayer();
		LocatorBarManager.applyVanillaMode(player, LocatorBarManager.isAllowedIn(player.getWorld()));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorldChange(PlayerChangedWorldEvent event) {
		if (!isActive())
			return;
		Player player = event.getPlayer();
		LocatorBarManager.applyVanillaMode(player, LocatorBarManager.isAllowedIn(player.getWorld()));
		// Remove the death marker; it is sent again once the player is back in that world
		refreshDeathWaypoint(player);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onRespawn(PlayerRespawnEvent event) {
		if (!isActive())
			return;
		refreshDeathWaypoint(event.getPlayer());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		shown.remove(event.getPlayer().getUniqueId());
		deathWaypointSent.remove(event.getPlayer().getUniqueId());
	}

	/** /track death or /track stop changed what the player tracks. */
	public void refreshDeathWaypoint(Player player) {
		if (deathWaypointSent.remove(player.getUniqueId()))
			WaypointPacketSender.removeWaypoint(player, deathWaypointId(player));
		WaypointPacketSender.forget(player.getUniqueId());
	}

	private static UUID deathWaypointId(Player player) {
		return WaypointPacketSender.generateWaypointId(player.getUniqueId(), DEATH_TARGET);
	}

	// -------------------------------------------------------------------------
	// Task
	// -------------------------------------------------------------------------

	@Override
	public void run() {
		if (!isActive())
			return;
		ticks += PERIOD_TICKS;

		for (World world : Bukkit.getWorlds()) {
			List<Player> players = world.getPlayers();
			if (players.isEmpty())
				continue;

			boolean barEnabled = !Boolean.FALSE.equals(world.getGameRuleValue(GameRules.LOCATOR_BAR))
					&& LocatorBarManager.isAllowedIn(world);
			for (Player viewer : players) {
				// Duels use the action bar for their own messages
				if (!barEnabled || viewer.getGameMode() == GameMode.SPECTATOR
						|| DuelManager.getInstance().isInDuel(viewer)
						|| LocatorBarManager.getReceiveRange(viewer) <= 0) {
					clear(viewer);
					continue;
				}
				updateDeathWaypoint(viewer);
				updateFocus(viewer, players);
			}
		}
	}

	private void updateDeathWaypoint(Player viewer) {
		PlayerCache cache = PlayerCache.from(viewer);
		if (!cache.isTrackingDeath())
			return;

		Location death = cache.getDeathLocation();
		if (death == null || death.getWorld() == null) {
			cache.stopTrackingDeath();
			return;
		}
		if (!death.getWorld().equals(viewer.getWorld()))
			return;

		if (death.distanceSquared(viewer.getLocation()) <= DEATH_REACHED_DISTANCE * DEATH_REACHED_DISTANCE) {
			cache.stopTrackingDeath();
			refreshDeathWaypoint(viewer);
			ColorUtil.sendMessage(viewer, "&aYou reached your death location.");
			return;
		}

		if (!deathWaypointSent.contains(viewer.getUniqueId())
				&& WaypointPacketSender.sendWaypoint(viewer, death, deathWaypointId(viewer)))
			deathWaypointSent.add(viewer.getUniqueId());
	}

	private void updateFocus(Player viewer, List<Player> candidates) {
		Location eye = viewer.getLocation();
		double receiveRange = LocatorBarManager.getReceiveRange(viewer);

		String bestKey = null;
		Component bestText = null;
		double bestAngle = FOCUS_DEGREES;

		// Same visibility rules as the game uses for the locator bar
		for (Player target : candidates) {
			if (target == viewer || target.getGameMode() == GameMode.SPECTATOR || !viewer.canSee(target))
				continue;
			double range = Math.min(receiveRange, LocatorBarManager.getTransmitRange(target));
			if (range <= 0 || target.getLocation().distanceSquared(eye) > range * range)
				continue;

			double angle = angleTo(eye, target.getLocation());
			if (angle <= bestAngle) {
				bestAngle = angle;
				bestKey = target.getUniqueId().toString();
				String name = PlainTextComponentSerializer.plainText().serialize(target.displayName());
				bestText = Component.text(name, TextColor.color(WaypointColors.of(target)));
			}
		}

		if (deathWaypointSent.contains(viewer.getUniqueId())) {
			Location death = PlayerCache.from(viewer).getDeathLocation();
			if (death != null && death.getWorld() == viewer.getWorld() && angleTo(eye, death) <= bestAngle) {
				bestKey = "death";
				bestText = Component.text("Death location",
						TextColor.color(WaypointColors.defaultColor(deathWaypointId(viewer))));
			}
		}

		if (bestKey == null) {
			clear(viewer);
			return;
		}

		Shown previous = shown.get(viewer.getUniqueId());
		if (previous == null || !previous.key().equals(bestKey) || ticks - previous.sentAt() >= RESEND_TICKS) {
			viewer.sendActionBar(bestText);
			shown.put(viewer.getUniqueId(), new Shown(bestKey, ticks));
		}
	}

	/** Horizontal angle between where the player looks and the target, in degrees. */
	private static double angleTo(Location from, Location to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		if (dx * dx + dz * dz < 1.0E-4)
			return 180;
		double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
		double diff = (targetYaw - from.getYaw()) % 360;
		if (diff > 180)
			diff -= 360;
		if (diff < -180)
			diff += 360;
		return Math.abs(diff);
	}

	/**
	 * Clears the name, but only if it is still on screen, so other action bar
	 * messages (music, bed, plugins) aren't wiped.
	 */
	private void clear(Player viewer) {
		Shown previous = shown.remove(viewer.getUniqueId());
		if (previous != null && ticks - previous.sentAt() < RESEND_TICKS + PERIOD_TICKS)
			viewer.sendActionBar(Component.empty());
	}

	/**
	 * Re-applies the allowed dimensions to everyone online (after /smp reload).
	 */
	public void reapplyToOnlinePlayers() {
		if (!isActive())
			return;
		for (Player player : Bukkit.getOnlinePlayers())
			LocatorBarManager.applyVanillaMode(player, LocatorBarManager.isAllowedIn(player.getWorld()));
	}
}
