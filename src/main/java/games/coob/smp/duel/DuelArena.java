package games.coob.smp.duel;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a duel takes place: the border centre and a landing spot for each side.
 *
 * @param arenaName name of the admin-built arena, or null for a natural spot
 */
public record DuelArena(Location center, Location spawn1, Location spawn2, String arenaName) {

	/** Extra border room per teammate. */
	private static final int TEAMMATE_SPACING = 2;
	/** Teammate positions along the line, nearest first in both directions. */
	private static final int[] OFFSETS = { 3, -3, 6, -6, 2, -2, 4, -4, 5, -5, 7, -7, 8, -8, 1, -1 };
	/** How far behind its team a side's mobs line up. */
	private static final int MOB_DISTANCE_BEHIND = 3;

	public DuelArena {
		center = center.clone();
		spawn1 = facing(spawn1, spawn2);
		spawn2 = facing(spawn2, spawn1);
	}

	public boolean created() {
		return arenaName != null;
	}

	public Location spawn(DuelSide side) {
		return side == DuelSide.RED ? spawn1 : spawn2;
	}

	/**
	 * Landing spots for a team: in a line next to the team's spawn, facing the
	 * other team, two blocks apart. Blocked positions (trees, steps) are skipped;
	 * if nothing nearby is safe the player lands on the spawn itself.
	 */
	public List<Location> spots(DuelSide side, int count) {
		Location base = spawn(side);
		List<Location> spots = line(base, across(side), count);
		while (spots.size() < count)
			spots.add(base.clone());
		return spots;
	}

	/**
	 * Spots for a side's mobs: a line a few blocks behind the team, facing the
	 * other team. When there aren't enough safe spots, mobs share them.
	 */
	public List<Location> mobSpots(DuelSide side, int count) {
		Location base = spawn(side);
		Vector back = base.toVector().subtract(spawn(side.other()).toVector()).setY(0);
		if (back.lengthSquared() > 1.0E-6) {
			Location behind = safeSpot(base.clone().add(back.normalize().multiply(MOB_DISTANCE_BEHIND)));
			if (behind != null)
				base = behind;
		}

		List<Location> line = line(base, across(side), count);
		List<Location> spots = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
			spots.add(line.get(i % line.size()).clone());
		return spots;
	}

	/** Horizontal direction along a side's line, at right angles to the other team. */
	private Vector across(DuelSide side) {
		Location base = spawn(side);
		Location other = spawn(side.other());
		Vector across = new Vector(-(other.getZ() - base.getZ()), 0, other.getX() - base.getX());
		if (across.lengthSquared() < 1.0E-6)
			across = new Vector(1, 0, 0);
		return across.normalize();
	}

	/** Up to {@code count} safe spots on a line through {@code base}, starting with base itself. */
	private static List<Location> line(Location base, Vector across, int count) {
		List<Location> spots = new ArrayList<>(count);
		spots.add(base.clone());
		Set<Long> used = new HashSet<>();
		used.add(blockKey(base));

		for (int offset : OFFSETS) {
			if (spots.size() >= count)
				break;
			Location spot = safeSpot(base.clone().add(across.clone().multiply(offset)));
			if (spot != null && used.add(blockKey(spot)))
				spots.add(spot);
		}
		return spots;
	}

	private static long blockKey(Location location) {
		return ((long) location.getBlockX() & 0x3FFFFFF) << 38 | ((long) location.getBlockZ() & 0x3FFFFFF) << 12
				| (location.getBlockY() & 0xFFF);
	}

	/** A standable spot at (or within two blocks of) the given height, or null. */
	private static Location safeSpot(Location location) {
		World world = location.getWorld();
		int x = location.getBlockX();
		int z = location.getBlockZ();

		for (int dy : new int[] { 0, 1, -1, 2, -2 }) {
			Block feet = world.getBlockAt(x, location.getBlockY() + dy, z);
			Block head = feet.getRelative(0, 1, 0);
			Block ground = feet.getRelative(0, -1, 0);
			if (ground.getType().isSolid() && !ground.isLiquid() && feet.isPassable() && !feet.isLiquid()
					&& head.isPassable() && !head.isLiquid())
				return new Location(world, x + 0.5, feet.getY(), z + 0.5, location.getYaw(), location.getPitch());
		}
		return null;
	}

	/**
	 * Border radius that keeps every spawn inside with some room; bigger teams get
	 * a bigger arena.
	 */
	public int borderRadius(int configuredRadius, int teamSize) {
		double furthest = Math.max(
				Math.max(Math.abs(spawn1.getX() - center.getX()), Math.abs(spawn1.getZ() - center.getZ())),
				Math.max(Math.abs(spawn2.getX() - center.getX()), Math.abs(spawn2.getZ() - center.getZ())));
		int wanted = configuredRadius + 4 * (teamSize - 1);
		return Math.max(wanted, (int) Math.ceil(furthest) + 3 + TEAMMATE_SPACING * teamSize);
	}

	/** Copy of {@code from} turned to look at {@code to}. */
	private static Location facing(Location from, Location to) {
		Location result = from.clone();
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		result.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
		result.setPitch(0);
		return result;
	}
}
