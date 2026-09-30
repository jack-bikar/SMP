package games.coob.smp.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Pose;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Works out where a dead player's body goes so it looks like it came to rest
 * there, by trying every way it could fit near the death spot and keeping the
 * most natural one:
 * <ul>
 * <li>Lying on its back or face down, in any direction, flat on the ground:
 * never through walls, never half hanging over a step or a drop. On a hillside
 * it lies along the slope.</li>
 * <li>Sitting on the stairs it died on, facing down them.</li>
 * <li>Slumped against a wall or in a corner, in places too cramped to lie down
 * (tunnels, holes, next to walls).</li>
 * <li>Sitting on the edge of a ledge or pillar that is too small to lie on.</li>
 * <li>Floating face down in water; lying on the bottom of flooded caves.</li>
 * </ul>
 * Bodies are placed on the real surface height (slabs, snow, paths, stairs),
 * never on top of each other, and stay within about a block of the death spot.
 */
public final class BodyPlacer {

	/** How far up we look for room when the player died inside a block. */
	private static final int MAX_ESCAPE_UP = 8;
	/** How far the body may move from the death spot to find room. */
	private static final double MAX_SHIFT = 1.25;
	private static final double SHIFT_STEP = 0.25;
	private static final int DIRECTIONS = 16;
	/** Most a lying body may sag across uneven ground before part of it would float. */
	private static final double MAX_SAG = 0.3;
	/** Highest step up from the death spot's ground the body may end up on. */
	private static final double MAX_STEP_UP = 0.55;
	/** Lowest step down (for bodies that were standing on something small, like a fence). */
	private static final double MAX_STEP_DOWN = 1.2;
	/** How deep the water above a flooded body may be before it lies on the bottom instead of floating. */
	private static final int MAX_FLOAT_DEPTH = 6;

	// Base scores: the best-scoring placement that fits wins
	private static final double SCORE_STAIRS = 80;
	private static final double SCORE_LIE = 60;
	private static final double SCORE_WALL = 52;
	private static final double SCORE_LEDGE = 44;
	private static final double SCORE_FLOAT = 50;
	/** Score lost per block moved from the death spot, and per block of height difference. */
	private static final double COST_SHIFT = 14;
	private static final double COST_HEIGHT = 8;
	private static final double COST_SAG = 25;
	/** Random bonus, so equally good spots don't always look the same. */
	private static final double VARIETY = 4;

	private static final BlockFace[] SIDES = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
	private static final Pose[] LYING = { Pose.SLEEPING, Pose.SWIMMING };

	private final World world;
	private final Location death;
	private final Predicate<BodyPlacement> taken;
	private final ThreadLocalRandom random = ThreadLocalRandom.current();
	private final Map<Long, Surface> surfaces = new HashMap<>();
	/** Blocks looked at so far: thousands of placements are tried, but only a few dozen blocks. */
	private final Map<Long, Cell> cells = new HashMap<>();

	private BodyPlacement best;
	private double bestScore = Double.NEGATIVE_INFINITY;

	/** A block's collision boxes (relative to the block) and liquid height (NaN if none). */
	private record Cell(BoundingBox[] boxes, double liquid) {
	}

	/** The top of what a body can rest on at one point, and the room above it. */
	private record Surface(double y, boolean liquid, double ceiling) {
		double room() {
			return ceiling - y;
		}
	}

	private BodyPlacer(Location death, Predicate<BodyPlacement> taken) {
		this.world = death.getWorld();
		this.death = death;
		this.taken = taken;
	}

	/**
	 * Where a body should go for a player who died at {@code death}.
	 *
	 * @param taken whether a placement would lie on (or its spot is used by) another body
	 * @return null when the player fell into the void
	 */
	public static BodyPlacement find(Location death, Predicate<BodyPlacement> taken) {
		if (death.getWorld() == null || death.getY() < death.getWorld().getMinHeight())
			return null;
		return new BodyPlacer(death, taken).place();
	}

	private BodyPlacement place() {
		double x = death.getX();
		double z = death.getZ();
		double feet = escapeUp(x, death.getY(), z);

		// In water: float at the top if there is air above, otherwise lie on the bottom
		Block feetBlock = world.getBlockAt(floor(x), floor(feet), floor(z));
		if (feetBlock.getType() == Material.WATER) {
			Double top = waterSurfaceAbove(feetBlock);
			if (top != null) {
				floating(x, top, z, Pose.SWIMMING);
				if (best != null)
					return best;
			}
		}

		Surface ground = surface(x, z, feet + 0.05, true);
		if (ground == null)
			return fallback(x, feet, z); // Over the void (e.g. in the End): stay where they died
		if (ground.liquid()) {
			// Fell onto water or lava: float on it (face down in water, on the back on lava)
			boolean water = world.getBlockAt(floor(x), floor(ground.y() - 0.01), floor(z)).getType() == Material.WATER;
			floating(x, ground.y(), z, water ? Pose.SWIMMING : Pose.SLEEPING);
			return best != null ? best : fallback(x, ground.y(), z);
		}

		onLand(ground.y(), 0);
		if (best == null) {
			// Standing on something too small for a body (a fence, a wall top): try the ground below
			Surface below = surface(x, z, ground.y() - 0.6, false);
			if (below != null && ground.y() - below.y() <= MAX_STEP_DOWN + 1.5)
				onLand(below.y(), COST_HEIGHT * (ground.y() - below.y()));
		}
		return best != null ? best : fallback(x, ground.y(), z);
	}

	/**
	 * Tries every kind of placement on land around the death spot, with ground at
	 * {@code ref}. The few sitting spots go first: a good one (stairs) lets most
	 * lying positions be skipped without checking them.
	 */
	private void onLand(double ref, double penalty) {
		stairs(ref, penalty);
		walls(ref, penalty);
		ledges(ref, penalty);
		lying(ref, penalty);
	}

	// -------------------------------------------------------------------------
	// Lying down
	// -------------------------------------------------------------------------

	private void lying(double ref, double penalty) {
		Vector look = BodyShape.forward(death.getYaw());
		List<Vector> heads = directions();

		for (double[] offset : shifts()) {
			double shift = offset[2];
			// Everything from here on is further away: stop once it can't beat the best spot
			if (SCORE_LIE + VARIETY + 3 - COST_SHIFT * shift - penalty <= bestScore)
				return;
			double cx = death.getX() + offset[0];
			double cz = death.getZ() + offset[1];

			for (Vector head : heads) {
				for (Pose pose : LYING) {
					// Another body is already there (cheap to check, unlike the ground)
					if (takenAt(pose, cx, ref, cz, head))
						continue;
					double[] fit = lyingFit(pose, cx, cz, head, ref);
					if (fit == null)
						continue;
					double rest = fit[0];
					double score = SCORE_LIE - COST_SHIFT * shift - COST_SAG * fit[1] - COST_HEIGHT * Math.abs(rest - ref)
							- penalty + random.nextDouble() * VARIETY;
					// Fell forward onto the face, or backwards onto the back
					double along = head.dot(look);
					if (pose == Pose.SWIMMING ? along > 0.7 : along < -0.7)
						score += 3;

					float yaw = BodyShape.yawForHead(pose, head.getX(), head.getZ());
					Vector origin = BodyShape.lyingOrigin(pose, cx, rest, cz, head);
					consider(new BodyPlacement(origin.getX(), origin.getY(), origin.getZ(), yaw, 0, pose), score);
				}
			}
		}
	}

	private boolean takenAt(Pose pose, double cx, double ground, double cz, Vector head) {
		Vector origin = BodyShape.lyingOrigin(pose, cx, ground, cz, head);
		return taken.test(new BodyPlacement(origin.getX(), origin.getY(), origin.getZ(),
				BodyShape.yawForHead(pose, head.getX(), head.getZ()), 0, pose));
	}

	/**
	 * Directions to try: the eight along the block grid (so bodies line up with
	 * corridors and walls) and eight at random angles in between.
	 */
	private List<Vector> directions() {
		List<Vector> heads = new ArrayList<>(DIRECTIONS);
		double offset = 5 + random.nextDouble() * 35;
		for (int i = 0; i < 8; i++) {
			for (double angle : new double[] { i * 45.0, i * 45.0 + offset }) {
				double radians = Math.toRadians(angle);
				heads.add(new Vector(-Math.sin(radians), 0, Math.cos(radians)));
			}
		}
		// Shuffled, so ties between equally good directions don't always go the same way
		java.util.Collections.shuffle(heads, random);
		return heads;
	}

	/** Offsets {dx, dz, distance} around the death spot, nearest first. */
	private static List<double[]> shifts() {
		List<double[]> shifts = new ArrayList<>();
		for (double dx = -MAX_SHIFT; dx <= MAX_SHIFT + 1.0E-6; dx += SHIFT_STEP) {
			for (double dz = -MAX_SHIFT; dz <= MAX_SHIFT + 1.0E-6; dz += SHIFT_STEP) {
				double distance = Math.hypot(dx, dz);
				if (distance <= MAX_SHIFT + 1.0E-6)
					shifts.add(new double[] { dx, dz, distance });
			}
		}
		shifts.sort(java.util.Comparator.comparingDouble(shift -> shift[2]));
		return shifts;
	}

	/**
	 * Whether a lying body fits with its middle at (cx, cz): flat enough ground
	 * under all of it, nothing in the way above, no liquid.
	 *
	 * @return {rest height, sag}, or null if it doesn't fit
	 */
	private double[] lyingFit(Pose pose, double cx, double cz, Vector head, double ref) {
		// The body's outline: along the middle, and along both edges at the body's width there.
		// Thousands of these are tried per death, so no objects are made and it stops at the first problem.
		double half = BodyShape.halfLength(pose) - 0.05;
		double hx = head.getX();
		double hz = head.getZ();
		int steps = (int) Math.ceil(2 * half / 0.25);
		double thickness = BodyShape.thickness(pose);
		double maxY = ref + MAX_STEP_UP;

		double top = Double.NEGATIVE_INFINITY;
		double bottom = Double.POSITIVE_INFINITY;
		double lowestCeiling = Double.POSITIVE_INFINITY;
		for (int i = 0; i <= steps; i++) {
			double t = -half + 2 * half * i / steps;
			double width = BodyShape.halfWidthAt(pose, t) - 0.02;
			for (int edge = 0; edge < 3; edge++) {
				double s = edge == 0 ? 0 : edge == 1 ? -width : width;
				Surface surface = surface(cx + hx * t - hz * s, cz + hz * t + hx * s, maxY, true);
				if (surface == null || surface.liquid() || surface.y() < ref - MAX_STEP_DOWN || surface.room() < thickness)
					return null;
				top = Math.max(top, surface.y());
				bottom = Math.min(bottom, surface.y());
				lowestCeiling = Math.min(lowestCeiling, surface.ceiling());
				if (top - bottom > MAX_SAG || lowestCeiling < top + thickness)
					return null;
			}
		}
		if (blocksBody(world.getBlockAt(floor(cx), floor(top + 0.05), floor(cz))))
			return null;
		return new double[] { top, top - bottom };
	}

	// -------------------------------------------------------------------------
	// Sitting
	// -------------------------------------------------------------------------

	/** Sitting on the stairs the player died on, back to the step behind, facing down. */
	private void stairs(double ref, double penalty) {
		int bx = floor(death.getX());
		int bz = floor(death.getZ());
		for (int x = bx - 1; x <= bx + 1; x++) {
			for (int z = bz - 1; z <= bz + 1; z++) {
				for (int y = floor(ref - 1.0); y <= floor(ref); y++) {
					Block block = world.getBlockAt(x, y, z);
					if (!(block.getBlockData() instanceof Stairs stairs) || stairs.getHalf() != Bisected.Half.BOTTOM
							|| stairs.getShape() != Stairs.Shape.STRAIGHT)
						continue;
					double seat = y + 0.5;
					if (seat > ref + MAX_STEP_UP || seat < ref - MAX_STEP_DOWN)
						continue;

					// The tall half is on the facing side; sit on the low half, just in front of it
					Vector up = stairs.getFacing().getDirection();
					Vector facing = up.clone().multiply(-1);
					double hx = x + 0.5 - up.getX() * 0.14;
					double hz = z + 0.5 - up.getZ() * 0.14;
					if (!sitFits(hx, hz, seat, facing, false))
						continue;
					double score = SCORE_STAIRS - COST_SHIFT * distance(hx, hz) - COST_HEIGHT * Math.abs(seat - ref) - penalty
							+ random.nextDouble() * VARIETY;
					sit(hx, seat, hz, facing, score);
				}
			}
		}
	}

	/** Slumped against a wall (or into a corner), legs out in front. */
	private void walls(double ref, double penalty) {
		for (Block cell : cellsAround()) {
			double cx = cell.getX() + 0.5;
			double cz = cell.getZ() + 0.5;
			Surface ground = surface(cx, cz, ref + MAX_STEP_UP, true);
			if (ground == null || ground.liquid() || Math.abs(ground.y() - ref) > MAX_STEP_UP)
				continue;
			double floor = ground.y();

			List<Vector> walls = new ArrayList<>();
			for (BlockFace face : SIDES) {
				Vector toWall = face.getDirection();
				if (solidAt(cx + toWall.getX() * 0.7, floor + 0.4, cz + toWall.getZ() * 0.7)
						&& solidAt(cx + toWall.getX() * 0.7, floor + 1.0, cz + toWall.getZ() * 0.7))
					walls.add(toWall);
			}

			for (Vector toWall : walls) {
				double hx = cx + toWall.getX() * 0.3;
				double hz = cz + toWall.getZ() * 0.3;
				Vector facing = toWall.clone().multiply(-1);
				if (sitFits(hx, hz, floor, facing, true))
					sit(hx, floor, hz, facing, SCORE_WALL - COST_SHIFT * distance(hx, hz)
							- COST_HEIGHT * Math.abs(floor - ref) - penalty + random.nextDouble() * VARIETY);
			}
			// Corners: two walls at a right angle
			for (int i = 0; i < walls.size(); i++) {
				for (int j = i + 1; j < walls.size(); j++) {
					Vector corner = walls.get(i).clone().add(walls.get(j));
					if (corner.lengthSquared() < 1.0E-6)
						continue; // Opposite walls
					double hx = cx + corner.getX() * 0.22;
					double hz = cz + corner.getZ() * 0.22;
					Vector facing = corner.clone().multiply(-1).normalize();
					if (sitFits(hx, hz, floor, facing, true))
						sit(hx, floor, hz, facing, SCORE_WALL + 2 - COST_SHIFT * distance(hx, hz)
								- COST_HEIGHT * Math.abs(floor - ref) - penalty + random.nextDouble() * VARIETY);
				}
			}
		}
	}

	/** Sitting on an edge with the legs hanging over the drop. */
	private void ledges(double ref, double penalty) {
		for (Block cell : cellsAround()) {
			double cx = cell.getX() + 0.5;
			double cz = cell.getZ() + 0.5;
			Surface ground = surface(cx, cz, ref + MAX_STEP_UP, true);
			if (ground == null || ground.liquid() || Math.abs(ground.y() - ref) > MAX_STEP_UP)
				continue;
			double floor = ground.y();

			for (BlockFace face : SIDES) {
				Vector out = face.getDirection();
				Surface beyond = surface(cx + out.getX() * 0.8, cz + out.getZ() * 0.8, floor + 0.05, true);
				if (beyond != null && beyond.y() > floor - 0.9)
					continue; // No drop there
				double hx = cx + out.getX() * 0.22;
				double hz = cz + out.getZ() * 0.22;
				if (sitFits(hx, hz, floor, out, false))
					sit(hx, floor, hz, out, SCORE_LEDGE - COST_SHIFT * distance(hx, hz)
							- COST_HEIGHT * Math.abs(floor - ref) - penalty + random.nextDouble() * VARIETY);
			}
		}
	}

	/**
	 * Room for a sitting body with its hips at (hx, hz) on a surface at
	 * {@code seat}: head room above, nothing where the shoulders and legs go.
	 */
	private boolean sitFits(double hx, double hz, double seat, Vector facing, boolean sameFloor) {
		Surface hips = surface(hx, hz, seat + 0.05, true);
		if (hips == null || hips.liquid() || Math.abs(hips.y() - seat) > 0.1 || hips.ceiling() < seat + BodyShape.SIT_HEIGHT)
			return false;

		Vector side = new Vector(-facing.getZ(), 0, facing.getX());
		for (double s : new double[] { -0.3, 0.3 }) {
			Surface shoulder = surface(hx + side.getX() * s, hz + side.getZ() * s, seat + 0.05, true);
			if (shoulder == null || shoulder.y() > seat + 0.05 || shoulder.ceiling() < seat + BodyShape.SIT_HEIGHT - 0.2)
				return false;
		}
		for (double t : new double[] { 0.35, BodyShape.SIT_LEG_REACH }) {
			// The legs spread a little toward the knees
			double spread = BodyShape.SIT_KNEE_SPREAD * t / BodyShape.SIT_LEG_REACH - 0.03;
			for (double s : new double[] { -spread, 0, spread }) {
				double lx = hx + facing.getX() * t + side.getX() * s;
				double lz = hz + facing.getZ() * t + side.getZ() * s;
				if (solidAt(lx, seat + 0.1, lz) || solidAt(lx, seat + 0.3, lz))
					return false;
				Surface leg = surface(lx, lz, seat + 0.05, true);
				if (sameFloor && (leg == null || leg.liquid() || leg.y() < seat - 0.1))
					return false; // Against a wall the legs rest on the floor, not over a hole
			}
		}
		return !blocksBody(world.getBlockAt(floor(hx), floor(seat + 0.05), floor(hz)));
	}

	private void sit(double hx, double seat, double hz, Vector facing, double score) {
		float yaw = BodyShape.yawFacing(facing.getX(), facing.getZ());
		Vector seatPoint = BodyShape.seat(hx, seat, hz);
		// Head hangs forward
		float pitch = 25 + random.nextFloat() * 20;
		consider(new BodyPlacement(seatPoint.getX(), seatPoint.getY(), seatPoint.getZ(), yaw, pitch, Pose.SITTING), score);
	}

	// -------------------------------------------------------------------------
	// Water and lava
	// -------------------------------------------------------------------------

	/** Floating on the surface at height {@code level}, where the whole body is over liquid. */
	private void floating(double x, double level, double z, Pose pose) {
		double offset = random.nextDouble() * (360.0 / DIRECTIONS);
		for (double dx = -MAX_SHIFT; dx <= MAX_SHIFT + 1.0E-6; dx += 0.5) {
			for (double dz = -MAX_SHIFT; dz <= MAX_SHIFT + 1.0E-6; dz += 0.5) {
				double cx = x + dx;
				double cz = z + dz;
				for (int i = 0; i < DIRECTIONS; i++) {
					double angle = Math.toRadians(offset + i * 360.0 / DIRECTIONS);
					Vector head = new Vector(-Math.sin(angle), 0, Math.cos(angle));
					if (!floats(pose, cx, cz, head, level))
						continue;
					float yaw = BodyShape.yawForHead(pose, head.getX(), head.getZ());
					// Partly under the surface
					double rest = level - (pose == Pose.SWIMMING ? 0.17 : 0.1);
					Vector origin = BodyShape.lyingOrigin(pose, cx, rest, cz, head);
					double score = SCORE_FLOAT - COST_SHIFT * Math.hypot(dx, dz) + random.nextDouble() * VARIETY;
					consider(new BodyPlacement(origin.getX(), origin.getY(), origin.getZ(), yaw, 0, pose), score);
				}
			}
		}
	}

	private boolean floats(Pose pose, double cx, double cz, Vector head, double level) {
		double half = BodyShape.halfLength(pose) - 0.1;
		for (double t = -half; t <= half + 1.0E-6; t += half / 3) {
			Surface surface = surface(cx + head.getX() * t, cz + head.getZ() * t, level + 0.05, true);
			if (surface == null || !surface.liquid() || Math.abs(surface.y() - level) > 0.15 || surface.room() < 0.4)
				return false;
		}
		return true;
	}

	/** Top of the water above this water block, if there is air there (not a flooded cave). */
	private Double waterSurfaceAbove(Block water) {
		Block block = water;
		for (int i = 0; i < MAX_FLOAT_DEPTH; i++) {
			Block above = block.getRelative(BlockFace.UP);
			if (above.getType() != Material.WATER)
				return above.isPassable() && !above.isLiquid() ? block.getY() + liquidHeight(block) : null;
			block = above;
		}
		return null;
	}

	// -------------------------------------------------------------------------
	// Choosing
	// -------------------------------------------------------------------------

	private void consider(BodyPlacement placement, double score) {
		if (score <= bestScore || taken.test(placement))
			return;
		best = placement;
		bestScore = score;
	}

	/** Nothing fits: lie on the back where they died, like before. */
	private BodyPlacement fallback(double x, double ground, double z) {
		double cx = floor(x) + 0.5;
		double cz = floor(z) + 0.5;
		Surface surface = surface(cx, cz, ground + 0.05, true);
		double rest = surface != null && ground - surface.y() < 1 ? surface.y() : ground;
		float yaw = random.nextFloat() * 360f - 180f;
		return new BodyPlacement(cx, rest + BodyShape.BACK_LIFT, cz, yaw, 0, Pose.SLEEPING);
	}

	// -------------------------------------------------------------------------
	// Terrain
	// -------------------------------------------------------------------------

	/** Out of walls, lava and portals: the first free height at or above the death spot. */
	private double escapeUp(double x, double y, double z) {
		int maxY = world.getMaxHeight() - 2;
		for (int i = 0; i < MAX_ESCAPE_UP && y < maxY; i++) {
			Block block = world.getBlockAt(floor(x), floor(y), floor(z));
			if (!block.getType().isSolid() && block.getType() != Material.LAVA && !blocksBody(block))
				return y;
			y = floor(y) + 1;
		}
		return y;
	}

	/**
	 * The highest surface at (x, z) no higher than {@code maxY}: the top of a
	 * block's collision box (stairs, slabs and snow included) or of a liquid.
	 * Null if there is nothing below (the void).
	 */
	private Surface surface(double x, double z, double maxY, boolean liquids) {
		// Rounded down to 1/16 block (the grid block shapes are made on), so a point never lands in the next block
		long key = ((long) Math.floor(x * 16) & 0xFFFFFL) << 40 | ((long) Math.floor(z * 16) & 0xFFFFFL) << 20
				| ((long) Math.floor(maxY * 16) & 0x7FFFFL) << 1 | (liquids ? 1 : 0);
		Surface cached = surfaces.get(key);
		if (cached != null)
			return cached;

		int bx = floor(x);
		int bz = floor(z);
		double lx = x - bx;
		double lz = z - bz;
		int minY = world.getMinHeight();
		Surface found = null;
		for (int by = floor(maxY); by >= minY && by >= floor(maxY) - 24; by--) {
			Cell cell = cell(bx, by, bz);
			double top = Double.NEGATIVE_INFINITY;
			for (BoundingBox box : cell.boxes()) {
				if (contains(box, lx, lz) && by + box.getMaxY() <= maxY + 1.0E-6)
					top = Math.max(top, by + box.getMaxY());
			}
			boolean liquid = false;
			if (liquids && !Double.isNaN(cell.liquid())) {
				double level = by + cell.liquid();
				if (level <= maxY + 1.0E-6 && level > top) {
					top = level;
					liquid = true;
				}
			}
			if (top > Double.NEGATIVE_INFINITY) {
				found = new Surface(top, liquid, ceiling(bx, bz, lx, lz, top));
				break;
			}
		}
		surfaces.put(key, found);
		return found;
	}

	/** The bottom of the first collision box above {@code floor} at this point (up to 2.5 blocks up). */
	private double ceiling(int bx, int bz, double lx, double lz, double floor) {
		double lowest = floor + 2.5;
		for (int by = floor(floor); by <= floor(floor + 2.5); by++) {
			for (BoundingBox box : cell(bx, by, bz).boxes()) {
				double bottom = by + box.getMinY();
				if (contains(box, lx, lz) && bottom >= floor - 1.0E-6 && by + box.getMaxY() > floor + 1.0E-6)
					lowest = Math.min(lowest, bottom);
			}
		}
		return lowest;
	}

	/** Whether the point is inside a block's collision box. */
	private boolean solidAt(double x, double y, double z) {
		int bx = floor(x);
		int by = floor(y);
		int bz = floor(z);
		for (BoundingBox box : cell(bx, by, bz).boxes()) {
			if (contains(box, x - bx, z - bz) && y - by >= box.getMinY() && y - by <= box.getMaxY())
				return true;
		}
		return false;
	}

	private Cell cell(int x, int y, int z) {
		long key = ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
		return cells.computeIfAbsent(key, k -> {
			Block block = world.getBlockAt(x, y, z);
			BoundingBox[] boxes = block.getCollisionShape().getBoundingBoxes().toArray(new BoundingBox[0]);
			return new Cell(boxes, block.isLiquid() ? liquidHeight(block) : Double.NaN);
		});
	}

	private static boolean contains(BoundingBox box, double lx, double lz) {
		return lx >= box.getMinX() && lx <= box.getMaxX() && lz >= box.getMinZ() && lz <= box.getMaxZ();
	}

	/** Height of the liquid in its block: sources and still water are just under full. */
	private static double liquidHeight(Block block) {
		BlockData data = block.getBlockData();
		if (!(data instanceof Levelled levelled))
			return 0.9;
		int level = levelled.getLevel();
		if (level >= 8)
			return 1.0; // Falling
		return (8 - Math.max(level, 0)) / 9.0;
	}

	/** Blocks a body must not lie in: portals would carry it away, plates and tripwires would stay pressed. */
	static boolean blocksBody(Block block) {
		Material type = block.getType();
		return type == Material.NETHER_PORTAL || type == Material.END_PORTAL || type == Material.END_GATEWAY
				|| type == Material.TRIPWIRE || Tag.PRESSURE_PLATES.isTagged(type);
	}

	/** The death spot's block column and its eight neighbours. */
	private List<Block> cellsAround() {
		List<Block> cells = new ArrayList<>(9);
		int bx = floor(death.getX());
		int by = floor(death.getY());
		int bz = floor(death.getZ());
		for (int x = bx - 1; x <= bx + 1; x++) {
			for (int z = bz - 1; z <= bz + 1; z++)
				cells.add(world.getBlockAt(x, by, z));
		}
		return cells;
	}

	private double distance(double x, double z) {
		return Math.hypot(x - death.getX(), z - death.getZ());
	}

	private static int floor(double value) {
		return (int) Math.floor(value);
	}
}
