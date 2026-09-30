package games.coob.smp.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Pose;
import org.bukkit.util.Vector;

/**
 * Exactly how a body lies (or sits) in the world, worked out by {@link BodyPlacer}.
 *
 * @param x        where the mannequin stands; for a sitting body, where its seat is
 * @param y        see x
 * @param z        see x
 * @param bodyYaw  the direction the body faces (for lying bodies it decides which way the head points).
 *                 The head always looks the same way: clients take the body's direction from the
 *                 head's when the mannequin appears, so any difference wouldn't last.
 * @param pitch    head tilt (a sitting body's head hangs down)
 * @param pose     {@link Pose#SLEEPING} on its back, {@link Pose#SWIMMING} face down, or
 *                 {@link Pose#SITTING} for a body sitting on a seat
 */
public record BodyPlacement(double x, double y, double z, float bodyYaw, float pitch, Pose pose) {

	public boolean isSitting() {
		return pose == Pose.SITTING;
	}

	/** Where the mannequin (or its seat) goes. */
	public Location location(World world) {
		return new Location(world, x, y, z, bodyYaw, pitch);
	}

	/** Middle of the body at ground level, for the click box and hologram. */
	public Vector center() {
		return BodyShape.center(this);
	}

	/**
	 * The line the body covers on the ground, head end to feet end (for a sitting
	 * body, hips to feet), so bodies can be kept from lying across each other.
	 */
	public Vector[] axis() {
		return BodyShape.axis(this);
	}

	/** Whether this body would lie on (or right next to) the other one. */
	public boolean overlaps(BodyPlacement other) {
		return axesOverlap(axis(), other.axis());
	}

	/** Whether two bodies with these {@link #axis() axes} would lie on (or right next to) each other. */
	public static boolean axesOverlap(Vector[] a, Vector[] b) {
		if (Math.abs(a[0].getY() - b[0].getY()) >= 1.0)
			return false;
		// Far apart: no need for the exact distance (a body's axis is at most 1.4 long)
		double dx = (a[0].getX() + a[1].getX() - b[0].getX() - b[1].getX()) / 2;
		double dz = (a[0].getZ() + a[1].getZ() - b[0].getZ() - b[1].getZ()) / 2;
		double far = 1.4 + BodyShape.MIN_GAP;
		if (dx * dx + dz * dz > far * far)
			return false;
		return segmentDistance(a[0], a[1], b[0], b[1]) < BodyShape.MIN_GAP;
	}

	/** Shortest horizontal distance between two segments. */
	private static double segmentDistance(Vector p1, Vector p2, Vector q1, Vector q2) {
		if (segmentsCross(p1, p2, q1, q2))
			return 0;
		return Math.min(Math.min(pointToSegment(p1, q1, q2), pointToSegment(p2, q1, q2)),
				Math.min(pointToSegment(q1, p1, p2), pointToSegment(q2, p1, p2)));
	}

	private static double pointToSegment(Vector p, Vector a, Vector b) {
		double dx = b.getX() - a.getX();
		double dz = b.getZ() - a.getZ();
		double lengthSquared = dx * dx + dz * dz;
		double t = lengthSquared < 1.0E-9 ? 0
				: Math.clamp(((p.getX() - a.getX()) * dx + (p.getZ() - a.getZ()) * dz) / lengthSquared, 0, 1);
		return Math.hypot(p.getX() - (a.getX() + t * dx), p.getZ() - (a.getZ() + t * dz));
	}

	private static boolean segmentsCross(Vector p1, Vector p2, Vector q1, Vector q2) {
		double d1 = cross(q1, q2, p1);
		double d2 = cross(q1, q2, p2);
		double d3 = cross(p1, p2, q1);
		double d4 = cross(p1, p2, q2);
		return d1 * d2 < 0 && d3 * d4 < 0;
	}

	private static double cross(Vector a, Vector b, Vector p) {
		return (b.getX() - a.getX()) * (p.getZ() - a.getZ()) - (b.getZ() - a.getZ()) * (p.getX() - a.getX());
	}

	/** Height of the body's top above {@link #center()}. */
	public double topAboveCenter() {
		return BodyShape.topAboveCenter(pose);
	}
}
