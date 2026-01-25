package games.coob.smp.tracking;

import games.coob.smp.PlayerCache;
import games.coob.smp.SMPPlugin;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TrackingRequestManager {
	private static final TrackingRequestManager instance = new TrackingRequestManager();
	private final Map<UUID, TrackingRequest> pendingRequests = new HashMap<>();
	private static final int EXPIRATION_SECONDS = 30;

	public static TrackingRequestManager getInstance() {
		return instance;
	}

	public void sendTrackingRequest(Player tracker, Player target) {
		// Cancel any existing request from this tracker
		cancelRequest(tracker.getUniqueId());

		TrackingRequest request = new TrackingRequest(tracker.getUniqueId(), target.getUniqueId());
		pendingRequests.put(tracker.getUniqueId(), request);

		// Send clickable message to target
		Component acceptButton = Component.text("[ACCEPT]", NamedTextColor.GREEN)
				.clickEvent(ClickEvent.runCommand("/tracking accept " + tracker.getName()))
				.hoverEvent(HoverEvent.showText(Component.text("Click to accept tracking request")));

		Component denyButton = Component.text("[DENY]", NamedTextColor.RED)
				.clickEvent(ClickEvent.runCommand("/tracking deny " + tracker.getName()))
				.hoverEvent(HoverEvent.showText(Component.text("Click to deny tracking request")));

		Component message = Component.text()
				.append(Component.text(tracker.getName(), NamedTextColor.YELLOW))
				.append(Component.text(" wants to track your location. ", NamedTextColor.WHITE))
				.append(acceptButton)
				.append(Component.text(" ", NamedTextColor.WHITE))
				.append(denyButton)
				.append(Component.text(" (Expires in 30 seconds)", NamedTextColor.GRAY))
				.build();

		target.sendMessage(message);
		ColorUtil.sendMessage(tracker, "&eTracking request sent to &3" + target.getName() + "&e. Waiting for response...");

		// Schedule expiration
		SchedulerUtil.runLater(EXPIRATION_SECONDS * 20, () -> {
			if (pendingRequests.containsKey(tracker.getUniqueId())) {
				TrackingRequest req = pendingRequests.remove(tracker.getUniqueId());
				if (req != null && req.getTrackerUUID().equals(tracker.getUniqueId())) {
					Player trackerPlayer = Bukkit.getPlayer(tracker.getUniqueId());
					if (trackerPlayer != null) {
						ColorUtil.sendMessage(trackerPlayer, "&cTracking request to &3" + target.getName() + " &cexpired.");
					}
				}
			}
		});
	}

	public boolean acceptRequest(Player target, String trackerName) {
		Player tracker = Bukkit.getPlayer(trackerName);
		if (tracker == null) {
			ColorUtil.sendMessage(target, "&cPlayer not found.");
			return false;
		}

		TrackingRequest request = pendingRequests.get(tracker.getUniqueId());
		if (request == null || !request.getTargetUUID().equals(target.getUniqueId())) {
			ColorUtil.sendMessage(target, "&cNo pending tracking request from that player.");
			return false;
		}

		pendingRequests.remove(tracker.getUniqueId());
		ColorUtil.sendMessage(target, "&aYou accepted the tracking request from &3" + tracker.getName() + "&a.");
		ColorUtil.sendMessage(tracker, "&a" + target.getName() + " &aaccepted your tracking request!");

		// Start tracking
		PlayerCache trackerCache = PlayerCache.from(tracker);
		trackerCache.setTrackingLocation("Player");
		trackerCache.setTargetByUUID(target.getUniqueId());

		// Immediately update Player Locator Bar
		LocatorBarManager locatorBar = new LocatorBarManager(tracker);
		LocatorBarManager targetLocatorBar = new LocatorBarManager(target);
		
		// Enable waypoint transmission for target so they appear as a waypoint
		targetLocatorBar.enableTransmit();
		
		// Enable locator bar for tracker
		locatorBar.enableTemporarily();
		
		// Set compass target to point to the player or portal
		if (target.isOnline()) {
			if (target.getWorld() == tracker.getWorld()) {
				// Same dimension - point to target player
				tracker.setCompassTarget(target.getLocation());
			} else {
				// Different dimension - point to portal if available
				org.bukkit.Location portalLoc = trackerCache.getPortalLocation();
				if (portalLoc != null && portalLoc.getWorld() == tracker.getWorld()) {
					tracker.setCompassTarget(portalLoc);
				} else {
					// No portal found, still enable locator bar but it won't show anything useful
					// The LocatorTask will handle updating this
					tracker.setCompassTarget(target.getLocation());
				}
			}
		}

		return true;
	}

	public boolean denyRequest(Player target, String trackerName) {
		Player tracker = Bukkit.getPlayer(trackerName);
		if (tracker == null) {
			ColorUtil.sendMessage(target, "&cPlayer not found.");
			return false;
		}

		TrackingRequest request = pendingRequests.get(tracker.getUniqueId());
		if (request == null || !request.getTargetUUID().equals(target.getUniqueId())) {
			ColorUtil.sendMessage(target, "&cNo pending tracking request from that player.");
			return false;
		}

		pendingRequests.remove(tracker.getUniqueId());
		ColorUtil.sendMessage(target, "&cYou denied the tracking request from &3" + tracker.getName() + "&c.");
		ColorUtil.sendMessage(tracker, "&c" + target.getName() + " &cdenied your tracking request.");

		return true;
	}

	public void cancelRequest(UUID trackerUUID) {
		pendingRequests.remove(trackerUUID);
	}

	public void cancelTracking(Player target) {
		// Find all players tracking this target
		for (Player player : Bukkit.getOnlinePlayers()) {
			PlayerCache cache = PlayerCache.from(player);
			if (cache.getTrackingLocation() != null && cache.getTrackingLocation().equals("Player")) {
				if (cache.getTargetByUUID() != null && cache.getTargetByUUID().equals(target.getUniqueId())) {
					cache.setTrackingLocation(null);
					cache.setTargetByUUID(null);
					ColorUtil.sendMessage(player, "&c" + target.getName() + " &chas cancelled tracking.");
				}
			}
		}
		ColorUtil.sendMessage(target, "&aYou have cancelled all tracking requests.");
	}

	private static class TrackingRequest {
		private final UUID trackerUUID;
		private final UUID targetUUID;

		public TrackingRequest(UUID trackerUUID, UUID targetUUID) {
			this.trackerUUID = trackerUUID;
			this.targetUUID = targetUUID;
		}

		public UUID getTrackerUUID() {
			return trackerUUID;
		}

		public UUID getTargetUUID() {
			return targetUUID;
		}
	}
}
