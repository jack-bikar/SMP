package games.coob.smp.listener;

import games.coob.smp.PlayerCache;
import games.coob.smp.SMPPlugin;
import games.coob.smp.settings.Settings;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.tracking.PortalCache;
import games.coob.smp.tracking.TrackingRegistry;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.world.PortalCreateEvent;

/**
 * Handles portal tracking and dimension change events for the locator bar.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LocatorListener implements Listener {

    private static final LocatorListener instance = new LocatorListener();

    public static LocatorListener getInstance() {
        return instance;
    }

    /**
     * Called AFTER a player has changed worlds.
     * This is the right time to recalculate portal targets.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!Settings.LocatorSection.ENABLE_TRACKING)
            return;

        Player player = event.getPlayer();

        // Schedule 1 tick later to ensure world change is complete
        Bukkit.getScheduler().runTaskLater(SMPPlugin.getInstance(), () -> {
            handleWorldChange(player);
        }, 1L);
    }

    private void handleWorldChange(Player player) {
        if (!player.isOnline())
            return;

        PlayerCache cache = PlayerCache.from(player);

        // If this player is tracking something, recalculate portal target
        if (TrackingRegistry.isTracking(player.getUniqueId())) {
            recalculatePortalTarget(player, cache);
        }

        // Also check if this player is being tracked by someone else
        for (Player tracker : TrackingRegistry.getOnlineTrackers()) {
            PlayerCache trackerCache = PlayerCache.from(tracker);
            if (player.getUniqueId().equals(trackerCache.getTargetByUUID())) {
                recalculatePortalTarget(tracker, trackerCache);
            }
        }
    }

    /**
     * Recalculate the portal target for a tracker based on current tracking state.
     */
    private void recalculatePortalTarget(Player tracker, PlayerCache cache) {
        String trackingType = cache.getTrackingLocation();
        if (trackingType == null)
            return;

        World.Environment targetDimension = null;

        if ("Player".equals(trackingType)) {
            Player target = cache.getTargetByUUID() != null ? Bukkit.getPlayer(cache.getTargetByUUID()) : null;
            if (target != null && target.isOnline() && !target.getWorld().equals(tracker.getWorld())) {
                targetDimension = target.getWorld().getEnvironment();
            }
        } else if ("Death".equals(trackingType)) {
            Location deathLoc = cache.getDeathLocation();
            if (deathLoc != null && deathLoc.getWorld() != null && !deathLoc.getWorld().equals(tracker.getWorld())) {
                targetDimension = deathLoc.getWorld().getEnvironment();
            }
        }

        if (targetDimension != null) {
            // Find and cache the portal to the target dimension
            Location portalLoc = findBestPortal(tracker, cache, targetDimension);
            cache.setCachedPortalTarget(portalLoc);

            // Update the locator bar immediately
            if (portalLoc != null) {
                LocatorBarManager.enableReceive(tracker);
                LocatorBarManager.setTarget(tracker, portalLoc);
            }
        } else {
            // Same dimension or no target - clear cached portal
            cache.setCachedPortalTarget(null);
        }
    }

    /**
     * Find the best portal to reach the target dimension.
     * Checks player's stored portals first, then falls back to PortalCache.
     */
    private Location findBestPortal(Player tracker, PlayerCache trackerCache, World.Environment targetDimension) {
        World trackerWorld = tracker.getWorld();
        Location trackerLoc = tracker.getLocation();
        World.Environment currentEnv = trackerWorld.getEnvironment();

        // First, check player's stored portal locations
        Location storedPortal = getStoredPortal(trackerCache, currentEnv, targetDimension);
        if (storedPortal != null && storedPortal.getWorld() != null
                && storedPortal.getWorld().equals(trackerWorld)) {
            return storedPortal;
        }

        // If tracking a player, also check their stored portals
        if ("Player".equals(trackerCache.getTrackingLocation()) && trackerCache.getTargetByUUID() != null) {
            Player target = Bukkit.getPlayer(trackerCache.getTargetByUUID());
            if (target != null && target.isOnline()) {
                PlayerCache targetCache = PlayerCache.from(target);
                Location targetStoredPortal = getStoredPortal(targetCache, currentEnv, targetDimension);
                if (targetStoredPortal != null && targetStoredPortal.getWorld() != null
                        && targetStoredPortal.getWorld().equals(trackerWorld)) {
                    // Return the nearest of the two stored portals
                    if (storedPortal == null) {
                        return targetStoredPortal;
                    }
                    double distTracker = trackerLoc.distanceSquared(storedPortal);
                    double distTarget = trackerLoc.distanceSquared(targetStoredPortal);
                    return distTracker <= distTarget ? storedPortal : targetStoredPortal;
                }
            }
        }

        // Fallback to PortalCache (global portal registry)
        return PortalCache.findNearestToDimension(trackerWorld, trackerLoc, targetDimension);
    }

    /**
     * Get the stored portal location based on current and target dimensions.
     */
    private Location getStoredPortal(PlayerCache cache, World.Environment currentEnv, World.Environment targetEnv) {
        if (currentEnv == World.Environment.NORMAL) {
            if (targetEnv == World.Environment.NETHER) {
                return cache.getOverworldNetherPortalLocation();
            } else if (targetEnv == World.Environment.THE_END) {
                return cache.getOverworldEndPortalLocation();
            }
        } else if (currentEnv == World.Environment.NETHER || currentEnv == World.Environment.THE_END) {
            if (targetEnv == World.Environment.NORMAL) {
                return cache.getPortalLocation();
            }
        }
        return null;
    }

    /**
     * Store portal location when a player enters a portal.
     */
    @EventHandler
    public void onEntityPortalEnter(EntityPortalEnterEvent event) {
        if (!Settings.LocatorSection.ENABLE_TRACKING)
            return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Player player))
            return;

        Location location = event.getLocation();
        Block block = location.getBlock();
        Material type = block.getType();
        PlayerCache cache = PlayerCache.from(player);
        World.Environment env = player.getWorld().getEnvironment();

        // Store portal location based on current dimension
        if (env == World.Environment.NORMAL) {
            if (type == Material.NETHER_PORTAL) {
                cache.setOverworldNetherPortalLocation(centerLocation(location));
                PortalCache.register(location, Material.NETHER_PORTAL);
            } else if (type == Material.END_PORTAL || type == Material.END_PORTAL_FRAME) {
                cache.setOverworldEndPortalLocation(centerLocation(location));
                PortalCache.register(location, Material.END_PORTAL);
            }
        } else if (env == World.Environment.NETHER || env == World.Environment.THE_END) {
            cache.setPortalLocation(centerLocation(location));
            if (type == Material.NETHER_PORTAL) {
                PortalCache.register(location, Material.NETHER_PORTAL);
            } else if (type == Material.END_PORTAL || type == Material.END_PORTAL_FRAME) {
                PortalCache.register(location, Material.END_PORTAL);
            }
        }
    }

    /**
     * Register newly created portals.
     */
    @EventHandler
    public void onPortalCreate(PortalCreateEvent event) {
        if (!Settings.LocatorSection.ENABLE_TRACKING)
            return;

        // Calculate center of the portal
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
