package games.coob.smp.tracking;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

/**
 * Works out the colour a player's waypoint has on the vanilla locator bar,
 * exactly as the game does: an explicitly set waypoint colour, otherwise the
 * scoreboard team colour, otherwise a colour the client derives from the UUID.
 */
public final class WaypointColors {

	private WaypointColors() {
	}

	/** RGB colour of this player's waypoint. */
	public static int of(Player player) {
		Color explicit = player.getWaypointColor();
		if (explicit != null)
			return explicit.asRGB();

		Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntityTeam(player);
		if (team != null && team.hasColor() && team.color() != null)
			return team.color().value();

		return defaultColor(player.getUniqueId());
	}

	/**
	 * The client's default: ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9f).
	 */
	public static int defaultColor(UUID id) {
		return setBrightness(0xFF000000 | (id.hashCode() & 0xFFFFFF), 0.9f) & 0xFFFFFF;
	}

	/** Port of net.minecraft.util.ARGB#setBrightness. */
	private static int setBrightness(int color, float brightness) {
		int red = color >> 16 & 0xFF;
		int green = color >> 8 & 0xFF;
		int blue = color & 0xFF;
		int alpha = color >>> 24;

		int max = Math.max(Math.max(red, green), blue);
		int min = Math.min(Math.min(red, green), blue);
		float delta = max - min;
		float saturation = max != 0 ? delta / max : 0.0F;

		float hue;
		if (saturation == 0.0F) {
			hue = 0.0F;
		} else {
			float redC = (max - red) / delta;
			float greenC = (max - green) / delta;
			float blueC = (max - blue) / delta;
			if (red == max) {
				hue = blueC - greenC;
			} else if (green == max) {
				hue = 2.0F + redC - blueC;
			} else {
				hue = 4.0F + greenC - redC;
			}
			hue /= 6.0F;
			if (hue < 0.0F)
				hue += 1.0F;
		}

		if (saturation == 0.0F) {
			int value = Math.round(brightness * 255.0F);
			return argb(alpha, value, value, value);
		}

		float sector = (hue - (float) Math.floor(hue)) * 6.0F;
		float fraction = sector - (float) Math.floor(sector);
		float p = brightness * (1.0F - saturation);
		float q = brightness * (1.0F - saturation * fraction);
		float t = brightness * (1.0F - saturation * (1.0F - fraction));

		switch ((int) sector) {
			case 0 -> {
				red = Math.round(brightness * 255.0F);
				green = Math.round(t * 255.0F);
				blue = Math.round(p * 255.0F);
			}
			case 1 -> {
				red = Math.round(q * 255.0F);
				green = Math.round(brightness * 255.0F);
				blue = Math.round(p * 255.0F);
			}
			case 2 -> {
				red = Math.round(p * 255.0F);
				green = Math.round(brightness * 255.0F);
				blue = Math.round(t * 255.0F);
			}
			case 3 -> {
				red = Math.round(p * 255.0F);
				green = Math.round(q * 255.0F);
				blue = Math.round(brightness * 255.0F);
			}
			case 4 -> {
				red = Math.round(t * 255.0F);
				green = Math.round(p * 255.0F);
				blue = Math.round(brightness * 255.0F);
			}
			case 5 -> {
				red = Math.round(brightness * 255.0F);
				green = Math.round(p * 255.0F);
				blue = Math.round(q * 255.0F);
			}
			default -> {
			}
		}
		return argb(alpha, red, green, blue);
	}

	private static int argb(int alpha, int red, int green, int blue) {
		return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
	}
}
