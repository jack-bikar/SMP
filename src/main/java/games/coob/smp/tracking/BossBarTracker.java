package games.coob.smp.tracking;

import games.coob.smp.PlayerCache;
import games.coob.smp.settings.Settings;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossBarTracker {
	private static final BossBarTracker instance = new BossBarTracker();
	private final Map<UUID, BossBar> activeBossBars = new HashMap<>();

	public static BossBarTracker getInstance() {
		return instance;
	}

	public void updateTracking(Player tracker) {
		PlayerCache cache = PlayerCache.from(tracker);
		
		if (cache.getTrackingLocation() == null || !cache.getTrackingLocation().equals("Player")) {
			removeBossBar(tracker);
			return;
		}

		Player target = cache.getTargetByUUID() != null ? Bukkit.getPlayer(cache.getTargetByUUID()) : null;
		if (target == null || !target.isOnline()) {
			removeBossBar(tracker);
			return;
		}

		// Only show boss bar if in same world
		if (tracker.getWorld() != target.getWorld()) {
			removeBossBar(tracker);
			return;
		}

		Location targetLoc = target.getLocation();
		double distance = tracker.getLocation().distance(targetLoc);
		
		// Create or update boss bar
		BossBar bossBar = activeBossBars.get(tracker.getUniqueId());
		if (bossBar == null) {
			bossBar = BossBar.bossBar(
				Component.text("Tracking: " + target.getName()),
				1.0f,
				BossBar.Color.RED,
				BossBar.Overlay.PROGRESS
			);
			tracker.showBossBar(bossBar);
			activeBossBars.put(tracker.getUniqueId(), bossBar);
		}

		// Update progress based on distance (closer = more progress)
		// Max distance for full progress: 100 blocks
		float progress = (float) Math.min(1.0, Math.max(0.0, 1.0 - (distance / 100.0)));
		bossBar.progress(progress);
		bossBar.name(Component.text("Tracking: " + target.getName() + " (" + String.format("%.1f", distance) + "m)"));
	}

	public void removeBossBar(Player player) {
		BossBar bossBar = activeBossBars.remove(player.getUniqueId());
		if (bossBar != null) {
			player.hideBossBar(bossBar);
		}
	}

	public void removeAllBossBars() {
		for (Map.Entry<UUID, BossBar> entry : activeBossBars.entrySet()) {
			Player player = Bukkit.getPlayer(entry.getKey());
			if (player != null) {
				player.hideBossBar(entry.getValue());
			}
		}
		activeBossBars.clear();
	}
}
