package games.coob.smp.model;

import org.bukkit.entity.Pose;
import org.bukkit.util.Vector;

/**
 * The shape a mannequin is drawn with in each body pose, in blocks, relative to
 * the entity's position. Measured from the 26.3 client's own model code (player
 * model at 15/16 scale, hat/jacket/sleeve layers included).
 * <ul>
 * <li>On its back ({@link Pose#SLEEPING}, no bed): the feet are at the entity
 * position and the head points 90 degrees clockwise of the body yaw.</li>
 * <li>Face down ({@link Pose#SWIMMING}): the head (tilted up) and the arms point
 * forward, the feet backward.</li>
 * <li>Sitting ({@link Pose#SITTING}): the mannequin rides an invisible seat 0.6
 * above its feet; the thighs rest on the seat's height and the legs reach
 * forward, spread slightly, knees a little lower than the hips.</li>
 * </ul>
 * The client takes the body's direction from the yaw the mannequin is spawned
 * with, so bodies are always spawned facing their body yaw, head included.
 */
final class BodyShape {

	// On the back
	/** Feet to the top of the hat. */
	static final double BACK_LENGTH = 1.9;
	/**
	 * Entity height above the ground. The model is centred on it, so the back of
	 * the head sinks a little and the torso floats a little: the best middle ground.
	 */
	static final double BACK_LIFT = 0.15;
	/** Room the body needs above the ground (top of the face). */
	static final double BACK_THICKNESS = 0.45;
	/** Arms lie beside the torso, from 0.70 to 1.43 up from the feet. */
	static final double BACK_ARMS_FROM = 0.70;
	static final double BACK_ARMS_TO = 1.43;
	static final double BACK_ARMS_HALF_WIDTH = 0.55;

	// Face down
	/** From the entity position forward to the top of the hat (the fingertips stop just short). */
	static final double FACE_FRONT = 0.95;
	/** From the entity position back to the feet. */
	static final double FACE_BACK = 1.03;
	/** The torso is drawn 0.18 above the entity; this rests it on the ground (one foot dips into it). */
	static final double FACE_LIFT = -0.12;
	/** The head is tilted up, so the body needs this much room above the ground. */
	static final double FACE_THICKNESS = 0.72;
	/** The arms run beside the head and shoulders, forward of the hips. */
	static final double FACE_ARMS_HALF_WIDTH = 0.49;
	/** From the feet to the hips. */
	static final double FACE_LEGS = 0.71;

	/** Legs and head, in both lying poses. */
	static final double NARROW_HALF_WIDTH = 0.27;

	// Sitting
	/** The seat is this far above the surface, so the thighs rest on it. */
	static final double SEAT_RAISE = 0.02;
	/** The rider's feet are this far below its seat. */
	static final double RIDE_OFFSET = 0.6;
	/** Room a sitting body needs above the surface. */
	static final double SIT_HEIGHT = 1.35;
	/** How far the legs reach forward of the hips, and how far apart the knees are. */
	static final double SIT_LEG_REACH = 0.74;
	static final double SIT_KNEE_SPREAD = 0.45;

	/** Closest two bodies' axes may come (a body is about a block wide). */
	static final double MIN_GAP = 0.85;

	private BodyShape() {
	}

	// -------------------------------------------------------------------------
	// Yaw <-> direction (Minecraft: yaw 0 faces +Z, yaw 90 faces -X)
	// -------------------------------------------------------------------------

	/** Where the head points (horizontal unit vector) for a lying body with this body yaw. */
	static Vector headDirection(Pose pose, float bodyYaw) {
		double radians = Math.toRadians(bodyYaw);
		if (pose == Pose.SLEEPING)
			return new Vector(-Math.cos(radians), 0, Math.sin(radians));
		return forward(bodyYaw);
	}

	/** Body yaw that makes a lying body's head point along (dx, dz). */
	static float yawForHead(Pose pose, double dx, double dz) {
		if (pose == Pose.SLEEPING)
			return (float) Math.toDegrees(Math.atan2(dz, -dx));
		return yawFacing(dx, dz);
	}

	/** Yaw looking along (dx, dz). */
	static float yawFacing(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	static Vector forward(float yaw) {
		double radians = Math.toRadians(yaw);
		return new Vector(-Math.sin(radians), 0, Math.cos(radians));
	}

	// -------------------------------------------------------------------------
	// Lying: body middle <-> entity position
	// -------------------------------------------------------------------------

	/** Half the body's length along its axis. */
	static double halfLength(Pose pose) {
		return pose == Pose.SLEEPING ? BACK_LENGTH / 2 : (FACE_FRONT + FACE_BACK) / 2;
	}

	static double thickness(Pose pose) {
		return pose == Pose.SLEEPING ? BACK_THICKNESS : FACE_THICKNESS;
	}

	/**
	 * Half the body's width at a point along it, {@code t} measured from the
	 * middle of the body toward the head (-halfLength to +halfLength).
	 */
	static double halfWidthAt(Pose pose, double t) {
		double fromFeet = t + halfLength(pose);
		if (pose == Pose.SLEEPING)
			return fromFeet >= BACK_ARMS_FROM && fromFeet <= BACK_ARMS_TO ? BACK_ARMS_HALF_WIDTH : NARROW_HALF_WIDTH;
		return fromFeet < FACE_LEGS ? NARROW_HALF_WIDTH : FACE_ARMS_HALF_WIDTH;
	}

	/**
	 * Entity position for a lying body whose middle is at (x, z), lying on
	 * ground at height {@code ground}, head pointing along {@code head}.
	 */
	static Vector lyingOrigin(Pose pose, double x, double ground, double z, Vector head) {
		double back = pose == Pose.SLEEPING ? BACK_LENGTH / 2 : (FACE_FRONT - FACE_BACK) / 2;
		double lift = pose == Pose.SLEEPING ? BACK_LIFT : FACE_LIFT;
		return new Vector(x - head.getX() * back, ground + lift, z - head.getZ() * back);
	}

	/** Seat position for a body sitting on a surface at {@code surface}, hips at (x, z). */
	static Vector seat(double x, double surface, double z) {
		return new Vector(x, surface + SEAT_RAISE, z);
	}

	// -------------------------------------------------------------------------
	// A placed body (click box, hologram, overlap)
	// -------------------------------------------------------------------------

	/** Middle of the body at ground level. */
	static Vector center(BodyPlacement placement) {
		Pose pose = placement.pose();
		if (pose == Pose.SITTING) {
			// A bit in front of the hips
			Vector forward = forward(placement.bodyYaw());
			return new Vector(placement.x() + forward.getX() * 0.25, placement.y() - SEAT_RAISE,
					placement.z() + forward.getZ() * 0.25);
		}
		Vector head = headDirection(pose, placement.bodyYaw());
		double along = pose == Pose.SLEEPING ? BACK_LENGTH / 2 : (FACE_FRONT - FACE_BACK) / 2;
		double ground = placement.y() - (pose == Pose.SLEEPING ? BACK_LIFT : FACE_LIFT);
		return new Vector(placement.x() + head.getX() * along, ground, placement.z() + head.getZ() * along);
	}

	/** Head end and feet end at ground level (hips and knees for a sitting body). */
	static Vector[] axis(BodyPlacement placement) {
		Vector center = center(placement);
		if (placement.pose() == Pose.SITTING) {
			Vector forward = forward(placement.bodyYaw());
			Vector hips = new Vector(placement.x(), center.getY(), placement.z());
			return new Vector[] { hips, hips.clone().add(forward.multiply(SIT_LEG_REACH)) };
		}
		Vector head = headDirection(placement.pose(), placement.bodyYaw());
		double half = halfLength(placement.pose()) - NARROW_HALF_WIDTH;
		return new Vector[] { center.clone().add(head.clone().multiply(half)), center.clone().subtract(head.clone().multiply(half)) };
	}

	/** How tall the body is above the ground point returned by {@link #center}. */
	static double topAboveCenter(Pose pose) {
		return pose == Pose.SITTING ? SIT_HEIGHT : pose == Pose.SLEEPING ? BACK_THICKNESS : FACE_THICKNESS;
	}
}
