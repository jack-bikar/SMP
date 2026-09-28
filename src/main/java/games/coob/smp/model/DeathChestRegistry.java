package games.coob.smp.model;

import games.coob.smp.SMPPlugin;
import games.coob.smp.config.ConfigFile;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.InventorySerialization;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * All death chests on the server, stored in death-chests.yml.
 */
public final class DeathChestRegistry extends ConfigFile {

	@Getter
	private static final DeathChestRegistry instance = new DeathChestRegistry();

	private Map<String, DeathChest> chests;

	private DeathChestRegistry() {
		super("death-chests.yml");
	}

	@Override
	protected void onLoad() {
		chests = new HashMap<>();

		ConfigurationSection section = getConfig().getConfigurationSection("Chests");
		if (section != null) {
			for (String id : section.getKeys(false)) {
				ConfigurationSection data = section.getConfigurationSection(id);
				if (data == null)
					continue;
				try {
					DeathChest chest = new DeathChest(
							data.getString("World"),
							data.getInt("X"), data.getInt("Y"), data.getInt("Z"),
							UUID.fromString(data.getString("Owner")),
							data.getString("Owner_Name", "Unknown"),
							materialOr(data.getString("Material")),
							InventorySerialization.fromBase64(data.getString("Items")));
					chests.put(chest.getKey(), chest);
				} catch (Exception e) {
					SMPPlugin.getInstance().getLogger().log(Level.WARNING, "Skipping unreadable death chest " + id, e);
				}
			}
		} else if (!file.exists()) {
			migrateLegacyChests();
		}
	}

	@Override
	protected void onSave() {
		getConfig().set("Chests", null);
		int index = 0;
		for (DeathChest chest : chests.values()) {
			String path = "Chests." + index++ + ".";
			getConfig().set(path + "World", chest.getWorldName());
			getConfig().set(path + "X", chest.getX());
			getConfig().set(path + "Y", chest.getY());
			getConfig().set(path + "Z", chest.getZ());
			getConfig().set(path + "Owner", chest.getOwnerId().toString());
			getConfig().set(path + "Owner_Name", chest.getOwnerName());
			getConfig().set(path + "Material", chest.getMaterial().name());
			getConfig().set(path + "Items", InventorySerialization.toBase64(chest.getInventory().getContents()));
		}
	}

	/**
	 * Places a death chest at the block and fills it with the items.
	 */
	public DeathChest create(Block block, UUID ownerId, String ownerName, ItemStack[] items) {
		block.setType(Settings.DeathStorageSection.STORAGE_MATERIAL, false);
		BlockData data = block.getBlockData();
		if (data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
			waterlogged.setWaterlogged(false);
			block.setBlockData(waterlogged, false);
		}

		DeathChest chest = new DeathChest(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
				ownerId, ownerName, Settings.DeathStorageSection.STORAGE_MATERIAL, items);
		chests.put(chest.getKey(), chest);
		chest.ensureHologram();
		save();
		return chest;
	}

	/**
	 * Removes a death chest: closes it for viewers, removes the block and hologram.
	 *
	 * @param dropItems drop what is still inside at the chest location
	 */
	public void remove(DeathChest chest, boolean dropItems) {
		// Already removed (e.g. two viewers closed it at once)
		if (chests.remove(chest.getKey()) != chest)
			return;
		chest.removeHologram();

		for (HumanEntity viewer : new ArrayList<>(chest.getInventory().getViewers()))
			viewer.closeInventory();

		if (dropItems && chest.getWorld() != null) {
			Location drop = chest.getDropLocation();
			for (ItemStack item : chest.getInventory().getContents()) {
				if (item != null && !item.getType().isAir())
					drop.getWorld().dropItemNaturally(drop, item);
			}
		}
		chest.getInventory().clear();

		Block block = chest.getBlock();
		if (block != null && chest.isLoaded() && block.getType() == chest.getMaterial())
			block.setType(Material.AIR);

		save();
	}

	public DeathChest get(Block block) {
		return chests.isEmpty() ? null : chests.get(DeathChest.key(block));
	}

	public boolean isDeathChest(Block block) {
		return get(block) != null;
	}

	public boolean isEmpty() {
		return chests.isEmpty();
	}

	public Collection<DeathChest> getChests() {
		return new ArrayList<>(chests.values());
	}

	/**
	 * Keeps holograms spawned for chests in loaded chunks, and cleans up chests
	 * whose block was removed by something other than a player (e.g. WorldEdit).
	 */
	public void tick() {
		for (DeathChest chest : getChests()) {
			if (!chest.isLoaded())
				continue;

			if (chest.getBlock().getType() != chest.getMaterial()) {
				remove(chest, true);
				continue;
			}
			chest.ensureHologram();
		}
	}

	/**
	 * Plugin disable: closes open death chests (so nothing can be taken twice
	 * after a reload) and removes the hologram entities.
	 */
	public void shutdown() {
		for (DeathChest chest : chests.values()) {
			for (HumanEntity viewer : new ArrayList<>(chest.getInventory().getViewers()))
				viewer.closeInventory();
			chest.removeHologram();
		}
	}

	private static Material materialOr(String name) {
		Material material = name != null ? Material.matchMaterial(name) : null;
		return material != null && material.isBlock() ? material : Settings.DeathStorageSection.STORAGE_MATERIAL;
	}

	/**
	 * Older versions stored death chests in data.yml (and never loaded them back
	 * after a restart). Import whatever is still there.
	 */
	@SuppressWarnings("unchecked")
	private void migrateLegacyChests() {
		File legacyFile = new File(SMPPlugin.getInstance().getDataFolder(), "data.yml");
		if (!legacyFile.exists())
			return;

		YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
		int imported = 0;
		for (Map<?, ?> map : legacy.getMapList("Death_Chests")) {
			try {
				Location location = (Location) map.get("Location");
				UUID owner = UUID.fromString((String) map.get("UUID"));
				ItemStack[] items = InventorySerialization.fromLegacyBase64((String) map.get("Inventory"));
				String ownerName = Bukkit.getOfflinePlayer(owner).getName();

				DeathChest chest = new DeathChest(location.getWorld().getName(), location.getBlockX(),
						location.getBlockY(), location.getBlockZ(), owner, ownerName != null ? ownerName : "Unknown",
						Settings.DeathStorageSection.STORAGE_MATERIAL, items);
				if (!chest.isEmpty()) {
					chests.put(chest.getKey(), chest);
					imported++;
				}
			} catch (Exception ignored) {
				// Unreadable legacy entry, skip it
			}
		}

		if (imported > 0) {
			SMPPlugin.getInstance().getLogger().info("Imported " + imported + " death chest(s) from data.yml");
			save();
		}
	}
}
