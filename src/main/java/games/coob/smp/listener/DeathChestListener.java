package games.coob.smp.listener;

import games.coob.smp.model.DeathChest;
import games.coob.smp.model.DeathChestRegistry;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DeathChestListener implements Listener {

	private static final DeathChestListener instance = new DeathChestListener();

	/** How far up from the death spot we look for room to place the chest. */
	private static final int MAX_SEARCH_UP = 8;

	public static DeathChestListener getInstance() {
		return instance;
	}

	/**
	 * Runs last so other plugins (and keepInventory) have already decided what drops.
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerDeath(final PlayerDeathEvent event) {
		if (!Settings.DeathStorageSection.ENABLE_DEATH_STORAGE || event.getKeepInventory())
			return;

		final List<ItemStack> items = new ArrayList<>();
		for (ItemStack item : event.getDrops()) {
			if (item != null && !item.getType().isAir())
				items.add(item);
		}
		if (items.isEmpty())
			return;

		final Player player = event.getEntity();
		final Block block = findChestSpot(player.getLocation());
		if (block == null)
			return; // No room for a chest, items drop normally

		// A chest holds at most 54 stacks; anything beyond that drops normally
		final int stored = Math.min(items.size(), 54);
		final DeathChest chest = DeathChestRegistry.getInstance().create(block, player.getUniqueId(), player.getName(),
				items.subList(0, stored).toArray(new ItemStack[0]));

		event.getDrops().clear();
		event.getDrops().addAll(items.subList(stored, items.size()));

		ColorUtil.sendMessage(player, "&7Your items are in a chest at &e" + chest.getX() + ", " + chest.getY() + ", "
				+ chest.getZ() + "&7. Use &e/track death &7to find it.");
	}

	/**
	 * Finds an air, liquid or replaceable block (grass, snow...) at or just above the
	 * death location. Solid blocks are never replaced.
	 */
	private Block findChestSpot(final Location location) {
		final World world = location.getWorld();
		final int minY = world.getMinHeight();
		final int maxY = world.getMaxHeight() - 1;
		final int startY = Math.clamp(location.getBlockY(), minY, maxY);
		final DeathChestRegistry registry = DeathChestRegistry.getInstance();

		for (int y = startY; y <= Math.min(startY + MAX_SEARCH_UP, maxY); y++) {
			final Block block = world.getBlockAt(location.getBlockX(), y, location.getBlockZ());
			if ((block.getType().isAir() || block.isLiquid() || block.isReplaceable()) && !registry.isDeathChest(block))
				return block;
		}
		return null;
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerInteract(final PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null)
			return;

		final DeathChest chest = DeathChestRegistry.getInstance().get(event.getClickedBlock());
		if (chest != null) {
			event.setCancelled(true);
			event.getPlayer().openInventory(chest.getInventory());
		}
	}

	@EventHandler
	public void onInventoryClose(final InventoryCloseEvent event) {
		if (!(event.getInventory().getHolder(false) instanceof DeathChest chest))
			return;

		// Next tick: removing closes the chest for all viewers, which must not happen inside this close event
		SchedulerUtil.runTask(() -> {
			final DeathChestRegistry registry = DeathChestRegistry.getInstance();
			if (chest.isEmpty()) {
				registry.remove(chest, false);
			} else {
				registry.save();
			}
		});
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockBreak(final BlockBreakEvent event) {
		final DeathChest chest = DeathChestRegistry.getInstance().get(event.getBlock());
		if (chest != null) {
			// The block is removed by the registry; don't drop a free chest item
			event.setCancelled(true);
			DeathChestRegistry.getInstance().remove(chest, true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onEntityExplode(final EntityExplodeEvent event) {
		final DeathChestRegistry registry = DeathChestRegistry.getInstance();
		if (!registry.isEmpty())
			event.blockList().removeIf(registry::isDeathChest);
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockExplode(final BlockExplodeEvent event) {
		final DeathChestRegistry registry = DeathChestRegistry.getInstance();
		if (!registry.isEmpty())
			event.blockList().removeIf(registry::isDeathChest);
	}
}
