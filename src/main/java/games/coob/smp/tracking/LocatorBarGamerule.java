package games.coob.smp.tracking;

import org.bukkit.GameRule;
import org.bukkit.World;

/**
 * Ensures the locator bar gamerule is enabled in all worlds.
 *
 * The locator bar UI is controlled by a gamerule (per-world). If it's disabled
 * in Nether/End,
 * the locator bar won't show even if we set waypoint attributes / compass
 * targets.
 */
public final class LocatorBarGamerule {

	@SuppressWarnings("unchecked")
	private static final GameRule<Boolean> LOCATOR_BAR_RULE = resolveLocatorBarRule();

	private LocatorBarGamerule() {
	}

	public static void ensureEnabled(World world) {
		if (world == null || LOCATOR_BAR_RULE == null)
			return;
		try {
			// Always force-enable; our attribute toggles handle visibility per-player.
			world.setGameRule(LOCATOR_BAR_RULE, true);
		} catch (Throwable ignored) {
			// Ignore: older API / unexpected behavior
		}
	}

	@SuppressWarnings("unchecked")
	private static GameRule<Boolean> resolveLocatorBarRule() {
		try {
			// Newer Paper (1.21.5+) exposes GameRule.LOCATOR_BAR
			return (GameRule<Boolean>) GameRule.class.getField("LOCATOR_BAR").get(null);
		} catch (Throwable ignored) {
			return null;
		}
	}
}
