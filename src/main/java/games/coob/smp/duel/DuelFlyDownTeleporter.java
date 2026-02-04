package games.coob.smp.duel;

import games.coob.smp.util.SchedulerUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Teleports a player from the sky down to a ground location with a cosmic-style
 * descent animation (based on CosmicTeleportTask). Player is invulnerable and
 * flies down in steps with effects and sounds.
 */
public final class DuelFlyDownTeleporter {

	private static final int HEIGHT_DECREASE_PER_STEP = 50;
	private static final int INITIAL_DELAY_TICKS = 40;  // 2 seconds before first drop
	private static final int PERIOD_TICKS = 15;         // ~0.75 seconds between steps

	private DuelFlyDownTeleporter() {
	}

	/**
	 * Teleports a player from max height down to the ground location with a
	 * step-by-step descent animation. Calls onComplete when the player has landed.
	 *
	 * @param player         The player to teleport
	 * @param groundLocation The destination on the ground (will use block center + 1 block up for standing)
	 * @param onComplete     Called when the player has landed (on main thread)
	 */
	public static void teleportToGround(Player player, Location groundLocation, Runnable onComplete) {
		if (player == null || !player.isOnline() || groundLocation == null) {
			if (onComplete != null) {
				SchedulerUtil.runTask(onComplete);
			}
			return;
		}

		World world = groundLocation.getWorld();
		if (world == null) {
			if (onComplete != null) {
				SchedulerUtil.runTask(onComplete);
			}
			return;
		}

		// Ground location with center offset and standing position
		Location landLocation = groundLocation.clone().add(0.5, 0, 0.5);
		landLocation.setPitch(0);

		final int maxHeight = world.getMaxHeight();
		final int[] currentHeight = { maxHeight };
		final boolean[] firstRun = { true };
		final float[] previousWalkSpeed = { 0.2f };
		final float[] previousFlySpeed = { 0.1f };

		BukkitTask[] taskHolder = new BukkitTask[1];

		taskHolder[0] = SchedulerUtil.runTimer(INITIAL_DELAY_TICKS, PERIOD_TICKS, () -> {
			if (!player.isOnline()) {
				if (taskHolder[0] != null) {
					taskHolder[0].cancel();
				}
				return;
			}

			// First run: prepare player for descent
			if (firstRun[0]) {
				firstRun[0] = false;
				previousWalkSpeed[0] = player.getWalkSpeed();
				previousFlySpeed[0] = player.getFlySpeed();

				player.setWalkSpeed(0f);
				player.setFlySpeed(0f);
				player.setAllowFlight(true);
				player.setFlying(true);
				player.setGravity(false);
				player.setNoDamageTicks(Integer.MAX_VALUE);
			}

			// Reached ground: land and restore
			if (currentHeight[0] <= HEIGHT_DECREASE_PER_STEP) {
				if (taskHolder[0] != null) {
					taskHolder[0].cancel();
					taskHolder[0] = null;
				}

				// Final position at ground
				Location finalPos = landLocation.clone();
				player.teleport(finalPos);

				player.setWalkSpeed(previousWalkSpeed[0]);
				player.setFlySpeed(previousFlySpeed[0]);
				player.setGravity(true);
				player.setNoDamageTicks(0);

				if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
					player.setAllowFlight(true);
				} else {
					player.setAllowFlight(false);
				}
				player.setFlying(false);

				player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BIG_FALL, 0.5f, 1f);

				if (onComplete != null) {
					onComplete.run();
				}
				return;
			}

			// Next step down
			currentHeight[0] -= HEIGHT_DECREASE_PER_STEP;
			Location highPos = landLocation.clone().add(0, currentHeight[0], 0);
			highPos.setPitch(90);
			player.teleport(highPos);

			// Effect and sound for this step
			SchedulerUtil.runLater(2, () -> {
				if (player.isOnline()) {
					player.getWorld().playEffect(player.getLocation(), Effect.ENDER_SIGNAL, null);
					player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.3f, 1f);
				}
			});
		});
	}
}
