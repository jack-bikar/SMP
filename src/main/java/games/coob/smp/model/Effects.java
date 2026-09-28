package games.coob.smp.model;

import de.slikey.effectlib.Effect;
import de.slikey.effectlib.EffectManager;
import de.slikey.effectlib.effect.BigBangEffect;
import de.slikey.effectlib.effect.BleedEffect;
import de.slikey.effectlib.effect.CloudEffect;
import de.slikey.effectlib.effect.DiscoBallEffect;
import de.slikey.effectlib.effect.DragonEffect;
import de.slikey.effectlib.effect.FountainEffect;
import de.slikey.effectlib.effect.GridEffect;
import de.slikey.effectlib.effect.SkyRocketEffect;
import de.slikey.effectlib.effect.StarEffect;
import de.slikey.effectlib.effect.TornadoEffect;
import de.slikey.effectlib.effect.VortexEffect;
import games.coob.smp.SMPPlugin;
import org.bukkit.Location;

import java.util.Locale;
import java.util.function.Function;

/**
 * Death effects, powered by the bundled EffectLib. The effect manager is only
 * created the first time an effect is played.
 */
public final class Effects {

	private static EffectManager effectManager;

	private Effects() {
	}

	/**
	 * Plays a named effect at the location for a limited time.
	 */
	public static void play(String name, Location location, int durationSeconds) {
		Function<EffectManager, Effect> factory = switch (name.toLowerCase(Locale.ROOT)) {
			case "grid" -> GridEffect::new;
			case "sky_rocket" -> SkyRocketEffect::new;
			case "big_bang" -> BigBangEffect::new;
			case "tornado" -> TornadoEffect::new;
			case "disco_ball" -> DiscoBallEffect::new;
			case "bleed" -> BleedEffect::new;
			case "vortex" -> VortexEffect::new;
			case "star" -> StarEffect::new;
			case "cloud" -> CloudEffect::new;
			case "dragon" -> DragonEffect::new;
			case "fountain" -> FountainEffect::new;
			default -> null;
		};
		if (factory == null)
			return;

		if (effectManager == null)
			effectManager = new EffectManager(SMPPlugin.getInstance());

		Effect effect = factory.apply(effectManager);
		effect.setLocation(location);
		effect.duration = durationSeconds * 1000;
		effect.start();
	}

	public static void disable() {
		if (effectManager != null) {
			effectManager.dispose();
			effectManager = null;
		}
	}
}
