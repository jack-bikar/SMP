package games.coob.smp.tracking;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Global cache of known portal locations per world.
 * Portals are registered when players use them or when they're created.
 * Finding the nearest portal is O(n) where n = portals in that world.
 */
public final class PortalCache {

    private static final Map<UUID, List<Location>> NETHER_PORTALS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Location>> END_PORTALS = new ConcurrentHashMap<>();

    private static final int SEARCH_RADIUS = 64;
    private static final int SEARCH_STEP = 8;

    private PortalCache() {
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Register a portal location (called when a player enters a portal or one is
     * created).
     */
    public static void register(Location location, Material type) {
        if (location == null || location.getWorld() == null || type == null)
            return;

        UUID worldId = location.getWorld().getUID();
        Location centered = location.clone();
        centered.setX(Math.floor(centered.getX()) + 0.5);
        centered.setY(Math.floor(centered.getY()) + 0.5);
        centered.setZ(Math.floor(centered.getZ()) + 0.5);

        if (type == Material.NETHER_PORTAL) {
            addIfNotDuplicate(NETHER_PORTALS.computeIfAbsent(worldId, k -> new CopyOnWriteArrayList<>()), centered);
        } else if (type == Material.END_PORTAL || type == Material.END_PORTAL_FRAME) {
            addIfNotDuplicate(END_PORTALS.computeIfAbsent(worldId, k -> new CopyOnWriteArrayList<>()), centered);
        }
    }

    private static void addIfNotDuplicate(List<Location> list, Location loc) {
        for (Location existing : list) {
            if (existing.distanceSquared(loc) < 9.0) { // Within 3 blocks = same portal
                return;
            }
        }
        list.add(loc);
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /**
     * Find the nearest registered portal of the given type in the specified world.
     * Returns null if no portals are registered.
     */
    public static Location findNearest(World world, Location from, Material type) {
        if (world == null || from == null || type == null)
            return null;

        List<Location> portals = getPortalList(world.getUID(), type);
        if (portals == null || portals.isEmpty()) {
            // Fallback: do a small area scan and cache the result
            return scanAndCache(world, from, type);
        }

        Location best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Location portal : portals) {
            if (portal == null || portal.getWorld() != world)
                continue;
            double distSq = from.distanceSquared(portal);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = portal;
            }
        }

        return best;
    }

    /**
     * Find nearest portal that leads to the target dimension.
     */
    public static Location findNearestToDimension(World currentWorld, Location from,
            World.Environment targetDimension) {
        if (currentWorld == null || from == null || targetDimension == null)
            return null;

        World.Environment currentEnv = currentWorld.getEnvironment();

        // Determine which portal type to look for
        if (currentEnv == World.Environment.NORMAL) {
            if (targetDimension == World.Environment.NETHER) {
                return findNearest(currentWorld, from, Material.NETHER_PORTAL);
            } else if (targetDimension == World.Environment.THE_END) {
                Location endPortal = findNearest(currentWorld, from, Material.END_PORTAL);
                return endPortal != null ? endPortal : findNearest(currentWorld, from, Material.END_PORTAL_FRAME);
            }
        } else if (currentEnv == World.Environment.NETHER) {
            if (targetDimension == World.Environment.NORMAL) {
                return findNearest(currentWorld, from, Material.NETHER_PORTAL);
            }
        } else if (currentEnv == World.Environment.THE_END) {
            if (targetDimension == World.Environment.NORMAL) {
                // End exit portal - usually at 0,0 area
                Location endPortal = findNearest(currentWorld, from, Material.END_PORTAL);
                if (endPortal != null)
                    return endPortal;
                // Fallback to world spawn area
                return new Location(currentWorld, 0.5, 64, 0.5);
            }
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static List<Location> getPortalList(UUID worldId, Material type) {
        if (type == Material.NETHER_PORTAL) {
            return NETHER_PORTALS.get(worldId);
        } else if (type == Material.END_PORTAL || type == Material.END_PORTAL_FRAME) {
            return END_PORTALS.get(worldId);
        }
        return null;
    }

    /**
     * Scan a small area for a portal and cache it if found.
     * This is only called when we have no cached portals.
     */
    private static Location scanAndCache(World world, Location from, Material type) {
        int cx = from.getBlockX();
        int cy = from.getBlockY();
        int cz = from.getBlockZ();
        int minY = Math.max(world.getMinHeight(), cy - 32);
        int maxY = Math.min(world.getMaxHeight() - 1, cy + 32);

        Location best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx += SEARCH_STEP) {
            for (int y = minY; y <= maxY; y += SEARCH_STEP) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz += SEARCH_STEP) {
                    Block block = world.getBlockAt(cx + dx, y, cz + dz);
                    if (block.getType() == type) {
                        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
                        double distSq = from.distanceSquared(loc);
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            best = loc;
                        }
                    }
                }
            }
        }

        // Cache the found portal
        if (best != null) {
            register(best, type);
        }

        return best;
    }

    /**
     * Clear all cached portals (e.g., on plugin disable).
     */
    public static void clear() {
        NETHER_PORTALS.clear();
        END_PORTALS.clear();
    }
}
