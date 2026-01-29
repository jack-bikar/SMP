package games.coob.smp.tracking;

import games.coob.smp.PlayerCache;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
        // Cancel any existing request from this tracker
        pendingRequests.remove(tracker.getUniqueId());

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
                .append(Component.text(" "))
                .append(denyButton)
                .append(Component.text(" (Expires in 30 seconds)", NamedTextColor.GRAY))
                .build();

        target.sendMessage(message);
        ColorUtil.sendMessage(tracker,
                "&eTracking request sent to &3" + target.getName() + "&e. Waiting for response...");

        // Schedule expiration
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

        // Start tracking
        PlayerCache trackerCache = PlayerCache.from(tracker);
        trackerCache.setTrackingLocation("Player");
        trackerCache.setTargetByUUID(target.getUniqueId());

        // Register in tracking registry
        TrackingRegistry.startTracking(tracker.getUniqueId());

        // Enable locator bar
        LocatorBarManager.enableTransmit(target);
        LocatorBarManager.enableReceive(tracker);

        // Set initial target
        if (target.getWorld().equals(tracker.getWorld())) {
            LocatorBarManager.setTarget(tracker, target);
        } else {
            // Different dimension - calculate portal target
            Location portalLoc = PortalCache.findNearestToDimension(
                    tracker.getWorld(), tracker.getLocation(), target.getWorld().getEnvironment());
            trackerCache.setCachedPortalTarget(portalLoc);
            if (portalLoc != null) {
                LocatorBarManager.setTarget(tracker, portalLoc);
            }
        }

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
     * Cancel all tracking of a specific target player.
     */
    public void cancelTracking(Player target) {
        for (UUID trackerUUID : TrackingRegistry.getActiveTrackers()) {
            Player tracker = Bukkit.getPlayer(trackerUUID);
            if (tracker == null || !tracker.isOnline())
                continue;

            PlayerCache cache = PlayerCache.from(tracker);
            if ("Player".equals(cache.getTrackingLocation())
                    && target.getUniqueId().equals(cache.getTargetByUUID())) {

                cache.setTrackingLocation(null);
                cache.setTargetByUUID(null);
                cache.setCachedPortalTarget(null);
                TrackingRegistry.stopTracking(trackerUUID);

                LocatorBarManager.disableReceive(tracker);
                LocatorBarManager.clearTarget(tracker);

                ColorUtil.sendMessage(tracker, "&c" + target.getName() + " &chas cancelled tracking.");
            }
        }
        ColorUtil.sendMessage(target, "&aYou have cancelled all tracking requests.");
    }

    /**
     * Internal tracking request data.
     */
    private record TrackingRequest(UUID trackerUUID, UUID targetUUID) {
    }
}
