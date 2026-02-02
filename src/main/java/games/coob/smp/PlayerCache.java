package games.coob.smp;

import games.coob.smp.config.ConfigFile;
import games.coob.smp.tracking.MarkerColor;
import games.coob.smp.tracking.TrackedTarget;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
public final class PlayerCache extends ConfigFile {

	private static final Map<UUID, PlayerCache> cacheMap = new HashMap<>();

	private final UUID uniqueId;

	private final String playerName;

	@Getter
	private Location deathLocation;

	@Getter
	private Location portalLocation;

	/** Overworld-side nether portal (when entering from overworld to nether) */
	@Getter
	private Location overworldNetherPortalLocation;

	/** Overworld-side end portal in stronghold (when entering overworld to end) */
	@Getter
	private Location overworldEndPortalLocation;

	/** Maximum number of other players a single player can track at once. */
	public static final int MAX_PLAYERS_TO_TRACK = 10;

	/**
	 * Multi-tracking: list of tracked targets (players and/or death location).
	 * Players have customizable colors; death location is always dark red.
	 */
	@Getter
	private final List<TrackedTarget> trackedTargets = new ArrayList<>();

	/** Legacy field for backward compatibility - use trackedTargets instead */
	@Deprecated
	@Getter
	private String trackingLocation;

	/** Legacy field for backward compatibility - use trackedTargets instead */
	@Deprecated
	@Getter
	private UUID targetByUUID;

	@Getter
	@Setter
	private Inventory deathChestInventory;

	@Getter
	@Setter
	private int secondsAfterDamage;

	@Getter
	@Setter
	private boolean drawingAxe;

	@Getter
	@Setter
	private boolean inCombat;

	// Combat punishment state
	@Getter
	@Setter
	private long pvpLockoutExpiry;

	@Getter
	@Setter
	private long debuffExpiry;

	/**
	 * Transient: cached portal location for cross-dimension tracking.
	 * Not persisted to disk - recalculated on dimension change.
	 * 
	 * @deprecated Use TrackedTarget.getCachedPortalTarget() instead
	 */
	@Deprecated
	@Getter
	@Setter
	private Location cachedPortalTarget;

	//
	// Store any custom saveable data here
	//

	/*
	 * Creates a new player cache (see the bottom)
	 */
	private PlayerCache(final String name, final UUID uniqueId) {
		super("data.yml");

		this.playerName = name;
		this.uniqueId = uniqueId;

		// Load player-specific data after uniqueId is set
		loadPlayerData();
	}

	/**
	 * Load player-specific data from the config file
	 */
	private void loadPlayerData() {
		if (uniqueId == null)
			return;

		String path = "Players." + uniqueId.toString() + ".";
		this.deathLocation = getConfig().getLocation(path + "Death_Location");
		this.portalLocation = getConfig().getLocation(path + "Portal_Location");
		this.overworldNetherPortalLocation = getConfig().getLocation(path + "Overworld_Nether_Portal");
		this.overworldEndPortalLocation = getConfig().getLocation(path + "Overworld_End_Portal");

		// Load tracked targets (new multi-tracking system)
		trackedTargets.clear();
		if (getConfig().contains(path + "Tracked_Targets")) {
			List<?> targetList = getConfig().getList(path + "Tracked_Targets");
			if (targetList != null) {
				for (Object obj : targetList) {
					if (obj instanceof Map) {
						@SuppressWarnings("unchecked")
						Map<String, Object> map = (Map<String, Object>) obj;
						String type = (String) map.get("Type");
						if ("Death".equals(type)) {
							trackedTargets.add(TrackedTarget.death());
						} else if ("Player".equals(type)) {
							String uuidStr = (String) map.get("UUID");
							String colorStr = (String) map.get("Color");
							if (uuidStr != null) {
								try {
									UUID targetUUID = UUID.fromString(uuidStr);
									MarkerColor color = MarkerColor.WHITE;
									if (colorStr != null) {
										try {
											color = MarkerColor.valueOf(colorStr);
										} catch (IllegalArgumentException ignored) {
										}
									}
									trackedTargets.add(TrackedTarget.player(targetUUID, color));
								} catch (IllegalArgumentException ignored) {
								}
							}
						}
					}
				}
			}
		}

		// Legacy loading (backward compatibility)
		this.trackingLocation = getConfig().getString(path + "Tracking_Location");
		String uuidStr = getConfig().getString(path + "Track_Player");
		if (uuidStr != null) {
			try {
				this.targetByUUID = UUID.fromString(uuidStr);
			} catch (IllegalArgumentException e) {
				this.targetByUUID = null;
			}
		}

		// Load combat punishment state
		this.pvpLockoutExpiry = getConfig().getLong(path + "PvP_Lockout_Expiry", 0);
		this.debuffExpiry = getConfig().getLong(path + "Debuff_Expiry", 0);
	}

	/**
	 * Automatically called when loading data from disk.
	 * Note: This is called before uniqueId is set, so we load player data
	 * separately.
	 */
	@Override
	protected void onLoad() {
		// Don't load player-specific data here since uniqueId isn't set yet
		// Player data is loaded in loadPlayerData() after uniqueId is set
	}

	@Override
	protected void onSave() {
		String path = "Players." + uniqueId.toString() + ".";
		getConfig().set(path + "Death_Location", deathLocation);
		getConfig().set(path + "Portal_Location", portalLocation);
		getConfig().set(path + "Overworld_Nether_Portal", overworldNetherPortalLocation);
		getConfig().set(path + "Overworld_End_Portal", overworldEndPortalLocation);

		// Save tracked targets (new multi-tracking system)
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
		getConfig().set(path + "Tracked_Targets", targetList);

		// Legacy fields (for backward compatibility, will be removed later)
		getConfig().set(path + "Tracking_Location", trackingLocation);
		getConfig().set(path + "Track_Player", targetByUUID != null ? targetByUUID.toString() : null);

		// Save combat punishment state
		getConfig().set(path + "PvP_Lockout_Expiry", pvpLockoutExpiry);
		getConfig().set(path + "Debuff_Expiry", debuffExpiry);
	}

	/**
	 * Return player from cache if online or null otherwise
	 *
	 * @return
	 */
	@Nullable
	public Player toPlayer() {
		return Bukkit.getPlayer(this.uniqueId);
	}

	/**
	 * Remove this cached data from memory if it exists
	 */
	public void removeFromMemory() {
		synchronized (cacheMap) {
			cacheMap.remove(this.uniqueId);
		}
	}

	@Override
	public String toString() {
		return "PlayerCache{" + this.playerName + ", " + this.uniqueId + "}";
	}

	public void setDeathLocation(final Location deathLocation) {
		this.deathLocation = deathLocation;

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

	@Deprecated
	public void setTrackingLocation(final String trackingLocation) {
		this.trackingLocation = trackingLocation;
		save();
	}

	@Deprecated
	public void setTargetByUUID(final UUID targetByUUID) {
		this.targetByUUID = targetByUUID;
		save();
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
		int colorIndex = (int) trackedTargets.stream().filter(TrackedTarget::isPlayer).count();
		addTrackedPlayer(playerUUID, MarkerColor.getDefault(colorIndex));
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
		trackedTargets.removeIf(TrackedTarget::isDeath);
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
		trackedTargets.removeIf(t -> t.isPlayer() && playerUUID.equals(t.getTargetUUID()));
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
		return trackedTargets.stream()
				.filter(t -> t.isPlayer() && playerUUID.equals(t.getTargetUUID()))
				.findFirst()
				.orElse(null);
	}

	/**
	 * Get the death tracking target if active.
	 */
	@Nullable
	public TrackedTarget getDeathTarget() {
		return trackedTargets.stream()
				.filter(TrackedTarget::isDeath)
				.findFirst()
				.orElse(null);
	}

	/**
	 * Get all tracked player UUIDs.
	 */
	public List<UUID> getTrackedPlayerUUIDs() {
		return trackedTargets.stream()
				.filter(TrackedTarget::isPlayer)
				.map(TrackedTarget::getTargetUUID)
				.toList();
	}

	/**
	 * Check if tracking anything.
	 */
	public boolean isTracking() {
		return !trackedTargets.isEmpty();
	}

	/**
	 * Change the color for a tracked player.
	 */
	public void setTrackedPlayerColor(UUID playerUUID, MarkerColor color) {
		TrackedTarget target = getTrackedTarget(playerUUID);
		if (target != null) {
			target.setColor(color);
			save();
		}
	}

	/*
	 * -----------------------------------------------------------------------------
	 * --
	 */
	/* Static access */
	/*
	 * -----------------------------------------------------------------------------
	 * --
	 */

	/**
	 * Return or create new player cache for the given player
	 *
	 * @param player
	 * @return
	 */
	public static PlayerCache from(final Player player) {
		synchronized (cacheMap) {
			final UUID uniqueId = player.getUniqueId();
			final String playerName = player.getName();

			PlayerCache cache = cacheMap.get(uniqueId);

			if (cache == null) {
				cache = new PlayerCache(playerName, uniqueId);

				cacheMap.put(uniqueId, cache);
			}

			return cache;
		}
	}

	/**
	 * Clear the entire cache map
	 */
	public static void clearCache() {
		synchronized (cacheMap) {
			cacheMap.clear();
		}
	}
}
