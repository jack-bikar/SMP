package games.coob.smp.util;

import games.coob.smp.SMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Utility class for scheduling tasks
 */
public final class SchedulerUtil {

	private SchedulerUtil() {
	}

	/**
	 * Run a task later (synchronously)
	 *
	 * @param delay Ticks to wait
	 * @param runnable The task to run
	 * @return The scheduled task
	 */
	public static BukkitTask runLater(long delay, Runnable runnable) {
		return Bukkit.getScheduler().runTaskLater(SMPPlugin.getInstance(), runnable, delay);
	}

	/**
	 * Run a task asynchronously later
	 *
	 * @param delay Ticks to wait
	 * @param runnable The task to run
	 * @return The scheduled task
	 */
	public static BukkitTask runLaterAsync(long delay, Runnable runnable) {
		return Bukkit.getScheduler().runTaskLaterAsynchronously(SMPPlugin.getInstance(), runnable, delay);
	}

	/**
	 * Run a repeating task
	 *
	 * @param delay Initial delay in ticks
	 * @param period Period between executions in ticks
	 * @param runnable The task to run
	 * @return The scheduled task
	 */
	public static BukkitTask runTimer(long delay, long period, Runnable runnable) {
		return Bukkit.getScheduler().runTaskTimer(SMPPlugin.getInstance(), runnable, delay, period);
	}

	/**
	 * Run a repeating task with no initial delay
	 *
	 * @param period Period between executions in ticks
	 * @param runnable The task to run
	 * @return The scheduled task
	 */
	public static BukkitTask runTimer(long period, Runnable runnable) {
		return runTimer(0, period, runnable);
	}

	/**
	 * Run a task asynchronously
	 *
	 * @param runnable The task to run
	 * @return The scheduled task
	 */
	public static BukkitTask runAsync(Runnable runnable) {
		return Bukkit.getScheduler().runTaskAsynchronously(SMPPlugin.getInstance(), runnable);
	}

	/**
	 * Run a task synchronously on the next tick
	 *
	 * @param runnable The task to run
	 * @return The scheduled task
	 */
	public static BukkitTask runTask(Runnable runnable) {
		return Bukkit.getScheduler().runTask(SMPPlugin.getInstance(), runnable);
	}
}
