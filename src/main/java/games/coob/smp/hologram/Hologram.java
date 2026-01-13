package games.coob.smp.hologram;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.mineacademy.fo.model.ConfigSerializable;

import java.util.UUID;

/**
 * Interface for hologram implementations using Paper 1.21+ API
 */
public interface Hologram extends ConfigSerializable {

	/**
	 * Show the hologram to a specific player at the given location
	 *
	 * @param location    The location to show the hologram
	 * @param player      The player to show it to
	 * @param linesOfText The text lines to display
	 */
	void show(Location location, Player player, String... linesOfText);

	/**
	 * Hide the hologram from a specific player
	 *
	 * @param player The player to hide it from
	 */
	void hide(Player player);

	/**
	 * Remove the hologram from a specific player (same as hide)
	 *
	 * @param player The player to remove it from
	 */
	void remove(Player player);

	/**
	 * Get the unique ID of this hologram
	 *
	 * @return The unique ID
	 */
	UUID getUniqueId();

	/**
	 * Get the location of this hologram
	 *
	 * @return The location
	 */
	Location getLocation();

	/**
	 * Set the text lines for this hologram
	 *
	 * @param lines The text lines
	 */
	void setLines(String[] lines);

	/**
	 * Get the text lines of this hologram
	 *
	 * @return The text lines
	 */
	String[] getLines();
}
