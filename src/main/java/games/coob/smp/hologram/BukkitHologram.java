package games.coob.smp.hologram;

import games.coob.smp.SMPPlugin;
import games.coob.smp.config.Serializable;
import games.coob.smp.config.SerializedMap;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.ValidationUtil;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;

/**
 * Hologram implementation using Paper 1.21+ API
 * Uses ArmorStands with Paper's showEntity/hideEntity API for per-player visibility
 */
public class BukkitHologram implements Serializable {

	@Getter
    private final UUID uniqueId;
	private Location location;
	@Setter
    private String[] lines;
	private final List<ArmorStand> armorStands = new ArrayList<>();
	private final Set<Player> visibleToPlayers = new HashSet<>();

	public BukkitHologram() {
		this.uniqueId = UUID.randomUUID();
	}

	public BukkitHologram(UUID uniqueId, Location location, String[] lines) {
		this.uniqueId = uniqueId;
		this.location = location;
		this.lines = lines;
	}

	/**
	 * Initialize hologram location and armor stands without showing to any player.
	 * Used for non-persistent holograms that are shown only when players enter range.
	 */
	public void ensureCreated(Location location, String... linesOfText) {
		if (this.location != null) return;
		this.location = location.clone();
		this.lines = linesOfText;
		Location currentLoc = location.clone();
		for (String line : linesOfText) {
			ArmorStand stand = location.getWorld().spawn(currentLoc, ArmorStand.class, as -> {
				as.setVisible(false);
				as.setGravity(false);
				as.setCustomName(ColorUtil.colorize(line));
				as.setCustomNameVisible(true);
				as.setMarker(true);
				as.setSmall(true);
				as.setInvulnerable(true);
				as.setCollidable(false);
			});
			armorStands.add(stand);
			currentLoc.subtract(0, 0.26, 0);
		}
	}

	@SuppressWarnings("deprecation")
	public void show(Location location, Player player, String... linesOfText) {
		// Set location and lines if not already set (first time)
		if (this.location == null) {
			this.location = location.clone();
			this.lines = linesOfText;
			Location currentLoc = location.clone();
			for (String line : linesOfText) {
				ArmorStand stand = location.getWorld().spawn(currentLoc, ArmorStand.class, as -> {
					as.setVisible(false);
					as.setGravity(false);
					as.setCustomName(ColorUtil.colorize(line));
					as.setCustomNameVisible(true);
					as.setMarker(true);
					as.setSmall(true);
					as.setInvulnerable(true);
					as.setCollidable(false);
				});
				armorStands.add(stand);
				currentLoc.subtract(0, 0.26, 0);
			}
		}

		// Show to this specific player using Paper 1.21+ API
		if (player != null && !visibleToPlayers.contains(player)) {
			for (ArmorStand stand : armorStands) {
				player.showEntity(SMPPlugin.getInstance(), stand);
			}
			visibleToPlayers.add(player);
			player.setMetadata(uniqueId.toString(), new FixedMetadataValue(SMPPlugin.getInstance(), ""));
		}
	}

	@SuppressWarnings("deprecation")
	public void hide(Player player) {
		if (!visibleToPlayers.contains(player))
			return;

		// Hide all armor stands from this player using Paper 1.21+ API
		// Note: hideEntity is deprecated but still the standard way for per-player visibility in Paper
		for (ArmorStand stand : armorStands) {
			player.hideEntity(SMPPlugin.getInstance(), stand);
		}

		visibleToPlayers.remove(player);
		player.removeMetadata(uniqueId.toString(), SMPPlugin.getInstance());
	}

	public void remove(Player player) {
		hide(player);
	}

	/**
	 * Remove this hologram completely (for all players)
	 */
	public void removeAll() {
		// Hide from all players
		for (Player player : new HashSet<>(visibleToPlayers)) {
			hide(player);
		}

		// Remove all armor stands
		for (ArmorStand stand : armorStands) {
			stand.remove();
		}
		armorStands.clear();
	}

    public Location getLocation() {
		ValidationUtil.checkBoolean(location != null, "Cannot call getLocation when location is not set");
		return location.clone();
	}

    public String[] getLines() {
		return lines != null ? lines.clone() : new String[0];
	}


	@Override
	public Map<String, Object> serialize() {
		ValidationUtil.checkBoolean(location != null, "Cannot save hologram without location");
		ValidationUtil.checkBoolean(lines != null, "Cannot save hologram without lines");

		Map<String, Object> map = new LinkedHashMap<>();
		map.put("UUID", this.uniqueId.toString());
		map.put("Lines", Arrays.asList(this.lines));
		map.put("Last_Location", this.location);
		return map;
	}

	public static BukkitHologram deserialize(SerializedMap map) {
		final String[] lines = map.getStringList("Lines").toArray(new String[0]);
		final Location lastLocation = map.getLocation("Last_Location");
		final UUID uuid = map.getUUID("UUID");

		return new BukkitHologram(uuid, lastLocation, lines);
	}
}
