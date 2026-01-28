package games.coob.smp.tracking;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory registry of known portal locations.
 *
 * Goal: avoid expensive block scans every tick. We register portals when:
 * - they are created (PortalCreateEvent)
 * - we discover one via a fallback scan (PortalFinder)
 */
public final class PortalRegistry {

	private static final Map<UUID, List<Location>> NETHER_PORTALS_BY_WORLD = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Location>> END_PORTALS_BY_WORLD = new ConcurrentHashMap<>();

	private PortalRegistry() {
	}

	public static void registerPortal(Location loc, Material portalType) {
		if (loc == null || loc.getWorld() == null || portalType == null)
			return;
		UUID worldId = loc.getWorld().getUID();
		if (portalType == Material.NETHER_PORTAL) {
			NETHER_PORTALS_BY_WORLD.computeIfAbsent(worldId, _k -> new ArrayList<>()).add(loc);
		} else if (portalType == Material.END_PORTAL || portalType == Material.END_PORTAL_FRAME) {
			END_PORTALS_BY_WORLD.computeIfAbsent(worldId, _k -> new ArrayList<>()).add(loc);
		}
	}

	public static Location findNearestRegisteredPortal(World world, Location from, Material portalType) {
		if (world == null || from == null || portalType == null)
			return null;
		List<Location> list;
		if (portalType == Material.NETHER_PORTAL) {
			list = NETHER_PORTALS_BY_WORLD.get(world.getUID());
		} else if (portalType == Material.END_PORTAL || portalType == Material.END_PORTAL_FRAME) {
			list = END_PORTALS_BY_WORLD.get(world.getUID());
		} else {
			return null;
		}

		if (list == null || list.isEmpty())
			return null;

		Location best = null;
		double bestDistSq = Double.MAX_VALUE;
		for (Location candidate : list) {
			if (candidate == null || candidate.getWorld() != world)
				continue;
			double d = from.distanceSquared(candidate);
			if (d < bestDistSq) {
				bestDistSq = d;
				best = candidate;
			}
		}
		return best;
	}
}
