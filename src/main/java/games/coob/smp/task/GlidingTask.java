package games.coob.smp.task;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class GlidingTask extends BukkitRunnable {

	@Override
	public void run() {
		for (final Player player : Bukkit.getOnlinePlayers()) {
			if (player.isGliding()) {
				final Location location = player.getLocation();
				location.getWorld().spawnParticle(Particle.FLASH, location, 1);
			}
		}
	}
}
