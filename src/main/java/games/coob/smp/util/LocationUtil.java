package games.coob.smp.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Stores locations as plain strings ("world;x;y;z;yaw;pitch").
 * Unlike Bukkit's Location serialization, a missing world never breaks loading
 * the rest of the file; the location just resolves to null.
 */
public final class LocationUtil {

	private LocationUtil() {
	}

	public static String serialize(Location location) {
		if (location == null || location.getWorld() == null)
			return null;
		return location.getWorld().getName() + ";" + location.getX() + ";" + location.getY() + ";" + location.getZ()
				+ ";" + location.getYaw() + ";" + location.getPitch();
	}

	public static Location deserialize(String value) {
		if (value == null || value.isEmpty())
			return null;
		String[] parts = value.split(";");
		if (parts.length < 4)
			return null;
		World world = Bukkit.getWorld(parts[0]);
		if (world == null)
			return null;
		try {
			double x = Double.parseDouble(parts[1]);
			double y = Double.parseDouble(parts[2]);
			double z = Double.parseDouble(parts[3]);
			float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0;
			float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0;
			return new Location(world, x, y, z, yaw, pitch);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
