package games.coob.smp.settings;

import games.coob.smp.config.ConfigFile;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Settings configuration file
 */
public final class Settings extends ConfigFile {

	private static Settings instance;

	public Settings() {
		super("settings.yml");
		instance = this;
	}

	public static void loadSettings() {
		if (instance == null) {
			new Settings();
		} else {
			instance.reload();
		}
	}

	public static Settings getInstance() {
		if (instance == null) {
			loadSettings();
		}
		return instance;
	}

	@Override
	protected void onLoad() {
		super.onLoad();
		FileConfiguration config = getConfig();
		DeathStorageSection.load(config);
		CompassSection.load(config);
		ProjectileSection.load(config);
		DeathEffectSection.load(config);
		CombatSection.load(config);
	}

	// Death Storage Section
	public static class DeathStorageSection {
		public static boolean ENABLE_DEATH_STORAGE;
		public static Material STORAGE_MATERIAL;
		public static String HOLOGRAM_TEXT;
		public static int HOLOGRAM_VISIBLE_RANGE;

		public static void load(FileConfiguration config) {
			ENABLE_DEATH_STORAGE = config.getBoolean("Death_Storage.Enable_Death_Storage", true);
			String materialName = config.getString("Death_Storage.Storage_Material", "CHEST");
			STORAGE_MATERIAL = Material.matchMaterial(materialName);
			if (STORAGE_MATERIAL == null) {
				STORAGE_MATERIAL = Material.CHEST;
			}
			HOLOGRAM_TEXT = config.getString("Death_Storage.Hologram_Text", "&6{player}'s loot");
			HOLOGRAM_VISIBLE_RANGE = config.getInt("Death_Storage.Hologram_Visible_Range", 20);
		}
	}

	// Compass Section
	public static class CompassSection {
		public static boolean ENABLE_COMPASS;
		public static String ALLOWED_ENVIRONEMENTS;

		public static void load(FileConfiguration config) {
			ENABLE_COMPASS = config.getBoolean("Compass_Tracker.Enable_Compass", true);
			ALLOWED_ENVIRONEMENTS = config.getString("Compass_Tracker.Allowed_Environements", "all");
		}
	}

	// Projectile Section
	public static class ProjectileSection {
		public static boolean ENABLE_TRAILS;
		public static String ACTIVE_TRAIL;
		public static double KNOCKBACK;
		public static boolean ENABLE_HEADSHOT;

		public static void load(FileConfiguration config) {
			ENABLE_TRAILS = config.getBoolean("Projectile_Settings.Enable_Trails", true);
			ACTIVE_TRAIL = config.getString("Projectile_Settings.Active_Trail", "soul_fire_flame");
			KNOCKBACK = config.getDouble("Projectile_Settings.Knockback", 0.4);
			ENABLE_HEADSHOT = config.getBoolean("Projectile_Settings.Enable_Headshot", true);
		}
	}

	// Death Effect Section
	public static class DeathEffectSection {
		public static boolean ENABLE_DEATH_EFFECTS;
		public static String ACTIVE_DEATH_EFFECT;

		public static void load(FileConfiguration config) {
			ENABLE_DEATH_EFFECTS = config.getBoolean("Death_Effects.Enable_Death_Effects", true);
			ACTIVE_DEATH_EFFECT = config.getString("Death_Effects.Active_Death_Effect", "grid");
		}
	}

	// Combat Section
	public static class CombatSection {
		public static boolean ENABLE_COMBAT_PUNISHMENTS;
		public static int SECONDS_TILL_PLAYER_LEAVES_COMBAT;

		public static void load(FileConfiguration config) {
			ENABLE_COMBAT_PUNISHMENTS = config.getBoolean("Combat_Settings.Enable_Combat_Punishments", true);
			SECONDS_TILL_PLAYER_LEAVES_COMBAT = config.getInt("Combat_Settings.Combat_Timer", 10);
		}
	}
}
