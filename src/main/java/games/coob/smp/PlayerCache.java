package games.coob.smp;

import games.coob.smp.config.ConfigFile;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import javax.annotation.Nullable;
import java.util.HashMap;
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

	@Getter
	private String trackingLocation;

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
		this.trackingLocation = getConfig().getString(path + "Tracking_Location");
		String uuidStr = getConfig().getString(path + "Track_Player");
		if (uuidStr != null) {
			try {
				this.targetByUUID = UUID.fromString(uuidStr);
			} catch (IllegalArgumentException e) {
				this.targetByUUID = null;
			}
		}
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
		getConfig().set(path + "Tracking_Location", trackingLocation);
		getConfig().set(path + "Track_Player", targetByUUID != null ? targetByUUID.toString() : null);
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

	public void setTrackingLocation(final String trackingLocation) {
		this.trackingLocation = trackingLocation;

		save();
	}

	public void setTargetByUUID(final UUID targetByUUID) {
		this.targetByUUID = targetByUUID;

		save();
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
