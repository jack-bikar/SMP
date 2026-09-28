package games.coob.smp.duel;

import games.coob.smp.settings.Settings;
import games.coob.smp.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A fixed square border around a duel. Each duel has its own border object, so
 * any number of duels can run at the same time without affecting each other.
 * <ul>
 * <li>Each duelist gets a client-side world border (the vanilla wall). It is
 * only sent to that duel's players, blocks walking out and tints the screen red
 * near the edge.</li>
 * <li>Players who still get outside (knockback, pearls) are pushed back in and
 * take damage.</li>
 * </ul>
 */
public final class DuelBorder {

	private static final int CHECK_PERIOD_TICKS = 10;
	/** Players further out than this are teleported back inside. */
	private static final double TELEPORT_BACK_DISTANCE = 4;

	private final Location center;
	private final double radius;
	private final List<Player> players = new ArrayList<>();
	private BukkitTask task;
	private int ticks;

	public DuelBorder(Location center, int radius) {
		this.center = center.clone();
		this.radius = radius;
	}

	/**
	 * Shows the border to the players and starts enforcing it.
	 *
	 * @param damageEnabled whether players outside currently take damage
	 */
	public void start(BooleanSupplier damageEnabled, Collection<Player> duelists) {
		WorldBorder border = Bukkit.createWorldBorder();
		border.setCenter(center.getX(), center.getZ());
		border.setSize(radius * 2);
		// Screen turns red within this many blocks of the wall
		border.setWarningDistance(5);

		for (Player player : duelists) {
			players.add(player);
			player.setWorldBorder(border);
		}

		task = SchedulerUtil.runTimer(CHECK_PERIOD_TICKS, CHECK_PERIOD_TICKS, () -> {
			ticks += CHECK_PERIOD_TICKS;
			boolean damageTick = ticks % 20 == 0 && damageEnabled.getAsBoolean();
			// Copy: border damage can eliminate a player, which removes them from the list
			for (Player player : new ArrayList<>(players)) {
				if (player.isOnline() && player.getWorld().equals(center.getWorld()))
					enforce(player, damageTick);
			}
		});
	}

	private void enforce(Player player, boolean damageTick) {
		Location location = player.getLocation();
		double dx = location.getX() - center.getX();
		double dz = location.getZ() - center.getZ();
		double outside = Math.max(Math.abs(dx), Math.abs(dz)) - radius;
		if (outside <= 0)
			return;

		// Knocked-out players spectate from inside the arena; they fly through walls, so just bring them back
		if (player.getGameMode() == GameMode.SPECTATOR) {
			double limit = radius - 1;
			Location back = location.clone();
			back.setX(center.getX() + Math.clamp(dx, -limit, limit));
			back.setZ(center.getZ() + Math.clamp(dz, -limit, limit));
			player.teleportAsync(back);
			return;
		}

		if (outside > TELEPORT_BACK_DISTANCE) {
			player.teleportAsync(clampInside(location));
		} else {
			Vector push = new Vector(-dx, 0, -dz).normalize().multiply(0.6).setY(0.3);
			player.setVelocity(push);
		}

		player.sendActionBar(Component.text("Stay inside the arena!", NamedTextColor.RED));
		if (damageTick && Settings.DuelSection.BORDER_DAMAGE_PER_SECOND > 0)
			player.damage(Settings.DuelSection.BORDER_DAMAGE_PER_SECOND);
	}

	/** A safe spot just inside the border, on the ground. */
	private Location clampInside(Location location) {
		double limit = radius - 2;
		double x = center.getX() + Math.clamp(location.getX() - center.getX(), -limit, limit);
		double z = center.getZ() + Math.clamp(location.getZ() - center.getZ(), -limit, limit);
		World world = center.getWorld();
		int y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z), HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
		return new Location(world, x, y, z, location.getYaw(), location.getPitch());
	}

	/** Whether a location is inside the border (used to block ender pearls out). */
	public boolean contains(Location location) {
		return location.getWorld() != null && location.getWorld().equals(center.getWorld())
				&& Math.abs(location.getX() - center.getX()) <= radius
				&& Math.abs(location.getZ() - center.getZ()) <= radius;
	}

	/**
	 * Removes the border for a single player (e.g. when they lose).
	 */
	public void remove(Player player) {
		if (players.remove(player) && player.isOnline())
			player.setWorldBorder(null);
	}

	/**
	 * Removes the border for everyone and stops enforcing it.
	 */
	public void stop() {
		if (task != null) {
			task.cancel();
			task = null;
		}
		for (Player player : players) {
			if (player.isOnline())
				player.setWorldBorder(null);
		}
		players.clear();
	}
}
