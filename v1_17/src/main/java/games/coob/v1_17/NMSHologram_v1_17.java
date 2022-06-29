package games.coob.v1_17;

import net.citizensnpcs.api.npc.NPCRegistry;
import net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLiving;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.entity.decoration.EntityArmorStand;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.remain.Remain;

import games.coob.nmsinterface.NMSHologramI;

import java.util.UUID;

public class NMSHologram_v1_17 implements NMSHologramI { // TODO Make this version work too

	/**
	 * The spawned NMS entity
	 */
	private EntityArmorStand entityArmorStand;

	private String[] lines;

	@Override
	public Object createEntity(final Object nmsWorld, final Location location) {
		entityArmorStand = new EntityArmorStand((WorldServer) nmsWorld, location.getX(), location.getY(), location.getZ());

		if (HologramRegistry_v1_17.getInstance().isRegistered(entityArmorStand.getUniqueID()))
			HologramRegistry_v1_17.getInstance().register(this);

		return entityArmorStand;
	}

	@Override
	public void sendPackets(final Player player, final Object nmsArmorStand) {
		final EntityArmorStand nmsStand = (EntityArmorStand) nmsArmorStand;

		Remain.sendPacket(player, new PacketPlayOutSpawnEntityLiving(nmsStand));
		Remain.sendPacket(player, new PacketPlayOutEntityMetadata(nmsStand.getId(), nmsStand.getDataWatcher(), true));
	}

	@Override
	public UUID getUniqueId() {
		return this.entityArmorStand.getBukkitEntity().getUniqueId();
	}

	/**
	 * Convenience method to return the location of this NPC.
	 *
	 * @return
	 */
	@Override
	public Location getLocation() {
		Valid.checkBoolean(this.isCreated(), "Cannot call getLocation when " + this + " is not created");

		return this.entityArmorStand.getBukkitEntity().getLocation();
	}

	@Override
	public void setLines(final String[] lines) {
		this.lines = lines;
	}

	@Override
	public String[] getLines() {
		return this.lines;
	}

	@Override
	public void remove(final Player player) {

	}

	@Override
	public void hide(final Player player) {

	}

	/**
	 * Return if this hologram is spawned
	 *
	 * @return
	 */
	boolean isCreated() {
		entityArmorStand.getBukkitEntity();
		return true;
	}

	@Override
	public SerializedMap serialize() {
		Valid.checkBoolean(this.isCreated(), "Cannot save non-created holograms");

		return SerializedMap.ofArray(
				//	"UUID", this.entityArmorStand.getUniqueID(),
				"Lines", this.lines,
				"Last_Location", this.getLocation());
	}

	/**
	 * Converts information saved in data.db file as a map into an NPC,
	 * also spawning it. After spawn this NPC will auto register in {@link NPCRegistry}
	 *
	 * @param map
	 * @return
	 */
	public static NMSHologram_v1_17 deserialize(final SerializedMap map) {
		final String[] lines = map.getStringList("Lines").toArray(new String[0]);
		final Location lastLocation = map.getLocation("Last_Location");
		final Object nmsWorld = Remain.getHandleWorld(lastLocation.getWorld());
		final NMSHologram_v1_17 hologram = new NMSHologram_v1_17();

		hologram.createEntity(nmsWorld, lastLocation);
		hologram.setLines(lines);

		return hologram;
	}

	@Override
	public void show(final Location location, final Player player, final String... linesOfText) {
		// TODO Auto-generated method stub

	}
}