package games.coob.smp.util;

import games.coob.smp.SMPPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

/**
 * Utility class for entity operations
 */
public final class EntityUtil {

	private EntityUtil() {
	}

	/**
	 * Track a flying projectile and execute a callback each tick
	 *
	 * @param projectile The projectile to track
	 * @param callback The callback to execute each tick (receives the location)
	 * @return The tracking task
	 */
	public static BukkitTask trackFlying(Projectile projectile, Consumer<Location> callback) {
		return new BukkitRunnable() {
			@Override
			public void run() {
				if (projectile.isDead() || !projectile.isValid()) {
					cancel();
					return;
				}

				callback.accept(projectile.getLocation());
			}
		}.runTaskTimer(SMPPlugin.getInstance(), 0, 1);
	}
}
