package games.coob.smp.model;

import games.coob.nmsinterface.NMSHologramI;
import games.coob.smp.PlayerCache;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.constants.FoConstants;
import org.mineacademy.fo.settings.YamlConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DeathChestRegistry extends YamlConfig {

	@Getter
	private static final DeathChestRegistry instance = new DeathChestRegistry();

	private Set<DeathChestData> registeredDeathChests = new HashSet<>();

	private DeathChestRegistry() {
		this.loadConfiguration(NO_DEFAULT, FoConstants.File.DATA);
	}

	@Override
	protected void onLoad() {
		this.registeredDeathChests = this.getSet("Death_Chests", DeathChestData.class);
	}

	@Override
	protected void onSave() {
		this.set("Death_Chests", this.registeredDeathChests);
	}

	public void register(final Block block, final Player player, final NMSHologramI hologram) {
		final DeathChestData deathChestData = new DeathChestData();
		final PlayerCache cache = PlayerCache.from(player);

		Valid.checkBoolean(!this.registeredDeathChests.contains(deathChestData), block + " has already been registered");

		deathChestData.setInventory(cache.getDeathChestInventory());
		deathChestData.setLocation(block.getLocation());
		deathChestData.setUuid(player.getUniqueId());
		deathChestData.setHologram(hologram);

		this.registeredDeathChests.add(deathChestData);
		this.save();
	}

	public void unregister(final Block block) {
		// synchronized (registeredBlocks) { // synchronized is used for anyscronous processing (Common.runLaterAsync)
		this.registeredDeathChests.removeIf(deathChestData -> deathChestData.getLocation().getBlock().equals(block));

		this.save();
	}

	public boolean isRegistered(final Block block) {
		for (final DeathChestData deathChestData : this.registeredDeathChests) {
			if (deathChestData.getLocation().equals(block.getLocation()))
				return true;
		}

		return false;
	}

	public List<Location> getLocations() {
		final List<Location> locations = new ArrayList<>();

		for (final DeathChestData deathChestData : this.registeredDeathChests)
			locations.add(deathChestData.getLocation());

		return locations;
	}

	public Inventory getInventory(final Block block) {
		for (final DeathChestData deathChestData : this.registeredDeathChests)
			if (deathChestData.getLocation().equals(block.getLocation()))
				return deathChestData.getInventory();

		return null;
	}

	public NMSHologramI getHologram(final Block block) {
		for (final DeathChestData deathChestData : this.registeredDeathChests)
			if (deathChestData.getLocation().equals(block.getLocation()))
				return deathChestData.getHologram();

		return null;
	}

	public Block getBlock(final Location location) {
		for (final DeathChestData deathChestData : this.registeredDeathChests) {
			if (deathChestData.getLocation().equals(location))
				return deathChestData.getLocation().getBlock();
		}

		return null;
	}
}
