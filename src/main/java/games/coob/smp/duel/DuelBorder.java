package games.coob.smp.duel;

import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

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
	private final Particle particleType;

	private double currentRadius;
	private long startTime;
	private boolean active;
	private boolean shrinking;

	private BukkitTask borderTask;
	private BukkitTask particleTask;

	private final Set<Player> playersInDuel = new HashSet<>();
	private final Set<Player> warnedPlayers = new HashSet<>();

	public DuelBorder(Location center) {
		this.center = center.clone();
		this.startRadius = Settings.DuelSection.BORDER_START_RADIUS;
		this.endRadius = Settings.DuelSection.BORDER_END_RADIUS;
		this.shrinkTimeSeconds = Settings.DuelSection.BORDER_SHRINK_TIME_SECONDS;
		this.warningDistance = Settings.DuelSection.BORDER_WARNING_DISTANCE;
		this.knockbackStrength = Settings.DuelSection.BORDER_KNOCKBACK_STRENGTH;
		this.damagePerSecond = Settings.DuelSection.BORDER_DAMAGE_PER_SECOND;

		// Parse particle type
		Particle particle;
		try {
			particle = Particle.valueOf(Settings.DuelSection.BORDER_PARTICLE_TYPE.toUpperCase());
		} catch (IllegalArgumentException e) {
			particle = Particle.FLAME;
		}
		this.particleType = particle;

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

		// Start the main border task (runs every second)
		borderTask = SchedulerUtil.runTimer(20, 20, this::tick);

		// Start particle visualization (runs every 5 ticks)
		particleTask = SchedulerUtil.runTimer(5, 5, this::drawBorder);
	}

	/**
	 * Starts the border shrinking.
	 */
	public void startShrinking() {
		this.shrinking = true;
		this.startTime = System.currentTimeMillis();

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
	 * Draws the border using particles.
	 */
	private void drawBorder() {
		if (!active)
			return;

		// Draw circle of particles at border edge
		int points = (int) (currentRadius * 4); // More points for larger radius
		points = Math.min(points, 100); // Cap at 100 points

		for (int i = 0; i < points; i++) {
			double angle = (2 * Math.PI * i) / points;
			double x = center.getX() + currentRadius * Math.cos(angle);
			double z = center.getZ() + currentRadius * Math.sin(angle);

			// Draw particles at multiple heights for visibility
			for (int y = 0; y < 3; y++) {
				Location particleLocation = new Location(center.getWorld(), x, center.getY() + y, z);

				// Only show to nearby players for performance
				for (Player player : playersInDuel) {
					if (player.isOnline() && player.getLocation().distance(particleLocation) < 50) {
						player.spawnParticle(particleType, particleLocation, 1, 0, 0, 0, 0);
					}
				}
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

		if (particleTask != null) {
			particleTask.cancel();
			particleTask = null;
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
