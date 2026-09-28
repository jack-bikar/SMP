package games.coob.smp.model;

import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * A chest holding a dead player's items. The chest block in the world stays
 * empty; its contents live in this virtual inventory, which is saved to
 * death-chests.yml.
 */
@Getter
public final class DeathChest implements InventoryHolder {

	private final String worldName;
	private final int x;
	private final int y;
	private final int z;
	private final UUID ownerId;
	private final String ownerName;
	/** The block type this chest was placed as (kept even if the setting changes later). */
	private final Material material;
	private final Inventory inventory;

	/** Non-persistent hologram entity; respawned whenever the chunk is loaded. */
	private TextDisplay hologram;

	DeathChest(String worldName, int x, int y, int z, UUID ownerId, String ownerName, Material material,
			ItemStack[] items) {
		this.worldName = worldName;
		this.x = x;
		this.y = y;
		this.z = z;
		this.ownerId = ownerId;
		this.ownerName = ownerName;
		this.material = material;

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
		return new Location(getWorld(), x + 0.5, y + 0.5, z + 0.5);
	}

	public boolean isEmpty() {
		return inventory.isEmpty();
	}

	/** Spawns the hologram if the chunk is loaded and it isn't there yet. */
	void ensureHologram() {
		if (hologram != null && hologram.isValid())
			return;
		hologram = null;

		World world = getWorld();
		if (world == null || !isLoaded())
			return;

		String text = Settings.DeathStorageSection.HOLOGRAM_TEXT.replace("{player}", ownerName);
		Location location = new Location(world, x + 0.5, y + 1.3, z + 0.5);
		hologram = world.spawn(location, TextDisplay.class, display -> {
			display.text(ColorUtil.toComponent(text));
			display.setBillboard(Display.Billboard.CENTER);
			display.setPersistent(false);
			display.setShadowed(true);
			// Display view range is a multiplier of 64 blocks
			display.setViewRange(Settings.DeathStorageSection.HOLOGRAM_VISIBLE_RANGE / 64f);
		});
	}

	void removeHologram() {
		if (hologram != null) {
			hologram.remove();
			hologram = null;
		}
	}
}
