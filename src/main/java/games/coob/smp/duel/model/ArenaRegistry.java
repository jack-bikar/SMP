package games.coob.smp.duel.model;

import games.coob.smp.config.ConfigFile;
import games.coob.smp.config.SerializedMap;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Registry for duel arenas.
 * Persists to arenas.yml.
 */
public class ArenaRegistry extends ConfigFile {

	@Getter
	private static final ArenaRegistry instance = new ArenaRegistry();

	// Initialized in onLoad() because super() calls load() before our instance initializers run
	private Map<String, ArenaData> arenas;

	@Getter
	private Location lobbySpawn;

	private ArenaRegistry() {
		super("arenas.yml");
		// Ensure arenas is initialized (onLoad is called from super() before we get here)
		if (arenas == null) {
			arenas = new HashMap<>();
		}
	}

	@Override
	protected void onLoad() {
		if (arenas == null) {
			arenas = new HashMap<>();
		} else {
			arenas.clear();
		}

		// Load lobby spawn
		lobbySpawn = getConfig().getLocation("Lobby_Spawn");

		// Load arenas
		ConfigurationSection section = getConfig().getConfigurationSection("Arenas");
		if (section != null) {
			for (String key : section.getKeys(false)) {
				ConfigurationSection arenaSection = section.getConfigurationSection(key);
				if (arenaSection != null) {
					Map<String, Object> mapData = arenaSection.getValues(true);
					SerializedMap map = SerializedMap.of(mapData);
					ArenaData arena = ArenaData.deserialize(map);
					arenas.put(arena.getName().toLowerCase(), arena);
				}
			}
		}
	}

	@Override
	protected void onSave() {
		// Save lobby spawn
		getConfig().set("Lobby_Spawn", lobbySpawn);

		// Save arenas
		getConfig().set("Arenas", null); // Clear existing
		if (arenas != null) {
			for (ArenaData arena : arenas.values()) {
				getConfig().set("Arenas." + arena.getName(), arena.serialize());
			}
		}
	}

	/**
	 * Create a new arena.
	 */
	public ArenaData createArena(String name) {
		String key = name.toLowerCase();
		if (arenas.containsKey(key)) {
			return null;
		}
		ArenaData arena = new ArenaData(name);
		arenas.put(key, arena);
		save();
		return arena;
	}

	/**
	 * Get an arena by name.
	 */
	public ArenaData getArena(String name) {
		return arenas.get(name.toLowerCase());
	}

	/**
	 * Delete an arena.
	 */
	public boolean deleteArena(String name) {
		ArenaData removed = arenas.remove(name.toLowerCase());
		if (removed != null) {
			save();
			return true;
		}
		return false;
	}

	/**
	 * Check if an arena exists.
	 */
	public boolean arenaExists(String name) {
		return arenas.containsKey(name.toLowerCase());
	}

	/**
	 * Get all arena names.
	 */
	public Set<String> getArenaNames() {
		Set<String> names = new HashSet<>();
		for (ArenaData arena : arenas.values()) {
			names.add(arena.getName());
		}
		return names;
	}

	/**
	 * Get all complete (ready) arenas.
	 */
	public List<ArenaData> getReadyArenas() {
		List<ArenaData> ready = new ArrayList<>();
		for (ArenaData arena : arenas.values()) {
			if (arena.isReady()) {
				ready.add(arena);
			}
		}
		return ready;
	}

	/**
	 * Get a random ready arena.
	 */
	public ArenaData getRandomArena() {
		List<ArenaData> ready = getReadyArenas();
		if (ready.isEmpty()) {
			return null;
		}
		return ready.get(new Random().nextInt(ready.size()));
	}

	/**
	 * Set the lobby spawn location.
	 */
	public void setLobbySpawn(Location location) {
		this.lobbySpawn = location;
		save();
	}

	/**
	 * Update an arena (after editing spawns).
	 */
	public void updateArena(ArenaData arena) {
		arenas.put(arena.getName().toLowerCase(), arena);
		save();
	}
}
