package games.coob.smp.listener;

import games.coob.smp.PlayerCache;
import games.coob.smp.SMPPlugin;
import games.coob.smp.settings.Settings;
import games.coob.smp.tracking.PortalCache;
import games.coob.smp.tracking.TrackedTarget;
import games.coob.smp.tracking.TrackingRegistry;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;

import java.util.logging.Level;

/**
 * Handles portal tracking events for the locator bar.
 * Portal locations are stored when players use portals (PlayerPortalEvent fires
 * once before teleport).
 * Cached portal targets are invalidated on dimension change so LocatorTask
 * recalculates.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LocatorListener implements Listener {

    private static final boolean DEBUG = true;
    private static final LocatorListener instance = new LocatorListener();

    public static LocatorListener getInstance() {
        return instance;
    }

    /**
     * Called AFTER a player has changed worlds.
     * Invalidate cached portal targets so LocatorTask recalculates them.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!Settings.LocatorSection.ENABLE_TRACKING)
            return;

        Player player = event.getPlayer();
        PlayerCache cache = PlayerCache.from(player);

        debug("onPlayerChangedWorld: " + player.getName() +
                " from " + event.getFrom().getName() +
                " to " + player.getWorld().getName());

        // Invalidate this player's cached portal targets (they're now in a different world)
        if (TrackingRegistry.isTracking(player.getUniqueId())) {
            for (TrackedTarget target : cache.getTrackedTargets()) {
                target.setCachedPortalTarget(null);
            }
            debug("  Invalidated cached portals for tracker: " + player.getName());
        }

        // Invalidate cached portals for anyone tracking this player
        for (java.util.UUID trackerUUID : TrackingRegistry.getActiveTrackers()) {
            Player tracker = org.bukkit.Bukkit.getPlayer(trackerUUID);
            if (tracker == null || !tracker.isOnline())
                continue;

            PlayerCache trackerCache = PlayerCache.from(tracker);
            TrackedTarget target = trackerCache.getTrackedTarget(player.getUniqueId());
            if (target != null) {
                target.setCachedPortalTarget(null);
                debug("  Invalidated cached portal for tracker " + tracker.getName() +
                        " (was tracking " + player.getName() + ")");
            }
        }
    }

    /**
     * Store portal location BEFORE a player teleports through a portal.
     * PlayerPortalEvent fires ONCE per teleport (unlike EntityPortalEnterEvent
     * which fires repeatedly).
     * The 'from' location is where the player is - the portal block itself.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!Settings.LocatorSection.ENABLE_TRACKING)
            return;

        Player player = event.getPlayer();
        Location fromLocation = event.getFrom();
        PlayerCache cache = PlayerCache.from(player);
        World.Environment currentEnv = player.getWorld().getEnvironment();

        // Determine portal type based on destination
        Location toLocation = event.getTo();
        if (toLocation == null || toLocation.getWorld() == null) {
            debug("onPlayerPortal: " + player.getName() + " - toLocation is null, skipping");
            return;
        }

        World.Environment targetEnv = toLocation.getWorld().getEnvironment();
        Location centered = centerLocation(fromLocation);

        debug("onPlayerPortal: " + player.getName() +
                " from " + currentEnv + " to " + targetEnv +
                " at " + formatLocation(centered));

        // Store in player's cache based on current dimension and destination
        if (currentEnv == World.Environment.NORMAL) {
            if (targetEnv == World.Environment.NETHER) {
                cache.setOverworldNetherPortalLocation(centered);
                PortalCache.register(centered, Material.NETHER_PORTAL);
                debug("  Stored overworld->nether portal");
            } else if (targetEnv == World.Environment.THE_END) {
                cache.setOverworldEndPortalLocation(centered);
                PortalCache.register(centered, Material.END_PORTAL);
                debug("  Stored overworld->end portal");
            }
        } else if (currentEnv == World.Environment.NETHER) {
            if (targetEnv == World.Environment.NORMAL) {
                cache.setPortalLocation(centered);
                PortalCache.register(centered, Material.NETHER_PORTAL);
                debug("  Stored nether->overworld portal");
            }
        } else if (currentEnv == World.Environment.THE_END) {
            if (targetEnv == World.Environment.NORMAL) {
                cache.setPortalLocation(centered);
                debug("  Stored end->overworld portal");
                // End portals to overworld don't need registration (end gateway or end
                // platform)
            }
        }
    }

    /**
     * Register newly created portals in the global cache.
     */
    @EventHandler
    public void onPortalCreate(PortalCreateEvent event) {
        if (!Settings.LocatorSection.ENABLE_TRACKING)
            return;

        double x = 0, y = 0, z = 0;
        int count = 0;

        for (org.bukkit.block.BlockState state : event.getBlocks()) {
            if (state.getType() == Material.NETHER_PORTAL) {
                Location loc = state.getLocation();
                x += loc.getX();
                y += loc.getY();
                z += loc.getZ();
                count++;
            }
        }

        if (count > 0) {
            Location center = new Location(event.getWorld(), x / count + 0.5, y / count + 0.5, z / count + 0.5);
            PortalCache.register(center, Material.NETHER_PORTAL);
        }
    }

    private Location centerLocation(Location loc) {
        return new Location(
                loc.getWorld(),
                Math.floor(loc.getX()) + 0.5,
                Math.floor(loc.getY()) + 0.5,
                Math.floor(loc.getZ()) + 0.5);
    }

    private String formatLocation(Location loc) {
        if (loc == null)
            return "null";
        return String.format("%d, %d, %d in %s",
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                loc.getWorld() != null ? loc.getWorld().getName() : "null");
    }

    private void debug(String message) {
        if (DEBUG) {
            SMPPlugin.getInstance().getLogger().log(Level.INFO, "[LocatorListener Debug] " + message);
        }
    }
}
