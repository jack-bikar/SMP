package games.coob.smp.tracking;

import games.coob.smp.PlayerCache;
import games.coob.smp.task.LocatorTask;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.tracking.WaypointPacketSender;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages tracking requests between players.
 */
public final class TrackingRequestManager {

    private static final TrackingRequestManager instance = new TrackingRequestManager();
    private static final int EXPIRATION_SECONDS = 30;

    private final Map<UUID, TrackingRequest> pendingRequests = new ConcurrentHashMap<>();

    private TrackingRequestManager() {
    }

    public static TrackingRequestManager getInstance() {
        return instance;
    }

    /**
     * Send a tracking request from tracker to target.
     */
    public void sendTrackingRequest(Player tracker, Player target) {
        pendingRequests.remove(tracker.getUniqueId());

        TrackingRequest request = new TrackingRequest(tracker.getUniqueId(), target.getUniqueId());
        pendingRequests.put(tracker.getUniqueId(), request);

        Component acceptButton = Component.text("[ACCEPT]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/track accept " + tracker.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Click to accept tracking request")));

        Component denyButton = Component.text("[DENY]", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/track deny " + tracker.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Click to deny tracking request")));

        Component message = Component.text()
                .append(Component.text(tracker.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" wants to track your location. ", NamedTextColor.WHITE))
                .append(acceptButton)
                .append(Component.text(" "))
                .append(denyButton)
                .append(Component.text(" (Expires in 30 seconds)", NamedTextColor.GRAY))
                .build();

        target.sendMessage(message);
        ColorUtil.sendMessage(tracker, "&eTracking request sent to &3" + target.getName() + "&e. Waiting for response...");

        SchedulerUtil.runLater(EXPIRATION_SECONDS * 20L, () -> {
            TrackingRequest req = pendingRequests.remove(tracker.getUniqueId());
            if (req != null) {
                Player trackerPlayer = Bukkit.getPlayer(tracker.getUniqueId());
                if (trackerPlayer != null && trackerPlayer.isOnline()) {
                    ColorUtil.sendMessage(trackerPlayer, "&cTracking request to &3" + target.getName() + " &cexpired.");
                }
            }
        });
    }

    /**
     * Accept a tracking request.
     */
    public boolean acceptRequest(Player target, String trackerName) {
        Player tracker = Bukkit.getPlayer(trackerName);
        if (tracker == null || !tracker.isOnline()) {
            ColorUtil.sendMessage(target, "&cPlayer not found or offline.");
            return false;
        }

        TrackingRequest request = pendingRequests.get(tracker.getUniqueId());
        if (request == null || !request.targetUUID.equals(target.getUniqueId())) {
            ColorUtil.sendMessage(target, "&cNo pending tracking request from that player.");
            return false;
        }

        pendingRequests.remove(tracker.getUniqueId());

        // Set up tracking state using new multi-tracking system
        PlayerCache trackerCache = PlayerCache.from(tracker);
        trackerCache.addTrackedPlayer(target.getUniqueId());

        // Register tracker
        TrackingRegistry.startTracking(tracker.getUniqueId());

        // Enable locator bar - LocatorTask will handle targeting
        if (!WaypointPacketSender.isAvailable()) {
            LocatorBarManager.enableTransmit(target);
        }
        LocatorBarManager.enableReceive(tracker);

        // Set initial target (same dimension = direct, different = task will handle portal)
        if (target.getWorld().equals(tracker.getWorld())) {
            LocatorBarManager.setTarget(tracker, target);
        }
        // If different dimension, LocatorTask will calculate portal on next tick

        ColorUtil.sendMessage(target, "&aYou accepted the tracking request from &3" + tracker.getName() + "&a.");
        ColorUtil.sendMessage(tracker, "&a" + target.getName() + " &aaccepted your tracking request!");

        return true;
    }

    /**
     * Deny a tracking request.
     */
    public boolean denyRequest(Player target, String trackerName) {
        Player tracker = Bukkit.getPlayer(trackerName);
        if (tracker == null) {
            ColorUtil.sendMessage(target, "&cPlayer not found.");
            return false;
        }

        TrackingRequest request = pendingRequests.get(tracker.getUniqueId());
        if (request == null || !request.targetUUID.equals(target.getUniqueId())) {
            ColorUtil.sendMessage(target, "&cNo pending tracking request from that player.");
            return false;
        }

        pendingRequests.remove(tracker.getUniqueId());
        ColorUtil.sendMessage(target, "&cYou denied the tracking request from &3" + tracker.getName() + "&c.");
        ColorUtil.sendMessage(tracker, "&c" + target.getName() + " &cdenied your tracking request.");

        return true;
    }

    /**
     * Revoke a single tracker's ability to track the target (called by target from "Who's tracking me" menu).
     */
    public void revokeTracker(Player target, Player tracker) {
        PlayerCache cache = PlayerCache.from(tracker);
        cache.removeTrackedPlayer(target.getUniqueId());
        WaypointPacketSender.removeWaypoint(tracker,
                WaypointPacketSender.generateWaypointId(tracker.getUniqueId(), target.getUniqueId()));

        if (!cache.isTracking()) {
            TrackingRegistry.stopTracking(tracker.getUniqueId());
            LocatorBarManager.disableReceive(tracker);
            LocatorBarManager.clearTarget(tracker);
            LocatorTask.cleanupPlayer(tracker.getUniqueId());
            WaypointPacketSender.clearWaypoint(tracker);
        }

        ColorUtil.sendMessage(tracker, "&c" + target.getName() + " &chas revoked your tracking.");
        ColorUtil.sendMessage(target, "&aYou revoked tracking for &3" + tracker.getName() + "&a.");
    }

    /**
     * Cancel all tracking of a specific target player (by others tracking them).
     */
    public void cancelTracking(Player target) {
        for (UUID trackerUUID : TrackingRegistry.getActiveTrackers()) {
            Player tracker = Bukkit.getPlayer(trackerUUID);
            if (tracker == null || !tracker.isOnline()) continue;

            PlayerCache cache = PlayerCache.from(tracker);
            TrackedTarget trackedTarget = cache.getTrackedTarget(target.getUniqueId());

            if (trackedTarget != null) {
                cache.removeTrackedPlayer(target.getUniqueId());
                WaypointPacketSender.removeWaypoint(tracker,
                        WaypointPacketSender.generateWaypointId(trackerUUID, target.getUniqueId()));

                // If not tracking anything anymore, stop completely
                if (!cache.isTracking()) {
                    TrackingRegistry.stopTracking(trackerUUID);
                    LocatorBarManager.disableReceive(tracker);
                    LocatorBarManager.clearTarget(tracker);
                    LocatorTask.cleanupPlayer(trackerUUID);
                    WaypointPacketSender.clearWaypoint(tracker);
                }

                ColorUtil.sendMessage(tracker, "&c" + target.getName() + " &chas cancelled tracking.");
            }
        }
        ColorUtil.sendMessage(target, "&aYou have cancelled all tracking requests.");
    }

    private record TrackingRequest(UUID trackerUUID, UUID targetUUID) {
    }
}
