package games.coob.smp.config;

import org.bukkit.Location;

import java.util.*;

/**
 * Utility class for serializing data to/from maps
 */
public final class SerializedMap {

	private final Map<String, Object> map;

	private SerializedMap(Map<String, Object> map) {
		this.map = map;
	}

	public static SerializedMap ofArray(Object... objects) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < objects.length; i += 2) {
			if (i + 1 < objects.length) {
				map.put(String.valueOf(objects[i]), objects[i + 1]);
			}
		}
		return new SerializedMap(map);
	}

	public static SerializedMap of(Map<String, Object> map) {
		return new SerializedMap(new LinkedHashMap<>(map));
	}

	public Map<String, Object> asMap() {
		return new LinkedHashMap<>(map);
	}

	public String getString(String key) {
		Object value = map.get(key);
		return value != null ? String.valueOf(value) : null;
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		Object value = map.get(key);
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		if (value instanceof String) {
			return Boolean.parseBoolean((String) value);
		}
		return defaultValue;
	}

	public UUID getUUID(String key) {
		String uuidStr = getString(key);
		if (uuidStr == null)
			return null;
		try {
			return UUID.fromString(uuidStr);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public Location getLocation(String key) {
		Object value = map.get(key);
		if (value instanceof Location) {
			return (Location) value;
		}
		if (value instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> locMap = (Map<String, Object>) value;
			String world = (String) locMap.get("world");
			if (world == null)
				return null;
			org.bukkit.World bukkitWorld = org.bukkit.Bukkit.getWorld(world);
			if (bukkitWorld == null)
				return null;
			Object xObj = locMap.get("x");
			Object yObj = locMap.get("y");
			Object zObj = locMap.get("z");
			if (xObj == null || yObj == null || zObj == null)
				return null;
			double x = ((Number) xObj).doubleValue();
			double y = ((Number) yObj).doubleValue();
			double z = ((Number) zObj).doubleValue();
			return new Location(bukkitWorld, x, y, z);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public List<String> getStringList(String key) {
		Object value = map.get(key);
		if (value instanceof List) {
			List<Object> list = (List<Object>) value;
			List<String> result = new ArrayList<>();
			for (Object item : list) {
				result.add(String.valueOf(item));
			}
			return result;
		}
		return new ArrayList<>();
	}

	@SuppressWarnings("unchecked")
	public SerializedMap getMap(String key) {
		Object value = map.get(key);
		if (value instanceof Map) {
			return new SerializedMap((Map<String, Object>) value);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public List<SerializedMap> getMapList(String key) {
		Object value = map.get(key);
		if (value instanceof List) {
			List<Object> list = (List<Object>) value;
			List<SerializedMap> result = new ArrayList<>();
			for (Object item : list) {
				if (item instanceof Map) {
					result.add(new SerializedMap((Map<String, Object>) item));
				}
			}
			return result;
		}
		return new ArrayList<>();
	}
}
