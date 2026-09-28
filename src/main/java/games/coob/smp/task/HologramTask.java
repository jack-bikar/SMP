package games.coob.smp.task;

import games.coob.smp.model.DeathChestRegistry;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Keeps death chest holograms spawned in loaded chunks. Holograms are text
 * displays that the client culls by distance, so no per-player work is needed.
 */
public final class HologramTask extends BukkitRunnable {

	@Override
	public void run() {
		DeathChestRegistry.getInstance().tick();
	}
}
