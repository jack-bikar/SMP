package games.coob.smp.model;

import com.destroystokyo.paper.profile.ProfileProperty;
import games.coob.smp.SMPPlugin;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * A dead player's items: either in a chest block, or with the player's body
 * lying where they died (Storage_Material: BODY). The contents live in this
 * virtual inventory, which is saved to death-chests.yml; the chest block stays
 * empty and the body is only a display.
 */
@Getter
public final class DeathChest implements InventoryHolder {

	/** Marks the body and its click box, holding the death chest's key. */
	static final NamespacedKey ENTITY_KEY = new NamespacedKey(SMPPlugin.getInstance(), "death_chest");

	/** The body's click box: lying bodies have a tiny hitbox of their own. */
	private static final float CLICK_BOX_WIDTH = 2.4f;
	private static final float CLICK_BOX_HEIGHT = 0.7f;
	private static final float SITTING_CLICK_BOX_WIDTH = 1.4f;

	private final String worldName;
	private final int x;
	private final int y;
	private final int z;
	private final UUID ownerId;
	private final String ownerName;
	/** The block type this chest was placed as, or null for a body. Kept even if the setting changes later. */
	private final Material material;
	private final Inventory inventory;

	// Body look (only for bodies)
	private final String skinValue;
	private final String skinSignature;
	private final float yaw;
	private final Pose pose;
	/** Exactly where and how the body lies; null for chests and bodies saved before placements existed. */
	private final BodyPlacement placement;

	/** Encoded contents from the last save; re-encoded only after the contents change. */
	private String savedItems;
	private boolean dirty = true;
	/** Clicked in since it was last closed (so closing without taking anything needs no save). */
	private boolean touched;
	/** When mounting the seat last failed (another plugin cancelled it): don't retry every tick. */
	private long seatFailedAt;

	// Non-persistent entities; respawned whenever the chunk is loaded
	private TextDisplay hologram;
	private Mannequin body;
	/** Invisible entity a sitting body rides on. */
	private ItemDisplay seat;
	private Interaction clickBox;

	DeathChest(String worldName, int x, int y, int z, UUID ownerId, String ownerName, Material material,
			ItemStack[] items, String skinValue, String skinSignature, float yaw, Pose pose, BodyPlacement placement) {
		this.worldName = worldName;
		this.x = x;
		this.y = y;
		this.z = z;
		this.ownerId = ownerId;
		this.ownerName = ownerName;
		this.material = material;
		this.skinValue = skinValue;
		this.skinSignature = skinSignature;
		this.yaw = yaw;
		this.pose = pose;
		this.placement = placement != null || material != null ? placement : legacyPlacement(x, y, z, yaw, pose);

		int size = Math.clamp((items.length + 8) / 9 * 9, 9, 54);
		this.inventory = Bukkit.createInventory(this, size,
				ColorUtil.toComponent("&8" + ownerName + "'s items"));
		for (int i = 0; i < items.length && i < size; i++)
			inventory.setItem(i, items[i]);
	}

	@Override
	public @NonNull Inventory getInventory() {
		return inventory;
	}

	public boolean isBody() {
		return material == null;
	}

	public String getKey() {
		return key(worldName, x, y, z);
	}

	static String key(String worldName, int x, int y, int z) {
		return worldName + ":" + x + ":" + y + ":" + z;
	}

	static String key(Block block) {
		return key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
	}

	public World getWorld() {
		return Bukkit.getWorld(worldName);
	}

	public boolean isLoaded() {
		World world = getWorld();
		return world != null && world.isChunkLoaded(x >> 4, z >> 4);
	}

	public Block getBlock() {
		World world = getWorld();
		return world == null ? null : world.getBlockAt(x, y, z);
	}

	public Location getDropLocation() {
		if (placement != null) {
			Vector center = placement.center();
			return new Location(getWorld(), center.getX(), center.getY() + 0.5, center.getZ());
		}
		return new Location(getWorld(), x + 0.5, y + 0.5, z + 0.5);
	}

	/** Middle of the body at ground level, or null for a chest. */
	public Vector getBodyCenter() {
		return placement != null ? placement.center() : null;
	}

	/** Bodies from before placements were worked out: on the back or face down at the block's centre. */
	private static BodyPlacement legacyPlacement(int x, int y, int z, float yaw, Pose pose) {
		Pose lying = pose == Pose.SWIMMING ? Pose.SWIMMING : Pose.SLEEPING;
		double lift = lying == Pose.SLEEPING ? 0.12 : 0.0;
		return new BodyPlacement(x + 0.5, y + lift, z + 0.5, yaw, 0, lying);
	}

	public boolean isEmpty() {
		return inventory.isEmpty();
	}

	/** Call after the contents changed (claimed, looted). */
	public void markDirty() {
		dirty = true;
	}

	/** The encoded contents it was loaded from: no need to encode them again until they change. */
	void primeSavedItems(String encoded) {
		savedItems = encoded;
		dirty = encoded == null;
	}

	/** Someone clicked in the open chest (maybe took or moved something). */
	public void markTouched() {
		touched = true;
	}

	/** Whether anyone clicked in it since the last call. */
	public boolean takeTouched() {
		boolean was = touched;
		touched = false;
		return was;
	}

	/** Drops references to entities that went away with an unloaded chunk, so they can be freed. */
	void forgetUnloadedEntities() {
		if (hologram != null && !hologram.isValid())
			hologram = null;
		if (body != null && !body.isValid())
			body = null;
		if (seat != null && !seat.isValid())
			seat = null;
		if (clickBox != null && !clickBox.isValid())
			clickBox = null;
	}

	/** The contents for saving; encoding every item is slow, so it is cached until something changes. */
	String encodedItems() {
		if (dirty || savedItems == null) {
			savedItems = games.coob.smp.util.InventorySerialization.toBase64(inventory.getContents());
			dirty = false;
		}
		return savedItems;
	}

	// -------------------------------------------------------------------------
	// Entities
	// -------------------------------------------------------------------------

	/** Spawns the hologram (and body) if the chunk is loaded and they aren't there yet. */
	void ensureEntities() {
		World world = getWorld();
		if (world == null || !isLoaded())
			return;

		if (hologram == null || !hologram.isValid())
			spawnHologram(world);

		if (isBody()) {
			boolean seated = !placement.isSitting() || (seat != null && seat.isValid() && seat.getPassengers().contains(body))
					|| System.currentTimeMillis() - seatFailedAt < 30_000;
			if (body == null || !body.isValid() || !seated) {
				removeBody();
				spawnBody(world);
			}
			if (clickBox == null || !clickBox.isValid())
				spawnClickBox(world);
		}
	}

	private void spawnHologram(World world) {
		String text = Settings.DeathStorageSection.HOLOGRAM_TEXT.replace("{player}", ownerName);
		Location location;
		if (isBody()) {
			Vector center = placement.center();
			location = new Location(world, center.getX(), center.getY() + placement.topAboveCenter() + 0.5, center.getZ());
		} else {
			location = new Location(world, x + 0.5, y + 1.3, z + 0.5);
		}
		hologram = world.spawn(location, TextDisplay.class, display -> {
			display.text(ColorUtil.toComponent(text));
			display.setBillboard(Display.Billboard.CENTER);
			display.setPersistent(false);
			display.setShadowed(true);
			// Display view range is a multiplier of 64 blocks
			display.setViewRange(Settings.DeathStorageSection.HOLOGRAM_VISIBLE_RANGE / 64f);
		});
	}

	private void spawnBody(World world) {
		Location location = placement.location(world);
		// A sitting body rides an invisible seat; standing is the pose that looks seated while riding
		Pose modelPose = placement.isSitting() ? Pose.STANDING : placement.pose();

		// Spawned facing its body yaw: clients take the body's direction from the yaw it appears with
		body = world.spawn(location, Mannequin.class, mannequin -> {
			mannequin.setProfile(buildProfile());
			mannequin.setPose(modelPose, true);
			mannequin.setDescription(null);
			mannequin.setImmovable(true);
			mannequin.setGravity(false);
			mannequin.setInvulnerable(true);
			mannequin.setSilent(true);
			mannequin.setCollidable(false);
			mannequin.setCanPickupItems(false);
			mannequin.setPersistent(false);
			// Never goes through portals (a body lying in one would otherwise travel and respawn endlessly)
			mannequin.setPortalCooldown(Integer.MAX_VALUE);
			mannequin.getPersistentDataContainer().set(ENTITY_KEY, PersistentDataType.STRING, getKey());
		});

		if (placement.isSitting()) {
			seat = world.spawn(new Location(world, placement.x(), placement.y(), placement.z(), placement.bodyYaw(), 0),
					ItemDisplay.class, display -> {
						display.setPersistent(false);
						display.getPersistentDataContainer().set(ENTITY_KEY, PersistentDataType.STRING, getKey());
					});
			if (!seat.addPassenger(body))
				seatFailedAt = System.currentTimeMillis();
		}
		updateBodyArmour();
	}

	private void removeBody() {
		if (body != null)
			body.remove();
		if (seat != null)
			seat.remove();
		body = null;
		seat = null;
	}

	private void spawnClickBox(World world) {
		Vector center = placement.center();
		Location location = new Location(world, center.getX(), center.getY(), center.getZ());
		boolean sitting = placement.isSitting();
		clickBox = world.spawn(location, Interaction.class, interaction -> {
			interaction.setInteractionWidth(sitting ? SITTING_CLICK_BOX_WIDTH : CLICK_BOX_WIDTH);
			interaction.setInteractionHeight(sitting ? (float) BodyShape.SIT_HEIGHT : CLICK_BOX_HEIGHT);
			interaction.setResponsive(true);
			interaction.setPersistent(false);
			interaction.getPersistentDataContainer().set(ENTITY_KEY, PersistentDataType.STRING, getKey());
		});
	}

	/** The owner's skin, from the textures saved when they died (no web lookups). */
	private ResolvableProfile buildProfile() {
		ResolvableProfile.Builder builder = ResolvableProfile.resolvableProfile().uuid(ownerId).name(ownerName);
		if (skinValue != null)
			builder.addProperty(new ProfileProperty("textures", skinValue, skinSignature));
		return builder.build();
	}

	/**
	 * The body wears whatever armour is still in the loot, so it visibly loses
	 * its gear as people take it. Display only: the body's items can't be taken.
	 */
	void updateBodyArmour() {
		if (body == null || !body.isValid())
			return;

		ItemStack helmet = null, chestplate = null, leggings = null, boots = null;
		for (ItemStack item : inventory.getContents()) {
			if (item == null || item.isEmpty())
				continue;
			String name = item.getType().name();
			if (helmet == null && name.endsWith("_HELMET"))
				helmet = item.clone();
			else if (chestplate == null && (name.endsWith("_CHESTPLATE") || item.getType() == Material.ELYTRA))
				chestplate = item.clone();
			else if (leggings == null && name.endsWith("_LEGGINGS"))
				leggings = item.clone();
			else if (boots == null && name.endsWith("_BOOTS"))
				boots = item.clone();
		}

		EntityEquipment equipment = body.getEquipment();
		equipment.setHelmet(helmet);
		equipment.setChestplate(chestplate);
		equipment.setLeggings(leggings);
		equipment.setBoots(boots);
	}

	void removeEntities() {
		for (Entity entity : new Entity[] { hologram, body, seat, clickBox }) {
			if (entity != null)
				entity.remove();
		}
		hologram = null;
		body = null;
		seat = null;
		clickBox = null;
	}
}
