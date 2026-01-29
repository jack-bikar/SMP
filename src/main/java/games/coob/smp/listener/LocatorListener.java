package games.coob.smp.listener;

import games.coob.smp.PlayerCache;
import games.coob.smp.settings.Settings;
import games.coob.smp.tracking.PortalCache;
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

/**
 * Handles portal tracking events for the locator bar.
 * Portal locations are stored when players use portals (PlayerPortalEvent fires
 * once before teleport).
 * Cached portal targets are invalidated on dimension change so LocatorTask
 * recalculates.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LocatorListener implements Listener {

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

        // Invalidate this player's cached portal (they're now in a different world)
        if (TrackingRegistry.isTracking(player.getUniqueId())) {
            cache.setCachedPortalTarget(null);
        }

        // Invalidate cached portals for anyone tracking this player
        for (java.util.UUID trackerUUID : TrackingRegistry.getActiveTrackers()) {
            Player tracker = org.bukkit.Bukkit.getPlayer(trackerUUID);
            if (tracker == null || !tracker.isOnline())
                continue;

            PlayerCache trackerCache = PlayerCache.from(tracker);
            if (player.getUniqueId().equals(trackerCache.getTargetByUUID())) {
                trackerCache.setCachedPortalTarget(null);
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
        if (toLocation == null || toLocation.getWorld() == null)
            return;

        World.Environment targetEnv = toLocation.getWorld().getEnvironment();
        Location centered = centerLocation(fromLocation);

        // Store in player's cache based on current dimension and destination
        if (currentEnv == World.Environment.NORMAL) {
            if (targetEnv == World.Environment.NETHER) {
                cache.setOverworldNetherPortalLocation(centered);
                PortalCache.register(centered, Material.NETHER_PORTAL);
            } else if (targetEnv == World.Environment.THE_END) {
                cache.setOverworldEndPortalLocation(centered);
                PortalCache.register(centered, Material.END_PORTAL);
            }
        } else if (currentEnv == World.Environment.NETHER) {
            if (targetEnv == World.Environment.NORMAL) {
                cache.setPortalLocation(centered);
                PortalCache.register(centered, Material.NETHER_PORTAL);
            }
        } else if (currentEnv == World.Environment.THE_END) {
            if (targetEnv == World.Environment.NORMAL) {
                cache.setPortalLocation(centered);
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
}
