package games.coob.smp.duel.model;

import games.coob.smp.config.Serializable;
import games.coob.smp.config.SerializedMap;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a duel arena with spawn points for both players.
 */
@Getter
@Setter
public class ArenaData implements Serializable {

	private String name;
	private Location spawn1; // Challenger spawn
	private Location spawn2; // Opponent spawn
	private Location center; // Center point for border (calculated from spawns)
	private boolean complete; // Whether both spawns are set

	public ArenaData() {
	}

	public ArenaData(String name) {
		this.name = name;
		this.complete = false;
	}

	@Override
	public Map<String, Object> serialize() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("Name", this.name);
		map.put("Spawn1", this.spawn1);
		map.put("Spawn2", this.spawn2);
		map.put("Center", this.center);
		map.put("Complete", this.complete);
		return map;
	}

	public static ArenaData deserialize(final SerializedMap map) {
		final ArenaData arena = new ArenaData();
		arena.setName(map.getString("Name"));
		arena.setSpawn1(map.getLocation("Spawn1"));
		arena.setSpawn2(map.getLocation("Spawn2"));
		arena.setCenter(map.getLocation("Center"));
		arena.setComplete(map.getBoolean("Complete", false));
		return arena;
	}

	/**
	 * Recalculates the center point from the two spawn points.
	 */
	public void recalculateCenter() {
		if (spawn1 != null && spawn2 != null) {
			double x = (spawn1.getX() + spawn2.getX()) / 2;
			double y = (spawn1.getY() + spawn2.getY()) / 2;
			double z = (spawn1.getZ() + spawn2.getZ()) / 2;
			this.center = new Location(spawn1.getWorld(), x, y, z);
			this.complete = true;
		}
	}

	/**
	 * Checks if the arena is ready for use.
	 */
	public boolean isReady() {
		return complete && spawn1 != null && spawn2 != null && center != null;
	}
}
