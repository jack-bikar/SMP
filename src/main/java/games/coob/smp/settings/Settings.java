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
		LocatorSection.load(config);
		ProjectileSection.load(config);
		DeathEffectSection.load(config);
		CombatSection.load(config);
		TpSection.load(config);
		DuelSection.load(config);
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

	// Locator Section
	public static class LocatorSection {
		public static boolean ENABLE_LOCATOR_BAR;
		public static String ALLOWED_ENVIRONEMENTS;
		// ENABLE_TRACKING is the inverse - if locator bar is enabled, custom tracking
		// is disabled
		public static boolean ENABLE_TRACKING;

		public static void load(FileConfiguration config) {
			ENABLE_LOCATOR_BAR = config.getBoolean("Locator_Toggle.Enable_Locator_Bar", false);
			ALLOWED_ENVIRONEMENTS = config.getString("Locator_Toggle.Allowed_Environements", "all");
			// ENABLE_TRACKING is the inverse - if locator bar is enabled, custom tracking
			// is disabled
			ENABLE_TRACKING = !ENABLE_LOCATOR_BAR;
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
		public static PunishmentType PUNISHMENT_TYPE;

		// Ghost body settings
		public static int GHOST_BODY_DURATION;
		public static boolean GHOST_BODY_USE_PLAYER_HEALTH;
		public static boolean GHOST_BODY_USE_PLAYER_ARMOR;
		public static boolean GHOST_BODY_FIGHT_BACK;

		// PvP lockout settings
		public static int PVP_LOCKOUT_DURATION_MINUTES;
		public static boolean PVP_LOCKOUT_CAN_TAKE_DAMAGE;

		// Debuff settings
		public static int DEBUFF_DURATION_MINUTES;
		public static int DEBUFF_SLOWNESS_LEVEL;
		public static int DEBUFF_WEAKNESS_LEVEL;
		public static int DEBUFF_MINING_FATIGUE_LEVEL;

		// Random item drop settings
		public static int RANDOM_ITEM_DROP_AMOUNT;
		public static boolean RANDOM_ITEM_DROP_INCLUDE_ARMOR;
		public static boolean RANDOM_ITEM_DROP_INCLUDE_HOTBAR;

		public static void load(FileConfiguration config) {
			ENABLE_COMBAT_PUNISHMENTS = config.getBoolean("Combat_Settings.Enable_Combat_Punishments", true);
			SECONDS_TILL_PLAYER_LEAVES_COMBAT = config.getInt("Combat_Settings.Combat_Timer", 10);

			String punishmentTypeStr = config.getString("Combat_Settings.Punishment_Type", "GHOST_BODY");
			try {
				PUNISHMENT_TYPE = PunishmentType.valueOf(punishmentTypeStr.toUpperCase());
			} catch (IllegalArgumentException e) {
				PUNISHMENT_TYPE = PunishmentType.GHOST_BODY;
			}

			// Ghost body
			GHOST_BODY_DURATION = config.getInt("Combat_Settings.Ghost_Body.Duration", 30);
			GHOST_BODY_USE_PLAYER_HEALTH = config.getBoolean("Combat_Settings.Ghost_Body.Use_Player_Health", true);
			GHOST_BODY_USE_PLAYER_ARMOR = config.getBoolean("Combat_Settings.Ghost_Body.Use_Player_Armor", true);
			GHOST_BODY_FIGHT_BACK = config.getBoolean("Combat_Settings.Ghost_Body.Fight_Back", false);

			// PvP lockout
			PVP_LOCKOUT_DURATION_MINUTES = config.getInt("Combat_Settings.PvP_Lockout.Duration_Minutes", 15);
			PVP_LOCKOUT_CAN_TAKE_DAMAGE = config.getBoolean("Combat_Settings.PvP_Lockout.Can_Take_Damage", true);

			// Debuff
			DEBUFF_DURATION_MINUTES = config.getInt("Combat_Settings.Debuff.Duration_Minutes", 10);
			DEBUFF_SLOWNESS_LEVEL = config.getInt("Combat_Settings.Debuff.Slowness_Level", 2);
			DEBUFF_WEAKNESS_LEVEL = config.getInt("Combat_Settings.Debuff.Weakness_Level", 2);
			DEBUFF_MINING_FATIGUE_LEVEL = config.getInt("Combat_Settings.Debuff.Mining_Fatigue_Level", 2);

			// Random item drop
			RANDOM_ITEM_DROP_AMOUNT = config.getInt("Combat_Settings.Random_Item_Drop.Amount", 10);
			RANDOM_ITEM_DROP_INCLUDE_ARMOR = config.getBoolean("Combat_Settings.Random_Item_Drop.Include_Armor", true);
			RANDOM_ITEM_DROP_INCLUDE_HOTBAR = config.getBoolean("Combat_Settings.Random_Item_Drop.Include_Hotbar",
					true);
		}

		public enum PunishmentType {
			INSTANT_DEATH,
			GHOST_BODY,
			RANDOM_ITEM_DROP,
			PVP_LOCKOUT,
			DEBUFF,
			NONE
		}
	}

	// TP request feature
	public static class TpSection {
		public static boolean ENABLE_TP;

		public static void load(FileConfiguration config) {
			ENABLE_TP = config.getBoolean("TP.Enable_TP", true);
		}
	}

	// Duel Section
	public static class DuelSection {
		public static boolean ENABLE_DUELS;
		public static int REQUEST_TIMEOUT_SECONDS;
		public static int COUNTDOWN_SECONDS;
		public static ArenaMode ARENA_MODE;

		// Border settings
		public static int BORDER_START_RADIUS;
		public static int BORDER_END_RADIUS;
		public static int BORDER_SHRINK_TIME_SECONDS;
		public static int BORDER_WARNING_DISTANCE;
		public static double BORDER_KNOCKBACK_STRENGTH;
		public static double BORDER_DAMAGE_PER_SECOND;
		public static String BORDER_PARTICLE_TYPE;

		// Natural arena settings
		public static int NATURAL_SEARCH_RADIUS;
		public static int NATURAL_MIN_PLAYER_DISTANCE;
		public static int NATURAL_MAX_SEARCH_ATTEMPTS;
		public static java.util.List<String> NATURAL_BANNED_BIOMES;
		public static java.util.List<String> NATURAL_BANNED_BLOCKS;

		// Loot settings
		public static LootMode LOOT_MODE;
		public static int LOOT_PHASE_SECONDS;
		public static boolean WINNER_KEEPS_INVENTORY;

		// Cleanup settings
		public static boolean CLEANUP_REMOVE_PLACED_BLOCKS;
		public static boolean CLEANUP_REMOVE_DROPPED_ITEMS;
		public static boolean CLEANUP_REMOVE_ENTITIES;
		public static boolean CLEANUP_UNLOAD_NATURAL_CHUNKS;

		// Queue settings
		public static int QUEUE_MIN_PLAYERS;
		public static int QUEUE_MATCH_CHECK_INTERVAL;

		public static void load(FileConfiguration config) {
			ENABLE_DUELS = config.getBoolean("Duel.Enable_Duels", true);
			REQUEST_TIMEOUT_SECONDS = config.getInt("Duel.Request_Timeout_Seconds", 60);
			COUNTDOWN_SECONDS = config.getInt("Duel.Countdown_Seconds", 5);

			String arenaModeStr = config.getString("Duel.Arena_Mode", "NATURAL");
			try {
				ARENA_MODE = ArenaMode.valueOf(arenaModeStr.toUpperCase());
			} catch (IllegalArgumentException e) {
				ARENA_MODE = ArenaMode.NATURAL;
			}

			// Border
			BORDER_START_RADIUS = config.getInt("Duel.Border.Start_Radius", 50);
			BORDER_END_RADIUS = config.getInt("Duel.Border.End_Radius", 10);
			BORDER_SHRINK_TIME_SECONDS = config.getInt("Duel.Border.Shrink_Time_Seconds", 180);
			BORDER_WARNING_DISTANCE = config.getInt("Duel.Border.Warning_Distance", 5);
			BORDER_KNOCKBACK_STRENGTH = config.getDouble("Duel.Border.Knockback_Strength", 1.5);
			BORDER_DAMAGE_PER_SECOND = config.getDouble("Duel.Border.Damage_Per_Second", 2.0);
			BORDER_PARTICLE_TYPE = config.getString("Duel.Border.Particle_Type", "FLAME");

			// Natural arena
			NATURAL_SEARCH_RADIUS = config.getInt("Duel.Natural_Arena.Search_Radius", 5000);
			NATURAL_MIN_PLAYER_DISTANCE = config.getInt("Duel.Natural_Arena.Min_Player_Distance", 30);
			NATURAL_MAX_SEARCH_ATTEMPTS = config.getInt("Duel.Natural_Arena.Max_Search_Attempts", 50);
			NATURAL_BANNED_BIOMES = config.getStringList("Duel.Natural_Arena.Banned_Biomes");
			NATURAL_BANNED_BLOCKS = config.getStringList("Duel.Natural_Arena.Banned_Blocks");

			// Loot
			String lootModeStr = config.getString("Duel.Loot.Mode", "LOOT_PHASE");
			try {
				LOOT_MODE = LootMode.valueOf(lootModeStr.toUpperCase());
			} catch (IllegalArgumentException e) {
				LOOT_MODE = LootMode.LOOT_PHASE;
			}
			LOOT_PHASE_SECONDS = config.getInt("Duel.Loot.Loot_Phase_Seconds", 30);
			WINNER_KEEPS_INVENTORY = config.getBoolean("Duel.Loot.Winner_Keeps_Inventory", true);

			// Cleanup
			CLEANUP_REMOVE_PLACED_BLOCKS = config.getBoolean("Duel.Cleanup.Remove_Placed_Blocks", true);
			CLEANUP_REMOVE_DROPPED_ITEMS = config.getBoolean("Duel.Cleanup.Remove_Dropped_Items", true);
			CLEANUP_REMOVE_ENTITIES = config.getBoolean("Duel.Cleanup.Remove_Entities", true);
			CLEANUP_UNLOAD_NATURAL_CHUNKS = config.getBoolean("Duel.Cleanup.Unload_Natural_Chunks", true);

			// Queue
			QUEUE_MIN_PLAYERS = config.getInt("Duel.Queue.Min_Players_To_Match", 2);
			QUEUE_MATCH_CHECK_INTERVAL = config.getInt("Duel.Queue.Match_Check_Interval_Seconds", 10);
		}

		public enum ArenaMode {
			NATURAL,
			CREATED,
			RANDOM
		}

		public enum LootMode {
			DROP_ITEMS,
			KEEP_INVENTORY,
			LOOT_PHASE
		}
	}
}
