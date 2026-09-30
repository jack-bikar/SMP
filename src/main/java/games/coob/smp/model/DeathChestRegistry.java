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
import org.bukkit.World;
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
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * All death chests on the server, stored in death-chests.yml.
 */
public final class DeathChestRegistry extends ConfigFile {

	@Getter
	private static final DeathChestRegistry instance = new DeathChestRegistry();

	public static final String BODY = "BODY";

	private Map<String, DeathChest> chests;
	/** How many entries are chest blocks rather than bodies. */
	private int chestBlocks;

	private DeathChestRegistry() {
		super("death-chests.yml");
	}

	@Override
	protected void onLoad() {
		chests = new HashMap<>();
		chestBlocks = 0;

		ConfigurationSection section = getConfig().getConfigurationSection("Chests");
		if (section != null) {
			for (String id : section.getKeys(false)) {
				ConfigurationSection data = section.getConfigurationSection(id);
				if (data == null)
					continue;
				try {
					String items = data.getString("Items");
					DeathChest chest = new DeathChest(
							data.getString("World"),
							data.getInt("X"), data.getInt("Y"), data.getInt("Z"),
							UUID.fromString(data.getString("Owner")),
							data.getString("Owner_Name", "Unknown"),
							materialOr(data.getString("Material")),
							InventorySerialization.fromBase64(items),
							data.getString("Skin.Value"), data.getString("Skin.Signature"),
							(float) data.getDouble("Yaw"), poseOr(data.getString("Pose")), placementOf(data));
					chest.primeSavedItems(items);
					chests.put(chest.getKey(), chest);
					if (!chest.isBody())
						chestBlocks++;
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
				BodyPlacement placement = chest.getPlacement();
				getConfig().set(path + "Body.X", placement.x());
				getConfig().set(path + "Body.Y", placement.y());
				getConfig().set(path + "Body.Z", placement.z());
				getConfig().set(path + "Body.Body_Yaw", placement.bodyYaw());
				getConfig().set(path + "Body.Pitch", placement.pitch());
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
				ownerId, ownerName, material, items, null, null, 0, Pose.STANDING, null));
	}

	/**
	 * Lays the player's body down (no block is placed) holding the items.
	 *
	 * @param placement where and how the body lies, from {@link BodyPlacer}
	 */
	public DeathChest createBody(World world, BodyPlacement placement, Player owner, ItemStack[] items) {
		String skinValue = null;
		String skinSignature = null;
		for (ProfileProperty property : owner.getPlayerProfile().getProperties()) {
			if (property.getName().equals("textures")) {
				skinValue = property.getValue();
				skinSignature = property.getSignature();
			}
		}

		// The body's middle names the spot (two bodies can't share one)
		Vector center = placement.center();
		int x = (int) Math.floor(center.getX());
		int y = (int) Math.floor(center.getY() + 0.01);
		int z = (int) Math.floor(center.getZ());
		return register(new DeathChest(world.getName(), x, y, z, owner.getUniqueId(), owner.getName(), null, items,
				skinValue, skinSignature, placement.bodyYaw(), placement.pose(), placement));
	}

	/**
	 * A check for whether a new body near {@code death} would lie on another
	 * one, or its spot is already used. Only bodies close by are compared.
	 */
	public Predicate<BodyPlacement> takenNear(Location death) {
		World world = death.getWorld();
		// Worked out once: thousands of placements may be checked against them
		List<Vector[]> nearby = new ArrayList<>();
		for (DeathChest chest : chests.values()) {
			if (chest.getPlacement() == null || !chest.getWorldName().equals(world.getName()))
				continue;
			Vector center = chest.getPlacement().center();
			if (Math.abs(center.getX() - death.getX()) < 6 && Math.abs(center.getZ() - death.getZ()) < 6
					&& Math.abs(center.getY() - death.getY()) < 6)
				nearby.add(chest.getPlacement().axis());
		}
		return placement -> {
			Vector[] axis = placement.axis();
			if (!nearby.isEmpty()) {
				for (Vector[] other : nearby) {
					if (BodyPlacement.axesOverlap(axis, other))
						return true;
				}
			}
			Vector center = placement.center();
			return chests.containsKey(DeathChest.key(world.getName(), (int) Math.floor(center.getX()),
					(int) Math.floor(center.getY() + 0.01), (int) Math.floor(center.getZ())));
		};
	}

	/** Saved exact body position, or null for bodies saved before it existed. */
	private static BodyPlacement placementOf(ConfigurationSection data) {
		if (!data.isSet("Body.X"))
			return null;
		return new BodyPlacement(data.getDouble("Body.X"), data.getDouble("Body.Y"), data.getDouble("Body.Z"),
				(float) data.getDouble("Body.Body_Yaw", data.getDouble("Yaw")),
				(float) data.getDouble("Body.Pitch"), poseOr(data.getString("Pose")));
	}

	/** Null if that spot is already taken (the caller lets the items drop normally). */
	private DeathChest register(DeathChest chest) {
		if (chests.putIfAbsent(chest.getKey(), chest) != null)
			return null;
		if (!chest.isBody())
			chestBlocks++;
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
		if (removeWithoutSaving(chest, dropItems))
			save();
	}

	/** {@link #remove} without the save, for removing several at once. */
	private boolean removeWithoutSaving(DeathChest chest, boolean dropItems) {
		// Already removed (e.g. two viewers closed it at once)
		if (!chests.remove(chest.getKey(), chest))
			return false;
		if (!chest.isBody())
			chestBlocks--;
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
		return true;
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
		boolean removed = false;
		for (DeathChest chest : getChests()) {
			// One bad entry mustn't stop the others from showing
			try {
				if (!chest.isLoaded()) {
					chest.forgetUnloadedEntities();
					continue;
				}

				if (!chest.isBody() && chest.getBlock().getType() != chest.getMaterial()) {
					removed |= removeWithoutSaving(chest, true);
					continue;
				}
				chest.ensureEntities();
			} catch (RuntimeException e) {
				SMPPlugin.getInstance().getLogger().log(Level.WARNING, "Problem with death chest " + chest.getKey(), e);
			}
		}
		if (removed)
			save();
	}

	/** Chests (not bodies) that are real blocks: explosions only need checking when there are some. */
	public boolean hasChestBlocks() {
		return chestBlocks > 0;
	}

	/** Every chest's items in one file: built in the background, it can be megabytes after a while. */
	@Override
	protected boolean isSerializedInBackground() {
		return true;
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
						Material.CHEST, items, null, null, 0, Pose.STANDING, null);
				if (!chest.isEmpty()) {
					chests.put(chest.getKey(), chest);
					chestBlocks++;
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
