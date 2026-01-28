package games.coob.smp.tracking;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

/**
 * Manages the Player Locator Bar using the proper API
 * Based on the LocatorToggle plugin implementation
 */
public class LocatorBarManager {

	private static final double WORLD_MAX = 6.0e7;
	private static final double NONE = 0.0;

	private final Player player;
	private static Attribute WAYPOINT_RECEIVE_RANGE;
	private static Attribute WAYPOINT_TRANSMIT_RANGE;

	static {
		// Try to get the attributes using reflection since they might not be in the
		// enum
		// These attributes were added in Minecraft 1.21.5+ for the locator bar feature
		try {
			// Try direct field access first
			Field receiveField = Attribute.class.getField("WAYPOINT_RECEIVE_RANGE");
			WAYPOINT_RECEIVE_RANGE = (Attribute) receiveField.get(null);
		} catch (NoSuchFieldException e) {
			// Try using valueOf if it's an enum value
			try {
				WAYPOINT_RECEIVE_RANGE = Attribute.valueOf("WAYPOINT_RECEIVE_RANGE");
			} catch (IllegalArgumentException ex) {
				// Attribute not found in this version, will use null checks
				WAYPOINT_RECEIVE_RANGE = null;
			}
		} catch (Exception e) {
			// Attribute not found, will use null checks
			WAYPOINT_RECEIVE_RANGE = null;
		}

		try {
			Field transmitField = Attribute.class.getField("WAYPOINT_TRANSMIT_RANGE");
			WAYPOINT_TRANSMIT_RANGE = (Attribute) transmitField.get(null);
		} catch (NoSuchFieldException e) {
			// Try using valueOf if it's an enum value
			try {
				WAYPOINT_TRANSMIT_RANGE = Attribute.valueOf("WAYPOINT_TRANSMIT_RANGE");
			} catch (IllegalArgumentException ex) {
				// Attribute not found in this version, will use null checks
				WAYPOINT_TRANSMIT_RANGE = null;
			}
		} catch (Exception e) {
			// Attribute not found, will use null checks
			WAYPOINT_TRANSMIT_RANGE = null;
		}
	}

	public LocatorBarManager(Player player) {
		this.player = player;
	}

	/**
	 * Enable the locator bar for this player
	 */
	public void enable() {
		enableTemporarily();
	}

	/**
	 * Disable the locator bar for this player
	 */
	public void disable() {
		disableTemporarily();
	}

	/**
	 * Temporarily enable the locator bar (doesn't persist preference)
	 * This enables the player to RECEIVE waypoints (see other players)
	 * Note: Even if attributes don't exist, setCompassTarget() should still work
	 * for the locator bar
	 */
	public void enableTemporarily() {
		// Try to enable via attributes if available (for Paper 1.21.5+)
		if (WAYPOINT_RECEIVE_RANGE != null) {
			AttributeInstance receiveRangeAttr = player.getAttribute(WAYPOINT_RECEIVE_RANGE);
			if (receiveRangeAttr != null) {
				receiveRangeAttr.setBaseValue(WORLD_MAX);
			}
		}
		// Note: We don't set transmit range here - that's done separately via
		// enableTransmit()
		// on the target player so they appear as a waypoint
		// Even without attributes, setCompassTarget() should make the locator bar point
		// to a location
	}

	/**
	 * Enable waypoint transmission for this player (makes them visible as a
	 * waypoint to others)
	 */
	public void enableTransmit() {
		if (WAYPOINT_TRANSMIT_RANGE != null) {
			AttributeInstance transmitRangeAttr = player.getAttribute(WAYPOINT_TRANSMIT_RANGE);
			if (transmitRangeAttr != null) {
				transmitRangeAttr.setBaseValue(WORLD_MAX);
			}
		}
	}

	/**
	 * Disable waypoint transmission for this player (hides them from others'
	 * locator bars)
	 */
	public void disableTransmit() {
		if (WAYPOINT_TRANSMIT_RANGE != null) {
			AttributeInstance transmitRangeAttr = player.getAttribute(WAYPOINT_TRANSMIT_RANGE);
			if (transmitRangeAttr != null) {
				transmitRangeAttr.setBaseValue(NONE);
			}
		}
	}

	/**
	 * Temporarily disable the locator bar (doesn't persist preference)
	 *
	 * Important: this should only disable the player's ability to RECEIVE waypoints
	 * (hide the bar).
	 * Do NOT disable transmit here, otherwise players being tracked will disappear
	 * as soon as their own
	 * tick runs (most players aren't tracking anyone themselves).
	 */
	public void disableTemporarily() {
		if (WAYPOINT_RECEIVE_RANGE != null) {
			AttributeInstance receiveRangeAttr = player.getAttribute(WAYPOINT_RECEIVE_RANGE);
			if (receiveRangeAttr != null) {
				receiveRangeAttr.setBaseValue(NONE);
			}
		}
	}

	/**
	 * Disable both receive + transmit for this player.
	 * Use this only when you explicitly want the player hidden from others too.
	 */
	public void disableAll() {
		disableTemporarily();
		disableTransmit();
	}

	/**
	 * Check if locator bar is enabled (non-zero range)
	 */
	public boolean isEnabled() {
		if (WAYPOINT_RECEIVE_RANGE == null) {
			// Attributes not available - assume enabled if compass target is set to
			// something other than player's location
			// This is a fallback for versions without waypoint attributes
			org.bukkit.Location compassTarget = player.getCompassTarget();
			org.bukkit.Location playerLoc = player.getLocation();
			if (compassTarget != null && playerLoc != null) {
				// If compass target is significantly different from player location, assume
				// locator bar is active
				return compassTarget.distanceSquared(playerLoc) > 1.0; // More than 1 block away
			}
			return false;
		}
		AttributeInstance receiveRangeAttr = player.getAttribute(WAYPOINT_RECEIVE_RANGE);
		if (receiveRangeAttr == null) {
			return false;
		}
		return receiveRangeAttr.getBaseValue() > 0;
	}

	/**
	 * Set the compass target location (this controls where the locator bar points)
	 * This works regardless of whether waypoint attributes are available
	 */
	public void setTarget(org.bukkit.Location location) {
		if (location != null && location.getWorld() != null) {
			player.setCompassTarget(location);
		}
	}
}
