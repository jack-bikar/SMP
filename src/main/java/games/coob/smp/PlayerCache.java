package games.coob.smp;

import games.coob.smp.config.ConfigFile;
import games.coob.smp.tracking.MarkerColor;
import games.coob.smp.tracking.TrackedTarget;
import games.coob.smp.util.LocationUtil;
import lombok.Getter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player data, stored in players/&lt;uuid&gt;.yml.
 * Loaded when first accessed and unloaded when the player quits.
 */
@Getter
public final class PlayerCache extends ConfigFile {

	private static final Map<UUID, PlayerCache> cacheMap = new HashMap<>();

	/** Old shared data file; player data is migrated out of it on first load. */
	private static YamlConfiguration legacyData;

	/** Maximum number of other players a single player can track at once. */
	public static final int MAX_PLAYERS_TO_TRACK = 10;

	private final UUID uniqueId;

	private final String playerName;

	private Location deathLocation;

	private Location portalLocation;

	/** Overworld-side nether portal (when entering from overworld to nether) */
	private Location overworldNetherPortalLocation;

	/** Overworld-side end portal in stronghold (when entering overworld to end) */
	private Location overworldEndPortalLocation;

	/**
	 * Multi-tracking: list of tracked targets (players and/or death location).
	 * Players have customizable colors; death location is always dark red.
	 */
	private final List<TrackedTarget> trackedTargets = new ArrayList<>();

	// Combat punishment state
	private long pvpLockoutExpiry;

	private long debuffExpiry;

	/**
	 * Where to send the player back to after a duel. Kept on disk so players who
	 * disconnect (or a server crash) mid-duel are still returned on their next join.
	 */
	private Location duelReturnLocation;

	private GameMode duelReturnGameMode;

	private PlayerCache(final String name, final UUID uniqueId) {
		super("players/" + uniqueId + ".yml");

		this.playerName = name;
		this.uniqueId = uniqueId;

		loadPlayerData();
	}

	private void loadPlayerData() {
		ConfigurationSection section = getConfig();

		// First load after updating: pull this player's data out of the old data.yml
		boolean migrated = false;
		if (!file.exists()) {
			ConfigurationSection legacy = getLegacySection(uniqueId);
			if (legacy != null) {
				section = legacy;
				migrated = true;
			}
		}

		this.deathLocation = readLocation(section, "Death_Location");
		this.portalLocation = readLocation(section, "Portal_Location");
		this.overworldNetherPortalLocation = readLocation(section, "Overworld_Nether_Portal");
		this.overworldEndPortalLocation = readLocation(section, "Overworld_End_Portal");

		trackedTargets.clear();
		for (Map<?, ?> map : section.getMapList("Tracked_Targets")) {
			Object type = map.get("Type");
			if ("Death".equals(type)) {
				trackedTargets.add(TrackedTarget.death());
			} else if ("Player".equals(type) && map.get("UUID") instanceof String uuidStr) {
				try {
					MarkerColor color = MarkerColor.WHITE;
					if (map.get("Color") instanceof String colorStr) {
						try {
							color = MarkerColor.valueOf(colorStr);
						} catch (IllegalArgumentException ignored) {
						}
					}
					trackedTargets.add(TrackedTarget.player(UUID.fromString(uuidStr), color));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}

		this.pvpLockoutExpiry = section.getLong("PvP_Lockout_Expiry", 0);
		this.debuffExpiry = section.getLong("Debuff_Expiry", 0);

		this.duelReturnLocation = readLocation(section, "Duel_Return.Location");
		String gameMode = section.getString("Duel_Return.GameMode");
		if (gameMode != null) {
			try {
				this.duelReturnGameMode = GameMode.valueOf(gameMode);
			} catch (IllegalArgumentException ignored) {
			}
		}

		if (migrated)
			save();
	}

	/** Reads a location written by this class or by the old Bukkit serialization. */
	private static Location readLocation(ConfigurationSection section, String path) {
		if (section.isString(path))
			return LocationUtil.deserialize(section.getString(path));
		try {
			return section.getLocation(path);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	@Override
	protected void onLoad() {
		// Player data is loaded in loadPlayerData() once uniqueId is set
	}

	@Override
	protected void onSave() {
		getConfig().set("Name", playerName);
		getConfig().set("Death_Location", LocationUtil.serialize(deathLocation));
		getConfig().set("Portal_Location", LocationUtil.serialize(portalLocation));
		getConfig().set("Overworld_Nether_Portal", LocationUtil.serialize(overworldNetherPortalLocation));
		getConfig().set("Overworld_End_Portal", LocationUtil.serialize(overworldEndPortalLocation));

		List<Map<String, Object>> targetList = new ArrayList<>();
		for (TrackedTarget target : trackedTargets) {
			Map<String, Object> map = new HashMap<>();
			map.put("Type", target.getType());
			if (target.isPlayer()) {
				map.put("UUID", target.getTargetUUID().toString());
				map.put("Color", target.getColor().name());
			}
			targetList.add(map);
		}
		getConfig().set("Tracked_Targets", targetList);

		getConfig().set("PvP_Lockout_Expiry", pvpLockoutExpiry > System.currentTimeMillis() ? pvpLockoutExpiry : null);
		getConfig().set("Debuff_Expiry", debuffExpiry > System.currentTimeMillis() ? debuffExpiry : null);

		getConfig().set("Duel_Return", null);
		if (duelReturnLocation != null) {
			getConfig().set("Duel_Return.Location", LocationUtil.serialize(duelReturnLocation));
			getConfig().set("Duel_Return.GameMode", duelReturnGameMode != null ? duelReturnGameMode.name() : null);
		}
	}

	@Override
	public String toString() {
		return "PlayerCache{" + this.playerName + ", " + this.uniqueId + "}";
	}

	public void setDeathLocation(final Location deathLocation) {
		this.deathLocation = deathLocation != null ? deathLocation.clone() : null;
		save();
	}

	public void setPortalLocation(final Location portalLocation) {
		this.portalLocation = portalLocation;
		save();
	}

	public void setOverworldNetherPortalLocation(final Location overworldNetherPortalLocation) {
		this.overworldNetherPortalLocation = overworldNetherPortalLocation;
		save();
	}

	public void setOverworldEndPortalLocation(final Location overworldEndPortalLocation) {
		this.overworldEndPortalLocation = overworldEndPortalLocation;
		save();
	}

	public void setPvpLockoutExpiry(final long pvpLockoutExpiry) {
		this.pvpLockoutExpiry = pvpLockoutExpiry;
		save();
	}

	public void setDebuffExpiry(final long debuffExpiry) {
		this.debuffExpiry = debuffExpiry;
		save();
	}

	public void setDuelReturn(@Nullable final Location location, @Nullable final GameMode gameMode) {
		this.duelReturnLocation = location != null ? location.clone() : null;
		this.duelReturnGameMode = location != null ? gameMode : null;
		save();
	}

	public boolean hasDuelReturn() {
		return duelReturnLocation != null;
	}

	// -------------------------------------------------------------------------
	// Multi-tracking methods
	// -------------------------------------------------------------------------

	/**
	 * Number of other players currently tracked (excluding death location).
	 */
	public int getTrackedPlayerCount() {
		return (int) trackedTargets.stream().filter(TrackedTarget::isPlayer).count();
	}

	/**
	 * Whether this cache can track more players (under
	 * {@link #MAX_PLAYERS_TO_TRACK}).
	 */
	public boolean canTrackMorePlayers() {
		return getTrackedPlayerCount() < MAX_PLAYERS_TO_TRACK;
	}

	/**
	 * Add a player to track with a specific color.
	 * Does not check limit; callers should use {@link #canTrackMorePlayers()}
	 * first.
	 */
	public void addTrackedPlayer(UUID playerUUID, MarkerColor color) {
		// Remove existing if already tracking
		trackedTargets.removeIf(t -> t.isPlayer() && playerUUID.equals(t.getTargetUUID()));
		trackedTargets.add(TrackedTarget.player(playerUUID, color));
		save();
	}

	/**
	 * Add a player to track with the next default color.
	 */
	public void addTrackedPlayer(UUID playerUUID) {
		addTrackedPlayer(playerUUID, MarkerColor.getDefault(getTrackedPlayerCount()));
	}

	/**
	 * Start tracking death location (always dark red).
	 */
	public void startTrackingDeath() {
		if (!isTrackingDeath()) {
			trackedTargets.add(TrackedTarget.death());
			save();
		}
	}

	/**
	 * Stop tracking death location.
	 */
	public void stopTrackingDeath() {
		if (trackedTargets.removeIf(TrackedTarget::isDeath))
			save();
	}

	/**
	 * Check if currently tracking death location.
	 */
	public boolean isTrackingDeath() {
		return trackedTargets.stream().anyMatch(TrackedTarget::isDeath);
	}

	/**
	 * Stop tracking a specific player.
	 */
	public void removeTrackedPlayer(UUID playerUUID) {
		if (trackedTargets.removeIf(t -> t.isPlayer() && playerUUID.equals(t.getTargetUUID())))
			save();
	}

	/**
	 * Stop all tracking.
	 */
	public void clearAllTracking() {
		trackedTargets.clear();
		save();
	}

	/**
	 * Get tracked target for a specific player UUID.
	 */
	@Nullable
	public TrackedTarget getTrackedTarget(UUID playerUUID) {
		for (TrackedTarget target : trackedTargets) {
			if (target.isPlayer() && playerUUID.equals(target.getTargetUUID()))
				return target;
		}
		return null;
	}

	/**
	 * Check if tracking anything.
	 */
	public boolean isTracking() {
		return !trackedTargets.isEmpty();
	}

	// -------------------------------------------------------------------------
	// Static access
	// -------------------------------------------------------------------------

	/**
	 * Return or create the player cache for the given player
	 */
	public static PlayerCache from(final Player player) {
		return cacheMap.computeIfAbsent(player.getUniqueId(), id -> new PlayerCache(player.getName(), id));
	}

	/**
	 * Save and forget a player's cache (call when they quit).
	 */
	public static void unload(final UUID uniqueId) {
		PlayerCache cache = cacheMap.remove(uniqueId);
		if (cache != null)
			cache.save();
	}

	/**
	 * Save every loaded cache immediately (call on plugin disable).
	 */
	public static void saveAllNow() {
		for (PlayerCache cache : cacheMap.values())
			cache.saveNow();
		cacheMap.clear();
	}

	private static ConfigurationSection getLegacySection(UUID uniqueId) {
		if (legacyData == null) {
			File legacyFile = new File(SMPPlugin.getInstance().getDataFolder(), "data.yml");
			legacyData = legacyFile.exists() ? YamlConfiguration.loadConfiguration(legacyFile) : new YamlConfiguration();
		}
		return legacyData.getConfigurationSection("Players." + uniqueId);
	}
}
