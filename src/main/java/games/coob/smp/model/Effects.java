package games.coob.smp.model;

import de.slikey.effectlib.EffectManager;
import games.coob.smp.SMPPlugin;
import lombok.Getter;

public class Effects {

	@Getter
	private static EffectManager effectManager;

	public static void load() {
		effectManager = new EffectManager(SMPPlugin.getInstance());
	}

	public static void disable() {
		if (effectManager != null) {
			effectManager.dispose();
		}
	}
}