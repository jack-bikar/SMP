package games.coob.smp.util;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

public class ArmourStandUtil {

	public static Location getSmallArmTip(final ArmorStand armorStand) {
		// Gets shoulder location
		final Location standLocation = armorStand.getLocation().clone();
		standLocation.setYaw(standLocation.getYaw() + 90f);
		final Vector direction = standLocation.getDirection();

		standLocation.setX(standLocation.getX() + 4f / 16f * direction.getX());
		standLocation.setY(standLocation.getY() + 11f / 16f);
		standLocation.setZ(standLocation.getZ() + 4f / 16f * direction.getZ());

		// Get Hand Location
		final EulerAngle rightArmPose = armorStand.getRightArmPose();
		Vector armDirection = getDirection(rightArmPose.getY(), rightArmPose.getX(), -rightArmPose.getZ());
		armDirection = rotateAroundAxisY(armDirection, Math.toRadians(standLocation.getYaw() - 90f));

		standLocation.setX(standLocation.getX() + 5f / 16f * armDirection.getX());
		standLocation.setY(standLocation.getY() + 5f / 16f * armDirection.getY());
		standLocation.setZ(standLocation.getZ() + 5f / 16f * armDirection.getZ());

		return standLocation;
	}

	public static Location getArmTip(final ArmorStand armorStand) {
		// Gets shoulder location
		final Location standLocation = armorStand.getLocation().clone();
		standLocation.setYaw(standLocation.getYaw() + 90f);
		final Vector direction = standLocation.getDirection();

		standLocation.setX(standLocation.getX() + 5f / 16f * direction.getX());
		standLocation.setY(standLocation.getY() + 22f / 16f);
		standLocation.setZ(standLocation.getZ() + 5f / 16f * direction.getZ());

		// Get Hand Location
		final EulerAngle rightArmPose = armorStand.getRightArmPose();
		Vector armDirection = getDirection(rightArmPose.getY(), rightArmPose.getX(), -rightArmPose.getZ());
		armDirection = rotateAroundAxisY(armDirection, Math.toRadians(standLocation.getYaw() - 90f));

		standLocation.setX(standLocation.getX() + 10f / 16f * armDirection.getX());
		standLocation.setY(standLocation.getY() + 10f / 16f * armDirection.getY());
		standLocation.setZ(standLocation.getZ() + 10f / 16f * armDirection.getZ());

		return standLocation;
	}

	public static Vector getDirection(final Double yaw, final Double pitch, final Double roll) {
		Vector vector = new Vector(0, -1, 0);
		vector = rotateAroundAxisX(vector, pitch);
		vector = rotateAroundAxisY(vector, yaw);
		vector = rotateAroundAxisZ(vector, roll);
		return vector;
	}

	private static Vector rotateAroundAxisX(final Vector vector, final double angle) {
		final double y;
		final double z;
		final double cos;
		final double sin;
		cos = Math.cos(angle);
		sin = Math.sin(angle);
		y = vector.getY() * cos - vector.getZ() * sin;
		z = vector.getY() * sin + vector.getZ() * cos;
		return vector.setY(y).setZ(z);
	}

	private static Vector rotateAroundAxisY(final Vector vector, double angle) {
		angle = -angle;
		final double x;
		final double z;
		final double cos;
		final double sin;
		cos = Math.cos(angle);
		sin = Math.sin(angle);
		x = vector.getX() * cos + vector.getZ() * sin;
		z = vector.getX() * -sin + vector.getZ() * cos;
		return vector.setX(x).setZ(z);
	}

	private static Vector rotateAroundAxisZ(final Vector vector, final double angle) {
		final double x;
		final double y;
		final double cos;
		final double sin;
		cos = Math.cos(angle);
		sin = Math.sin(angle);
		x = vector.getX() * cos - vector.getY() * sin;
		y = vector.getX() * sin + vector.getY() * cos;
		return vector.setX(x).setY(y);
	}
}
