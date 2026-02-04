package games.coob.smp.duel;

import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Soft border system for duels with shrinking mechanics.
 * Each ActiveDuel has its own DuelBorder instance for concurrent duel support.
 */
public class DuelBorder {

	private final Location center;
	private final double startRadius;
	private final double endRadius;
	private final int shrinkTimeSeconds;
	private final double warningDistance;
	private final double knockbackStrength;
	private final double damagePerSecond;
	private final boolean useWorldBorder;
	private final boolean smoothWorldBorderShrink;
	private final int worldBorderWarningDistance;
	private final double worldBorderDamageAmount;
	private final double worldBorderDamageBuffer;

	private double currentRadius;
	private long startTime;
	private boolean active;
	private boolean shrinking;

	private BukkitTask borderTask;
	private WorldBorder worldBorder;

	private final Set<Player> playersInDuel = new HashSet<>();
	private final Set<Player> warnedPlayers = new HashSet<>();
	private final Map<UUID, WorldBorder> previousWorldBorders = new HashMap<>();

	public DuelBorder(Location center) {
		this.center = center.clone();
		this.startRadius = Settings.DuelSection.BORDER_START_RADIUS;
		this.endRadius = Settings.DuelSection.BORDER_END_RADIUS;
		this.shrinkTimeSeconds = Settings.DuelSection.BORDER_SHRINK_TIME_SECONDS;
		this.warningDistance = Settings.DuelSection.BORDER_WARNING_DISTANCE;
		this.knockbackStrength = Settings.DuelSection.BORDER_KNOCKBACK_STRENGTH;
		this.damagePerSecond = Settings.DuelSection.BORDER_DAMAGE_PER_SECOND;
		this.useWorldBorder = Settings.DuelSection.BORDER_USE_WORLD_BORDER;
		this.smoothWorldBorderShrink = Settings.DuelSection.BORDER_WORLD_BORDER_SMOOTH_SHRINK;
		this.worldBorderWarningDistance = Settings.DuelSection.BORDER_WORLD_BORDER_WARNING_DISTANCE;
		this.worldBorderDamageAmount = Settings.DuelSection.BORDER_WORLD_BORDER_DAMAGE_AMOUNT;
		this.worldBorderDamageBuffer = Settings.DuelSection.BORDER_WORLD_BORDER_DAMAGE_BUFFER;

		this.currentRadius = startRadius;
		this.active = false;
		this.shrinking = false;
	}

	/**
	 * Starts the border system.
	 *
	 * @param players The players participating in the duel
	 */
	public void start(Player... players) {
		this.active = true;
		this.startTime = System.currentTimeMillis();

		for (Player player : players) {
			playersInDuel.add(player);
		}

		if (useWorldBorder) {
			worldBorder = Bukkit.createWorldBorder();
			worldBorder.setCenter(center.getX(), center.getZ());
			worldBorder.setSize(startRadius * 2);
			worldBorder.setWarningDistance(worldBorderWarningDistance);
			worldBorder.setDamageAmount(worldBorderDamageAmount);
			worldBorder.setDamageBuffer(worldBorderDamageBuffer);

			for (Player player : playersInDuel) {
				if (player.isOnline()) {
					previousWorldBorders.put(player.getUniqueId(), player.getWorldBorder());
					player.setWorldBorder(worldBorder);
				}
			}
		}

		// Start the main border task (runs every second)
		borderTask = SchedulerUtil.runTimer(20, 20, this::tick);
	}

	/**
	 * Starts the border shrinking.
	 */
	public void startShrinking() {
		this.shrinking = true;
		this.startTime = System.currentTimeMillis();

		if (useWorldBorder && smoothWorldBorderShrink && worldBorder != null) {
			worldBorder.setSize(endRadius * 2, shrinkTimeSeconds);
		}

		for (Player player : playersInDuel) {
			ColorUtil.sendMessage(player, "&c&lBorder is now shrinking! Stay inside!");
		}
	}

	/**
	 * Main tick - handles shrinking, warnings, knockback, and damage.
	 */
	private void tick() {
		if (!active)
			return;

		// Update radius if shrinking
		if (shrinking) {
			long elapsed = System.currentTimeMillis() - startTime;
			double progress = Math.min(1.0, elapsed / (shrinkTimeSeconds * 1000.0));
			currentRadius = startRadius - (progress * (startRadius - endRadius));

			// Clamp to end radius
			if (currentRadius <= endRadius) {
				currentRadius = endRadius;
				shrinking = false;
			}
		}

		if (useWorldBorder && !smoothWorldBorderShrink && worldBorder != null) {
			worldBorder.setSize(currentRadius * 2);
		}

		// Check each player
		for (Player player : new HashSet<>(playersInDuel)) {
			if (!player.isOnline())
				continue;

			double distance = getHorizontalDistance(player.getLocation(), center);
			double distanceFromBorder = currentRadius - distance;

			if (distanceFromBorder < 0) {
				// Outside border - apply knockback and damage
				applyKnockback(player);
				applyDamage(player);
				warnedPlayers.add(player);
			} else if (distanceFromBorder < warningDistance) {
				// In warning zone
				if (!warnedPlayers.contains(player)) {
					ColorUtil.sendMessage(player, "&e&lWarning: &eYou are near the border!");
					warnedPlayers.add(player);
				}
			} else {
				// Safe zone
				warnedPlayers.remove(player);
			}
		}
	}

	/**
	 * Applies knockback toward the center.
	 */
	private void applyKnockback(Player player) {
		Location playerLoc = player.getLocation();
		Vector direction = center.toVector().subtract(playerLoc.toVector()).normalize();
		direction.setY(0.2); // Slight upward component
		direction.multiply(knockbackStrength);

		player.setVelocity(direction);
	}

	/**
	 * Applies damage for being outside the border.
	 */
	private void applyDamage(Player player) {
		player.damage(damagePerSecond);
		ColorUtil.sendMessage(player, "&c&lYou are outside the border! Return immediately!");
	}

	/**
	 * Gets horizontal distance between two locations (ignoring Y).
	 */
	private double getHorizontalDistance(Location loc1, Location loc2) {
		double dx = loc1.getX() - loc2.getX();
		double dz = loc1.getZ() - loc2.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * Checks if a player is inside the border.
	 */
	public boolean isInsideBorder(Player player) {
		return getHorizontalDistance(player.getLocation(), center) <= currentRadius;
	}

	/**
	 * Gets current border radius.
	 */
	public double getCurrentRadius() {
		return currentRadius;
	}

	/**
	 * Gets the center location.
	 */
	public Location getCenter() {
		return center.clone();
	}

	/**
	 * Removes a player from the border tracking.
	 */
	public void removePlayer(Player player) {
		playersInDuel.remove(player);
		warnedPlayers.remove(player);

		if (useWorldBorder) {
			WorldBorder previous = previousWorldBorders.remove(player.getUniqueId());
			if (player.isOnline()) {
				player.setWorldBorder(previous != null ? previous : player.getWorld().getWorldBorder());
			}
		}
	}

	/**
	 * Stops the border system.
	 */
	public void stop() {
		this.active = false;
		this.shrinking = false;

		if (borderTask != null) {
			borderTask.cancel();
			borderTask = null;
		}

		if (useWorldBorder) {
			for (Map.Entry<UUID, WorldBorder> entry : previousWorldBorders.entrySet()) {
				Player player = Bukkit.getPlayer(entry.getKey());
				if (player != null && player.isOnline()) {
					WorldBorder previous = entry.getValue();
					player.setWorldBorder(previous != null ? previous : player.getWorld().getWorldBorder());
				}
			}
			previousWorldBorders.clear();
			worldBorder = null;
		}

		playersInDuel.clear();
		warnedPlayers.clear();
	}

	/**
	 * Checks if the border is active.
	 */
	public boolean isActive() {
		return active;
	}

	/**
	 * Checks if the border is currently shrinking.
	 */
	public boolean isShrinking() {
		return shrinking;
	}
}
