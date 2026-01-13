package games.coob.smp.config;

import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

import java.util.*;

/**
 * Interface for objects that can be serialized to/from configuration
 */
public interface Serializable {

	/**
	 * Serialize this object to a map
	 *
	 * @return Serialized map
	 */
	Map<String, Object> serialize();

	/**
	 * Deserialize from a map
	 *
	 * @param map The map to deserialize from
	 * @return Deserialized object
	 */
	static Serializable deserialize(Map<String, Object> map) {
		throw new UnsupportedOperationException("Must be implemented by subclass");
	}

	/**
	 * Helper to get a value from a map
	 */
	default <T> T get(Map<String, Object> map, String key, Class<T> type) {
		Object value = map.get(key);
		if (value == null) return null;
		if (type.isInstance(value)) {
			return type.cast(value);
		}
		return null;
	}

	/**
	 * Helper to get a string from a map
	 */
	default String getString(Map<String, Object> map, String key) {
		return get(map, key, String.class);
	}

	/**
	 * Helper to get a UUID from a map
	 */
	default java.util.UUID getUUID(Map<String, Object> map, String key) {
		String uuidStr = getString(map, key);
		if (uuidStr == null) return null;
		try {
			return java.util.UUID.fromString(uuidStr);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * Helper to get a location from a map
	 */
	default Location getLocation(Map<String, Object> map, String key) {
		Object value = map.get(key);
		if (value instanceof Location) {
			return (Location) value;
		}
		if (value instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> locMap = (Map<String, Object>) value;
			String world = getString(locMap, "world");
			if (world == null) return null;
			org.bukkit.World bukkitWorld = org.bukkit.Bukkit.getWorld(world);
			if (bukkitWorld == null) return null;
			Double x = get(locMap, "x", Double.class);
			Double y = get(locMap, "y", Double.class);
			Double z = get(locMap, "z", Double.class);
			if (x == null || y == null || z == null) return null;
			return new Location(bukkitWorld, x, y, z);
		}
		return null;
	}

	/**
	 * Helper to get a list from a map
	 */
	@SuppressWarnings("unchecked")
	default <T> List<T> getList(Map<String, Object> map, String key, Class<T> type) {
		Object value = map.get(key);
		if (value instanceof List) {
			List<Object> list = (List<Object>) value;
			List<T> result = new ArrayList<>();
			for (Object item : list) {
				if (type.isInstance(item)) {
					result.add(type.cast(item));
				}
			}
			return result;
		}
		return new ArrayList<>();
	}

	/**
	 * Helper to get a string list from a map
	 */
	default List<String> getStringList(Map<String, Object> map, String key) {
		return getList(map, key, String.class);
	}
}
