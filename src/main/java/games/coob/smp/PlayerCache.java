package games.coob.smp;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.mineacademy.fo.constants.FoConstants;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.YamlConfig;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
public final class PlayerCache extends YamlConfig {

	private static final Map<UUID, PlayerCache> cacheMap = new HashMap<>();

	private final UUID uniqueId;

	private final String playerName;

	@Getter
	private Location deathLocation;

	@Getter
	private Location portalLocation;

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
		setPathPrefix("Players." + uniqueId.toString());

		this.playerName = name;
		this.uniqueId = uniqueId;

		this.loadConfiguration(NO_DEFAULT, FoConstants.File.DATA);
	}

	/**
	 * Automatically called when loading data from disk.
	 *
	 * @see org.mineacademy.fo.settings.YamlConfig#onLoad()
	 */
	@Override
	protected void onLoad() {
		// Load any custom fields here, example:
		// this.chatColor = get("Chat_Color", CompChatColor.class);
		this.deathLocation = getLocation("Death_Location");
		this.portalLocation = getLocation("Portal_Location");
		this.trackingLocation = getString("Tracking_Location");
		this.targetByUUID = get("Track_Player", UUID.class);
		//this.deathDrops = get("Death_Drops", ItemStack[].class);
	}

	@Override
	protected void onSave() {
		this.set("Death_Location", deathLocation);
		this.set("Portal_Location", portalLocation);
		this.set("Tracking_Location", trackingLocation);
		this.set("Track_Player", targetByUUID);
	}

	/**
	 * Return player from cache if online or null otherwise
	 *
	 * @return
	 */
	@Nullable
	public Player toPlayer() {
		final Player player = Remain.getPlayerByUUID(this.uniqueId);

		return player != null && player.isOnline() ? player : null;
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

	public void setTrackingLocation(final String trackingLocation) {
		this.trackingLocation = trackingLocation;

		save();
	}

	public void setTargetByUUID(final UUID targetByUUID) {
		this.targetByUUID = targetByUUID;

		save();
	}

	/* ------------------------------------------------------------------------------- */
	/* Static access */
	/* ------------------------------------------------------------------------------- */

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
