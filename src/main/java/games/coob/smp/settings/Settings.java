package games.coob.smp.settings;

import games.coob.smp.SMPPlugin;
import games.coob.smp.config.ConfigFile;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

		// Older configs had a single MOTD_Text; keep it as the first line instead of the new default
		boolean changed = false;
		if (config.isString("MOTD.MOTD_Text") && !config.contains("MOTD.Lines")) {
			config.set("MOTD.Lines", List.of(config.getString("MOTD.MOTD_Text")));
			changed = true;
		}
		changed |= addMissingDefaults();

		// New defaults only replace values that were still on the old default
		int version = config.getInt("Version", 1);
		if (version < 2) {
			changed |= replaceOldDefault(config, "Projectile_Settings.Enable_Trails", true, false);
			changed |= replaceOldDefault(config, "Death_Effects.Enable_Death_Effects", true, false);
			changed |= replaceOldDefault(config, "TP.Enable_TP", true, false);
		}
		if (version < 3) {
			changed |= replaceOldDefault(config, "Locator_Toggle.Enable_Locator_Bar", false, true);
			changed |= replaceOldDefault(config, "Duel.Arena_Mode", "NATURAL", "RANDOM");
		}
		if (version < 4) {
			changed |= replaceOldDefault(config, "Death_Storage.Storage_Material", "CHEST", "BODY");
			// Options from older versions that no longer do anything
			for (String dead : DEAD_KEYS) {
				if (config.contains(dead)) {
					config.set(dead, null);
					changed = true;
				}
			}
			config.set("Version", 4);
			SMPPlugin.getInstance().getLogger().info("Updated settings.yml to the latest defaults.");
			changed = true;
		}
		if (changed) {
			save();
		}

		DeathStorageSection.load(config);
		LocatorSection.load(config);
		ProjectileSection.load(config);
		DeathEffectSection.load(config);
		MotdSection.load(config);
		DeathMessageSection.load(config);
		NicknameSection.load(config);
		CombatSection.load(config);
		TpSection.load(config);
		DuelSection.load(config);
	}

	private static boolean replaceOldDefault(FileConfiguration config, String path, Object oldDefault, Object newDefault) {
		Object current = config.get(path);
		boolean matches = current instanceof String text && oldDefault instanceof String old
				? text.equalsIgnoreCase(old)
				: oldDefault.equals(current);
		if (current != null && !matches)
			return false;
		config.set(path, newDefault);
		SMPPlugin.getInstance().getLogger().info("settings.yml: " + path + " changed to the new default: " + newDefault);
		return true;
	}

	/** Settings from versions 1-3 that were removed. */
	private static final List<String> DEAD_KEYS = List.of(
			"Command_Aliases", "Locale", "Prefix", "Log_Lag_Over_Milis", "Debug",
			"MOTD.MOTD_Text",
			"Combat_Settings.PvP_Lockout.Can_Take_Damage",
			"Duel.Border.Start_Radius", "Duel.Border.End_Radius", "Duel.Border.Shrink_Time_Seconds",
			"Duel.Border.Warning_Distance", "Duel.Border.Knockback_Strength", "Duel.Border.Use_World_Border",
			"Duel.Border.World_Border",
			"Duel.Loot.Loot_Phase_Seconds", "Duel.Loot.Winner_Keeps_Inventory",
			"Duel.Cleanup.Unload_Natural_Chunks",
			"Duel.Queue");

	// Death Storage Section
	public static class DeathStorageSection {
		public static boolean ENABLE_DEATH_STORAGE;
		/** Storage_Material: BODY - the player's body holds the items instead of a chest block. */
		public static boolean USE_BODY;
		/** Block used when not using bodies. */
		public static Material STORAGE_MATERIAL;
		public static String HOLOGRAM_TEXT;
		public static int HOLOGRAM_VISIBLE_RANGE;

		public static void load(FileConfiguration config) {
			ENABLE_DEATH_STORAGE = config.getBoolean("Death_Storage.Enable_Death_Storage", true);
			String materialName = config.getString("Death_Storage.Storage_Material", "BODY");
			USE_BODY = materialName.equalsIgnoreCase("BODY");
			STORAGE_MATERIAL = USE_BODY ? null : Material.matchMaterial(materialName);
			if (STORAGE_MATERIAL == null || !STORAGE_MATERIAL.isBlock()) {
				STORAGE_MATERIAL = Material.CHEST;
			}
			HOLOGRAM_TEXT = config.getString("Death_Storage.Hologram_Text", "&6{player}'s loot");
			HOLOGRAM_VISIBLE_RANGE = Math.max(1, config.getInt("Death_Storage.Hologram_Visible_Range", 20));
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
			ENABLE_LOCATOR_BAR = config.getBoolean("Locator_Toggle.Enable_Locator_Bar", true);
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
			ENABLE_TRAILS = config.getBoolean("Projectile_Settings.Enable_Trails", false);
			ACTIVE_TRAIL = config.getString("Projectile_Settings.Active_Trail", "soul_fire_flame");
			KNOCKBACK = config.getDouble("Projectile_Settings.Knockback", 0.4);
			ENABLE_HEADSHOT = config.getBoolean("Projectile_Settings.Enable_Headshot", true);
		}
	}

	// Death Effect Section
	public static class DeathEffectSection {
		public static boolean ENABLE_DEATH_EFFECTS;
		public static String ACTIVE_DEATH_EFFECT;
		public static int DURATION_SECONDS;

		public static void load(FileConfiguration config) {
			ENABLE_DEATH_EFFECTS = config.getBoolean("Death_Effects.Enable_Death_Effects", false);
			ACTIVE_DEATH_EFFECT = config.getString("Death_Effects.Active_Death_Effect", "grid");
			DURATION_SECONDS = Math.max(1, config.getInt("Death_Effects.Duration_Seconds", 3));
		}
	}

	// Death message Section
	public static class DeathMessageSection {
		public static boolean ENABLED;

		public static void load(FileConfiguration config) {
			ENABLED = config.getBoolean("Death_Messages.Enabled", true);
		}
	}

	// Nickname Section
	public static class NicknameSection {
		public static boolean ENABLED;
		public static int MAX_LENGTH;
		public static boolean SHOW_ABOVE_HEAD;

		public static void load(FileConfiguration config) {
			ENABLED = config.getBoolean("Nicknames.Enabled", true);
			SHOW_ABOVE_HEAD = config.getBoolean("Nicknames.Show_Above_Head", false);
			MAX_LENGTH = Math.clamp(config.getInt("Nicknames.Max_Length", 16), 3, 32);
		}
	}

	// MOTD Section
	public static class MotdSection {
		public static boolean ENABLE_MOTD;
		public static List<String> LINES;

		public static void load(FileConfiguration config) {
			ENABLE_MOTD = config.getBoolean("MOTD.Enable_MOTD", true);
			List<String> lines = new ArrayList<>(config.getStringList("MOTD.Lines"));
			// Older configs used a single MOTD_Text value
			if (lines.isEmpty() && config.isString("MOTD.MOTD_Text")) {
				lines.add(config.getString("MOTD.MOTD_Text"));
			}
			LINES = lines.size() > 2 ? lines.subList(0, 2) : lines;
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
				PUNISHMENT_TYPE = PunishmentType.valueOf(punishmentTypeStr.toUpperCase(Locale.ROOT));
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
			ENABLE_TP = config.getBoolean("TP.Enable_TP", false);
		}
	}

	// Duel Section
	public static class DuelSection {
		public static boolean ENABLE_DUELS;
		public static int REQUEST_TIMEOUT_SECONDS;
		public static int COUNTDOWN_SECONDS;
		public static ArenaMode ARENA_MODE;

		// Team duels
		public static boolean TEAMS_ENABLED;
		public static int MAX_TEAM_SIZE;
		public static int TEAM_INVITE_TIMEOUT_SECONDS;
		public static boolean ALLOW_UNEVEN_TEAMS;

		// Border settings
		public static boolean BORDER_ENABLED;
		public static int BORDER_RADIUS;
		public static double BORDER_DAMAGE_PER_SECOND;

		// Natural arena settings
		public static int NATURAL_SEARCH_RADIUS;
		public static int NATURAL_MIN_PLAYER_DISTANCE;
		public static int NATURAL_MAX_SEARCH_ATTEMPTS;
		public static List<String> NATURAL_BANNED_BIOMES;
		public static List<String> NATURAL_BANNED_BLOCKS;

		// Loot settings
		public static LootMode LOOT_MODE;

		// End-of-duel return countdown (seconds before auto-teleport)
		public static int END_RETURN_COUNTDOWN_SECONDS;

		// Fights longer than this end in a draw (0 = no limit)
		public static int MAX_FIGHT_MINUTES;

		// Cleanup settings
		public static boolean CLEANUP_REMOVE_PLACED_BLOCKS;
		public static boolean CLEANUP_REMOVE_DROPPED_ITEMS;
		public static boolean CLEANUP_REMOVE_ENTITIES;

		public static void load(FileConfiguration config) {
			ENABLE_DUELS = config.getBoolean("Duel.Enable_Duels", true);
			REQUEST_TIMEOUT_SECONDS = config.getInt("Duel.Request_Timeout_Seconds", 60);
			COUNTDOWN_SECONDS = config.getInt("Duel.Countdown_Seconds", 5);

			String arenaModeStr = config.getString("Duel.Arena_Mode", "RANDOM");
			try {
				ARENA_MODE = ArenaMode.valueOf(arenaModeStr.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				ARENA_MODE = ArenaMode.RANDOM;
			}

			// Teams
			TEAMS_ENABLED = config.getBoolean("Duel.Teams.Enabled", true);
			MAX_TEAM_SIZE = Math.clamp(config.getInt("Duel.Teams.Max_Team_Size", 4), 1, 10);
			TEAM_INVITE_TIMEOUT_SECONDS = Math.max(5, config.getInt("Duel.Teams.Invite_Timeout_Seconds", 60));
			ALLOW_UNEVEN_TEAMS = config.getBoolean("Duel.Teams.Allow_Uneven_Teams", false);

			// Border
			BORDER_ENABLED = config.getBoolean("Duel.Border.Enabled", true);
			BORDER_RADIUS = Math.max(5, config.getInt("Duel.Border.Radius", 30));
			BORDER_DAMAGE_PER_SECOND = config.getDouble("Duel.Border.Damage_Per_Second", 2.0);

			// Natural arena
			NATURAL_SEARCH_RADIUS = Math.max(100, config.getInt("Duel.Natural_Arena.Search_Radius", 5000));
			NATURAL_MIN_PLAYER_DISTANCE = Math.max(4, config.getInt("Duel.Natural_Arena.Min_Player_Distance", 30));
			NATURAL_MAX_SEARCH_ATTEMPTS = Math.max(1, config.getInt("Duel.Natural_Arena.Max_Search_Attempts", 10));
			NATURAL_BANNED_BIOMES = upperCase(config.getStringList("Duel.Natural_Arena.Banned_Biomes"));
			NATURAL_BANNED_BLOCKS = upperCase(config.getStringList("Duel.Natural_Arena.Banned_Blocks"));

			// Loot (LOOT_PHASE from older configs behaves like DROP_ITEMS)
			String lootModeStr = config.getString("Duel.Loot.Mode", "KEEP_INVENTORY").toUpperCase(Locale.ROOT);
			LOOT_MODE = lootModeStr.equals("DROP_ITEMS") || lootModeStr.equals("LOOT_PHASE")
					? LootMode.DROP_ITEMS
					: LootMode.KEEP_INVENTORY;

			// End return countdown
			END_RETURN_COUNTDOWN_SECONDS = Math.max(1, config.getInt("Duel.End_Return_Countdown_Seconds", 15));
			MAX_FIGHT_MINUTES = Math.max(0, config.getInt("Duel.Max_Fight_Minutes", 10));

			// Cleanup
			CLEANUP_REMOVE_PLACED_BLOCKS = config.getBoolean("Duel.Cleanup.Remove_Placed_Blocks", true);
			CLEANUP_REMOVE_DROPPED_ITEMS = config.getBoolean("Duel.Cleanup.Remove_Dropped_Items", true);
			CLEANUP_REMOVE_ENTITIES = config.getBoolean("Duel.Cleanup.Remove_Entities", true);
		}

		private static List<String> upperCase(List<String> values) {
			List<String> result = new ArrayList<>(values.size());
			for (String value : values) {
				result.add(value.toUpperCase(Locale.ROOT));
			}
			return result;
		}

		public enum ArenaMode {
			NATURAL,
			CREATED,
			RANDOM
		}

		public enum LootMode {
			DROP_ITEMS,
			KEEP_INVENTORY
		}
	}
}
