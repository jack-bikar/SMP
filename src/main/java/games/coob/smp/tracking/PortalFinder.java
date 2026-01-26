package games.coob.smp.tracking;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Finds the nearest portal in a dimension that leads to the target dimension.
 */
public final class PortalFinder {

	private static final int SEARCH_RADIUS = 128;
	private static final int STEP = 8; // Check every 8 blocks to reduce cost

	private PortalFinder() {
	}

	/**
	 * Find the nearest nether portal in the given world to the given location.
	 * Used when tracker is in overworld and target is in nether.
	 */
	public static Location findNearestNetherPortal(World world, Location from) {
		if (world.getEnvironment() != World.Environment.NORMAL) {
			return null;
		}
		return findNearestPortal(world, from, Material.NETHER_PORTAL);
	}

	/**
	 * Find the nearest end portal (portal blocks in stronghold) in overworld.
	 * Used when tracker is in overworld and target is in end.
	 */
	public static Location findNearestEndPortal(World world, Location from) {
		if (world.getEnvironment() != World.Environment.NORMAL) {
			return null;
		}
		Location portal = findNearestPortal(world, from, Material.END_PORTAL);
		if (portal != null) return portal;
		return findNearestPortal(world, from, Material.END_PORTAL_FRAME);
	}

	/**
	 * Find the nearest portal in the given world that leads to the target dimension.
	 * E.g. world=overworld, target=nether -> nearest overworld nether portal.
	 */
	public static Location findNearestPortalToDimension(World world, Location from, World.Environment targetDimension) {
		if (world == null || from == null) return null;
		if (world.getEnvironment() == World.Environment.NORMAL) {
			if (targetDimension == World.Environment.NETHER) return findNearestNetherPortal(world, from);
			if (targetDimension == World.Environment.THE_END) return findNearestEndPortal(world, from);
		} else if (world.getEnvironment() == World.Environment.NETHER || world.getEnvironment() == World.Environment.THE_END) {
			if (targetDimension == World.Environment.NORMAL) {
				return findNearestPortalInDimension(world, from, world.getEnvironment());
			}
		}
		return null;
	}

	/**
	 * Find the nearest portal in nether/end that leads to overworld.
	 */
	public static Location findNearestPortalInDimension(World world, Location from, World.Environment dimension) {
		if (dimension == World.Environment.NETHER) {
			return findNearestPortal(world, from, Material.NETHER_PORTAL);
		}
		if (dimension == World.Environment.THE_END) {
			Location ep = findNearestPortal(world, from, Material.END_PORTAL);
			if (ep != null) return ep;
			return findNearestPortal(world, from, Material.END_PORTAL_FRAME);
		}
		return null;
	}

	private static Location findNearestPortal(World world, Location from, Material portalType) {
		if (world == null || from == null) return null;
		int cx = from.getBlockX();
		int cy = from.getBlockY();
		int cz = from.getBlockZ();
		double bestDistSq = Double.MAX_VALUE;
		Location best = null;

		for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx += STEP) {
			for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy += STEP) {
				for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz += STEP) {
					int x = cx + dx;
					int y = cy + dy;
					int z = cz + dz;
					Block b = world.getBlockAt(x, y, z);
					if (b.getType() != portalType) continue;
					Location blockCenter = new Location(world, x + 0.5, y + 0.5, z + 0.5);
					double distSq = from.distanceSquared(blockCenter);
					if (distSq < bestDistSq) {
						bestDistSq = distSq;
						best = blockCenter;
					}
				}
			}
		}
		return best;
	}
}
