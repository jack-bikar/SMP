package games.coob.smp.duel;

import games.coob.smp.util.SchedulerUtil;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Brings a player into the arena from the sky in a few quick steps, with
 * effects and sounds. The player's own state (speed, gravity, flight) is never
 * changed, so nothing needs restoring if the descent is interrupted; the duel
 * cancels all damage while players are arriving.
 */
public final class DuelFlyDownTeleporter {

	private static final int HEIGHT_DECREASE_PER_STEP = 50;
	private static final int INITIAL_DELAY_TICKS = 20;
	private static final int PERIOD_TICKS = 12;

	private DuelFlyDownTeleporter() {
	}

	/**
	 * Starts the descent. {@code onLanded} runs on the main thread once the player
	 * stands on {@code landing}; it is not called if the player goes offline.
	 *
	 * @return the task, so the duel can cancel it
	 */
	public static BukkitTask descend(Player player, Location landing, Runnable onLanded) {
		final int[] height = { landing.getWorld().getMaxHeight() - landing.getBlockY() };
		final BukkitTask[] task = new BukkitTask[1];

		task[0] = SchedulerUtil.runTimer(INITIAL_DELAY_TICKS, PERIOD_TICKS, () -> {
			if (!player.isOnline()) {
				task[0].cancel();
				return;
			}

			if (height[0] <= HEIGHT_DECREASE_PER_STEP) {
				task[0].cancel();
				player.teleportAsync(landing).thenAccept(success -> {
					if (!player.isOnline())
						return;
					player.setFallDistance(0);
					player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BIG_FALL, 0.5f, 1f);
					onLanded.run();
				});
				return;
			}

			height[0] -= HEIGHT_DECREASE_PER_STEP;
			Location step = landing.clone().add(0, height[0], 0);
			step.setPitch(90);
			player.teleportAsync(step).thenAccept(success -> {
				if (!player.isOnline())
					return;
				player.setFallDistance(0);
				player.getWorld().playEffect(player.getLocation(), Effect.ENDER_SIGNAL, null);
				player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.3f, 1f);
			});
		});
		return task[0];
	}
}
