package games.coob.smp.duel;

import games.coob.smp.duel.model.ArenaData;
import games.coob.smp.duel.model.ArenaRegistry;
import games.coob.smp.util.ColorUtil;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages arena creation and editing.
 * Provides a simple API for arena CRUD operations.
 */
public class ArenaManager {

	@Getter
	private static final ArenaManager instance = new ArenaManager();

	// Players currently in edit mode: playerId -> arenaName
	private final Map<UUID, String> editingPlayers = new ConcurrentHashMap<>();

	private ArenaManager() {
	}

	/**
	 * Creates a new arena and puts the player in edit mode.
	 *
	 * @param player The player creating the arena
	 * @param name   The arena name
	 * @return true if created successfully
	 */
	public boolean createArena(Player player, String name) {
		if (ArenaRegistry.getInstance().arenaExists(name)) {
			ColorUtil.sendMessage(player, "&cAn arena with that name already exists.");
			return false;
		}

		ArenaData arena = ArenaRegistry.getInstance().createArena(name);
		if (arena == null) {
			ColorUtil.sendMessage(player, "&cFailed to create arena.");
			return false;
		}

		// Put player in edit mode
		editingPlayers.put(player.getUniqueId(), name.toLowerCase());

		ColorUtil.sendMessage(player, "&aArena '&e" + name + "&a' created!");
		ColorUtil.sendMessage(player, "&7You are now in edit mode. Use:");
		ColorUtil.sendMessage(player, "&7- &e/arena setspawn1 &7- Set challenger spawn");
		ColorUtil.sendMessage(player, "&7- &e/arena setspawn2 &7- Set opponent spawn");
		ColorUtil.sendMessage(player, "&7- &e/arena save &7- Save and exit edit mode");

		return true;
	}

	/**
	 * Puts a player in edit mode for an existing arena.
	 *
	 * @param player The player
	 * @param name   The arena name
	 * @return true if edit mode started
	 */
	public boolean editArena(Player player, String name) {
		if (!ArenaRegistry.getInstance().arenaExists(name)) {
			ColorUtil.sendMessage(player, "&cArena '&e" + name + "&c' does not exist.");
			return false;
		}

		editingPlayers.put(player.getUniqueId(), name.toLowerCase());

		ColorUtil.sendMessage(player, "&aEditing arena '&e" + name + "&a'.");
		ColorUtil.sendMessage(player, "&7Use:");
		ColorUtil.sendMessage(player, "&7- &e/arena setspawn1 &7- Set challenger spawn");
		ColorUtil.sendMessage(player, "&7- &e/arena setspawn2 &7- Set opponent spawn");
		ColorUtil.sendMessage(player, "&7- &e/arena save &7- Save and exit edit mode");

		return true;
	}

	/**
	 * Sets spawn point 1 for the arena the player is editing.
	 *
	 * @param player The player
	 * @return true if set successfully
	 */
	public boolean setSpawn1(Player player) {
		String arenaName = editingPlayers.get(player.getUniqueId());
		if (arenaName == null) {
			ColorUtil.sendMessage(player, "&cYou are not editing any arena. Use &e/arena edit <name> &cfirst.");
			return false;
		}

		ArenaData arena = ArenaRegistry.getInstance().getArena(arenaName);
		if (arena == null) {
			editingPlayers.remove(player.getUniqueId());
			ColorUtil.sendMessage(player, "&cArena no longer exists.");
			return false;
		}

		Location location = player.getLocation();
		arena.setSpawn1(location);
		arena.recalculateCenter();
		ArenaRegistry.getInstance().updateArena(arena);

		ColorUtil.sendMessage(player, "&aSpawn 1 (Challenger) set at your location.");
		return true;
	}

	/**
	 * Sets spawn point 2 for the arena the player is editing.
	 *
	 * @param player The player
	 * @return true if set successfully
	 */
	public boolean setSpawn2(Player player) {
		String arenaName = editingPlayers.get(player.getUniqueId());
		if (arenaName == null) {
			ColorUtil.sendMessage(player, "&cYou are not editing any arena. Use &e/arena edit <name> &cfirst.");
			return false;
		}

		ArenaData arena = ArenaRegistry.getInstance().getArena(arenaName);
		if (arena == null) {
			editingPlayers.remove(player.getUniqueId());
			ColorUtil.sendMessage(player, "&cArena no longer exists.");
			return false;
		}

		Location location = player.getLocation();
		arena.setSpawn2(location);
		arena.recalculateCenter();
		ArenaRegistry.getInstance().updateArena(arena);

		ColorUtil.sendMessage(player, "&aSpawn 2 (Opponent) set at your location.");
		return true;
	}

	/**
	 * Saves the arena and exits edit mode.
	 *
	 * @param player The player
	 * @return true if saved successfully
	 */
	public boolean saveArena(Player player) {
		String arenaName = editingPlayers.remove(player.getUniqueId());
		if (arenaName == null) {
			ColorUtil.sendMessage(player, "&cYou are not editing any arena.");
			return false;
		}

		ArenaData arena = ArenaRegistry.getInstance().getArena(arenaName);
		if (arena == null) {
			ColorUtil.sendMessage(player, "&cArena no longer exists.");
			return false;
		}

		if (arena.isReady()) {
			ColorUtil.sendMessage(player, "&aArena '&e" + arena.getName() + "&a' saved and ready for use!");
		} else {
			ColorUtil.sendMessage(player, "&eArena '&6" + arena.getName() + "&e' saved but incomplete.");
			ColorUtil.sendMessage(player, "&7Missing spawn points. Arena cannot be used until both spawns are set.");
		}

		return true;
	}

	/**
	 * Deletes an arena.
	 *
	 * @param player The player
	 * @param name   The arena name
	 * @return true if deleted
	 */
	public boolean deleteArena(Player player, String name) {
		if (!ArenaRegistry.getInstance().arenaExists(name)) {
			ColorUtil.sendMessage(player, "&cArena '&e" + name + "&c' does not exist.");
			return false;
		}

		// Remove from any editing players
		editingPlayers.entrySet().removeIf(entry -> entry.getValue().equalsIgnoreCase(name));

		if (ArenaRegistry.getInstance().deleteArena(name)) {
			ColorUtil.sendMessage(player, "&aArena '&e" + name + "&a' deleted.");
			return true;
		}

		ColorUtil.sendMessage(player, "&cFailed to delete arena.");
		return false;
	}

	/**
	 * Sets the global lobby spawn.
	 *
	 * @param player The player
	 * @return true if set
	 */
	public boolean setLobbySpawn(Player player) {
		ArenaRegistry.getInstance().setLobbySpawn(player.getLocation());
		ColorUtil.sendMessage(player, "&aLobby spawn set at your location.");
		return true;
	}

	/**
	 * Lists all arenas.
	 *
	 * @param player The player to send the list to
	 */
	public void listArenas(Player player) {
		Set<String> arenaNames = ArenaRegistry.getInstance().getArenaNames();

		if (arenaNames.isEmpty()) {
			ColorUtil.sendMessage(player, "&7No arenas have been created yet.");
			return;
		}

		ColorUtil.sendMessage(player, "&6&lArenas &7(" + arenaNames.size() + "):");

		for (String name : arenaNames) {
			ArenaData arena = ArenaRegistry.getInstance().getArena(name);
			String status = arena.isReady() ? "&a[Ready]" : "&c[Incomplete]";
			ColorUtil.sendMessage(player, "&7- &e" + name + " " + status);
		}
	}

	/**
	 * Checks if a player is in edit mode.
	 *
	 * @param player The player
	 * @return true if editing
	 */
	public boolean isEditing(Player player) {
		return editingPlayers.containsKey(player.getUniqueId());
	}

	/**
	 * Gets the arena name a player is editing.
	 *
	 * @param player The player
	 * @return The arena name or null
	 */
	public String getEditingArena(Player player) {
		return editingPlayers.get(player.getUniqueId());
	}

	/**
	 * Cancels edit mode for a player.
	 *
	 * @param player The player
	 */
	public void cancelEdit(Player player) {
		String arena = editingPlayers.remove(player.getUniqueId());
		if (arena != null) {
			ColorUtil.sendMessage(player, "&cExited edit mode for arena '&e" + arena + "&c'.");
		}
	}

	/**
	 * Handles player disconnect.
	 *
	 * @param player The player
	 */
	public void handlePlayerQuit(Player player) {
		editingPlayers.remove(player.getUniqueId());
	}
}
