package games.coob.smp.model;

import de.slikey.effectlib.EffectManager;
import lombok.Getter;
import org.mineacademy.fo.plugin.SimplePlugin;

public class Effects {

	@Getter
	private static EffectManager effectManager;

	public static void load() {
		effectManager = new EffectManager(SimplePlugin.getInstance());
	}

	public static void disable() {
		effectManager.dispose();
	}
}