package games.coob.smp.settings;

import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.settings.Lang;
import org.mineacademy.fo.settings.SimpleSettings;
import org.mineacademy.fo.settings.YamlStaticConfig;

import java.util.Arrays;
import java.util.List;

/**
 * A sample settings class, utilizing {@link YamlStaticConfig} with prebuilt settings.yml handler
 * with a bunch of preconfigured keys, see resources/settings.yml
 * <p>
 * Foundation detects if you have "settings.yml" placed in your jar (in src/main/resources in source)
 * and will load this class automatically. The same goes for the {@link Lang} class which is
 * automatically loaded when we detect the presence of at least one localization/messages_X.yml
 * file in your jar.
 */
@SuppressWarnings("unused")
public final class Settings extends SimpleSettings {

	/**
	 * @see org.mineacademy.fo.settings.SimpleSettings#getConfigVersion()
	 */
	@Override
	protected int getConfigVersion() {
		return 1;
	}

	/**
	 * Place the sections where user can create new "key: value" pairs
	 * here so that they are not removed while adding comments.
	 * <p>
	 * Example use in ChatControl: user can add new channels in "Channels.List"
	 * section so we place "Channels.List" here.
	 *
	 * @return the ignored sections
	 */
	@Override
	protected List<String> getUncommentedSections() {
		return Arrays.asList(
				"Example.Uncommented_Section");
	}

	public static class DeathStorageSection {
		public static Boolean ENABLE_DEATH_STORAGE;
		public static CompMaterial STORAGE_MATERIAL;
		public static String HOLOGRAM_TEXT;
		public static Integer HOLOGRAM_VISIBLE_RANGE;

		/*
		 * Automatically called method when we load settings.yml to load values in this subclass
		 */
		private static void init() {

			// A convenience method to instruct the loader to prepend all paths with Example so you
			// do not have to call "Example.Key1", "Example.Key2" all the time, only "Key1" and "Key2".
			setPathPrefix("Death_Storage");

			ENABLE_DEATH_STORAGE = getBoolean("Enable_Death_Storage");
			STORAGE_MATERIAL = getMaterial("Storage_Material");
			HOLOGRAM_TEXT = getString("Hologram_Text");
			HOLOGRAM_VISIBLE_RANGE = getInteger("Hologram_Visible_Range");
		}
	}

	public static class CompassSection {
		public static Boolean ENABLE_COMPASS;
		public static String ALLOWED_ENVIRONEMENTS;

		private static void init() {
			setPathPrefix("Compass_Tracker");

			ENABLE_COMPASS = getBoolean("Enable_Compass");
			ALLOWED_ENVIRONEMENTS = getString("Allowed_Environements");
		}
	}

	public static class ProjectileSection {
		public static Boolean ENABLE_TRAILS;
		public static String ACTIVE_TRAIL;
		public static Double KNOCKBACK;
		public static Boolean ENABLE_HEADSHOT;

		private static void init() {
			setPathPrefix("Projectile_Settings");

			ENABLE_TRAILS = getBoolean("Enable_Trails");
			ACTIVE_TRAIL = getString("Active_Trail");
			KNOCKBACK = getDouble("Knockback");
			ENABLE_HEADSHOT = getBoolean("Enable_Headshot");
		}
	}

	public static class DeathEffectSection {
		public static Boolean ENABLE_DEATH_EFFECTS;
		public static String ACTIVE_DEATH_EFFECT;

		private static void init() {
			setPathPrefix("Death_Effects");

			ENABLE_DEATH_EFFECTS = getBoolean("Enable_Death_Effects");
			ACTIVE_DEATH_EFFECT = getString("Active_Death_Effect");
		}
	}

	public static class MOTDSection {
		public static Boolean ENABLE_MOTD;
		public static String MOTD_TEXT;

		private static void init() {
			setPathPrefix("MOTD");

			ENABLE_MOTD = getBoolean("Enable_MOTD");
			MOTD_TEXT = getString("MOTD_Text");
		}
	}

	public static class ThrowingAxeSection {
		public static Boolean ENABLE_THROWING_AXE;

		private static void init() {
			setPathPrefix("Throwing_Axe");

			ENABLE_THROWING_AXE = getBoolean("Enable_Throwing_Axe");
		}
	}

	public static class CombatSection {
		public static Boolean ENABLE_COMBAT_PUNISHMENTS;
		public static Integer SECONDS_TILL_PLAYER_LEAVES_COMBAT;

		private static void init() {
			setPathPrefix("Combat_Settings");

			ENABLE_COMBAT_PUNISHMENTS = getBoolean("Enable_Combat_Punishments");
			SECONDS_TILL_PLAYER_LEAVES_COMBAT = getInteger("Combat_Timer");
		}
	}
}
