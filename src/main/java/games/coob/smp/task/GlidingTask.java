package games.coob.smp.task;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.mineacademy.fo.remain.CompParticle;
import org.mineacademy.fo.remain.Remain;

public class GlidingTask extends BukkitRunnable {

	@Override
	public void run() {
		for (final Player player : Remain.getOnlinePlayers()) {
			if (player.isGliding()) {
				final Location location = player.getLocation();

				CompParticle.FLASH.spawn(location);
			}
		}
	}
}
