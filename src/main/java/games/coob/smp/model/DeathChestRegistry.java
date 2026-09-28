package games.coob.smp.model;

import com.destroystokyo.paper.profile.ProfileProperty;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * All death chests on the server, stored in death-chests.yml.
 */
public final class DeathChestRegistry extends ConfigFile {

	@Getter
	private static final DeathChestRegistry instance = new DeathChestRegistry();

	public static final String BODY = "BODY";

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
							InventorySerialization.fromBase64(data.getString("Items")),
							data.getString("Skin.Value"), data.getString("Skin.Signature"),
							(float) data.getDouble("Yaw"), poseOr(data.getString("Pose")));
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
			getConfig().set(path + "Material", chest.isBody() ? BODY : chest.getMaterial().name());
			if (chest.isBody()) {
				getConfig().set(path + "Skin.Value", chest.getSkinValue());
				getConfig().set(path + "Skin.Signature", chest.getSkinSignature());
				getConfig().set(path + "Yaw", chest.getYaw());
				getConfig().set(path + "Pose", chest.getPose().name());
			}
			getConfig().set(path + "Items", chest.encodedItems());
		}
	}

	/**
	 * Places a death chest block at the block and fills it with the items.
	 */
	public DeathChest create(Block block, UUID ownerId, String ownerName, ItemStack[] items) {
		Material material = Settings.DeathStorageSection.STORAGE_MATERIAL;
		block.setType(material, false);
		BlockData data = block.getBlockData();
		if (data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
			waterlogged.setWaterlogged(false);
			block.setBlockData(waterlogged, false);
		}

		return register(new DeathChest(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
				ownerId, ownerName, material, items, null, null, 0, Pose.STANDING));
	}

	/**
	 * Lays the player's body down at the block (no block is placed) holding the items.
	 */
	public DeathChest createBody(Block spot, Player owner, ItemStack[] items) {
		String skinValue = null;
		String skinSignature = null;
		for (ProfileProperty property : owner.getPlayerProfile().getProperties()) {
			if (property.getName().equals("textures")) {
				skinValue = property.getValue();
				skinSignature = property.getSignature();
			}
		}

		ThreadLocalRandom random = ThreadLocalRandom.current();
		Pose pose = random.nextBoolean() ? Pose.SLEEPING : Pose.SWIMMING;
		return register(new DeathChest(spot.getWorld().getName(), spot.getX(), spot.getY(), spot.getZ(),
				owner.getUniqueId(), owner.getName(), null, items, skinValue, skinSignature,
				random.nextFloat() * 360f - 180f, pose));
	}

	/** Null if that spot is already taken (the caller lets the items drop normally). */
	private DeathChest register(DeathChest chest) {
		if (chests.putIfAbsent(chest.getKey(), chest) != null)
			return null;
		try {
			chest.ensureEntities();
		} catch (RuntimeException e) {
			SMPPlugin.getInstance().getLogger().log(Level.WARNING, "Could not show death chest " + chest.getKey(), e);
		}
		save();
		return chest;
	}

	/** The death chest a body or its click box belongs to, if any. */
	public DeathChest get(Entity entity) {
		if (chests.isEmpty())
			return null;
		String key = entity.getPersistentDataContainer().get(DeathChest.ENTITY_KEY, PersistentDataType.STRING);
		return key == null ? null : chests.get(key);
	}

	/** Whether this entity is part of a death body (even a leftover one). */
	public static boolean isBodyEntity(Entity entity) {
		return entity.getPersistentDataContainer().has(DeathChest.ENTITY_KEY, PersistentDataType.STRING);
	}

	/** After items were taken: update what the body wears. */
	public void refresh(DeathChest chest) {
		chest.updateBodyArmour();
	}

	/**
	 * Removes a death chest: closes it for viewers, removes the block and hologram.
	 *
	 * @param dropItems drop what is still inside at the chest location
	 */
	public void remove(DeathChest chest, boolean dropItems) {
		// Already removed (e.g. two viewers closed it at once)
		if (!chests.remove(chest.getKey(), chest))
			return;
		chest.removeEntities();

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

		Block block = chest.isBody() ? null : chest.getBlock();
		if (block != null && chest.isLoaded() && block.getType() == chest.getMaterial())
			block.setType(Material.AIR);

		save();
	}

	public DeathChest get(Block block) {
		return chests.isEmpty() ? null : chests.get(DeathChest.key(block));
	}

	/** Any death chest or body at this spot (used to keep spots from overlapping). */
	public boolean isDeathChest(Block block) {
		return get(block) != null;
	}

	/** Only real chest blocks, not bodies (the block under a body is just terrain). */
	public DeathChest getChestBlock(Block block) {
		DeathChest chest = get(block);
		return chest != null && !chest.isBody() ? chest : null;
	}

	public boolean isChestBlock(Block block) {
		return getChestBlock(block) != null;
	}

	public boolean isEmpty() {
		return chests.isEmpty();
	}

	public Collection<DeathChest> getChests() {
		return new ArrayList<>(chests.values());
	}

	/**
	 * Keeps holograms and bodies spawned in loaded chunks, and cleans up chests
	 * whose block was removed by something other than a player (e.g. WorldEdit).
	 */
	public void tick() {
		for (DeathChest chest : getChests()) {
			// One bad entry mustn't stop the others from showing
			try {
				if (!chest.isLoaded())
					continue;

				if (!chest.isBody() && chest.getBlock().getType() != chest.getMaterial()) {
					remove(chest, true);
					continue;
				}
				chest.ensureEntities();
			} catch (RuntimeException e) {
				SMPPlugin.getInstance().getLogger().log(Level.WARNING, "Problem with death chest " + chest.getKey(), e);
			}
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
			chest.removeEntities();
		}
	}

	/** "BODY" (or a missing value on a body setting) means a body, stored as a null material. */
	private static Material materialOr(String name) {
		if (BODY.equalsIgnoreCase(name))
			return null;
		Material material = name != null ? Material.matchMaterial(name) : null;
		return material != null && material.isBlock() ? material : Settings.DeathStorageSection.STORAGE_MATERIAL;
	}

	private static Pose poseOr(String name) {
		try {
			return name != null ? Pose.valueOf(name) : Pose.SLEEPING;
		} catch (IllegalArgumentException e) {
			return Pose.SLEEPING;
		}
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
						Material.CHEST, items, null, null, 0, Pose.STANDING);
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

		// Remove them from the old file so they can never be imported twice
		if (legacy.contains("Death_Chests")) {
			legacy.set("Death_Chests", null);
			try {
				legacy.save(legacyFile);
			} catch (java.io.IOException e) {
				SMPPlugin.getInstance().getLogger().log(Level.WARNING, "Could not update data.yml", e);
			}
		}
	}
}
