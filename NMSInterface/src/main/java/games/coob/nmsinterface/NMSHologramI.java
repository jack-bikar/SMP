package games.coob.nmsinterface;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.mineacademy.fo.model.ConfigSerializable;

import java.util.UUID;

public interface NMSHologramI extends ConfigSerializable {

	void show(Location location, final Player player, final String... linesOfText);

	Object createEntity(Object nmsWorld, Location location);

	void sendPackets(Player player, Object nmsArmorStand);

	UUID getUniqueId();

	Location getLocation();

	void setLines(String[] lines);

	String[] getLines();

	void remove(Player player);

	void hide(Player player);
}
