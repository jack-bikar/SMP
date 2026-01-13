package games.coob.smp.listener;

import games.coob.smp.PlayerCache;
import games.coob.smp.hologram.BukkitHologram;
import games.coob.smp.hologram.HologramProvider;
import games.coob.smp.model.DeathChestRegistry;
import games.coob.smp.settings.Settings;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.fo.remain.Remain;

import games.coob.smp.hologram.BukkitHologram;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DeathChestListener implements Listener {

	@Getter
	private static final DeathChestListener instance = new DeathChestListener();

	@EventHandler
	public void onPlayerInteract(final PlayerInteractEvent event) {
		final Player player = event.getPlayer();

		if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			final Block block = event.getClickedBlock();
			final DeathChestRegistry registry = DeathChestRegistry.getInstance();

			if (block != null && block.getType() == Settings.DeathStorageSection.STORAGE_MATERIAL.getMaterial() && registry.isRegistered(block)) {
				event.setCancelled(true);
				player.openInventory(registry.getInventory(block));

				if (block.getType().toString().contains("CHEST"))
					Remain.sendChestOpen(block);
			}
		}
	}

	@EventHandler
	public void onPlayerDeath(final PlayerDeathEvent event) {
		if (Settings.DeathStorageSection.ENABLE_DEATH_STORAGE) {
			final Player player = event.getEntity();
			final Location location = player.getLocation();
			final Block block = location.getBlock();
			final PlayerCache cache = PlayerCache.from(player);
			final DeathChestRegistry registry = DeathChestRegistry.getInstance();
			final BukkitHologram hologram = HologramProvider.createHologram();
			final ItemStack[] drops = Stream.of(player.getInventory().getContents()) // Create a stream of ItemStack
					.filter(Objects::nonNull) // Filter all non null values (removing empty slot)
					.toArray(ItemStack[]::new); // Convert the result to ItemStack array

			if (player.getInventory().isEmpty())
				return;

			if (player.getWorld().getEnvironment() == World.Environment.NORMAL)
				cache.setDeathLocation(location);
			else {
				location.setY(1);
				cache.setDeathLocation(location);
			}

			for (final ItemStack itemStack : Arrays.stream(drops).toList())
				event.getDrops().remove(itemStack);

			Inventory inventory = null;

			if (drops.length <= 9)
				inventory = Bukkit.createInventory(player, 9);
			else if (drops.length <= 18)
				inventory = Bukkit.createInventory(player, 18);
			else if (drops.length <= 27)
				inventory = Bukkit.createInventory(player, 27);
			else if (drops.length <= 36)
				inventory = Bukkit.createInventory(player, 36);
			else if (drops.length <= 45)
				inventory = Bukkit.createInventory(player, 45);

			assert inventory != null;
			block.setType(Settings.DeathStorageSection.STORAGE_MATERIAL.getMaterial());

			for (final Player closePlayers : Remain.getOnlinePlayers()) // TODO
				if (closePlayers.getLocation().distance(block.getLocation().clone().add(0.5, -0.75, 0.5)) < Settings.DeathStorageSection.HOLOGRAM_VISIBLE_RANGE)
					hologram.show(block.getLocation().clone().add(0.5, -0.75, 0.5), player, chestOwnerMessage(Settings.DeathStorageSection.HOLOGRAM_TEXT, player));

			inventory.setContents(drops);
			cache.setDeathChestInventory(inventory);

			if (!registry.isRegistered(block))
				registry.register(block, player, hologram);
		}
	}

	private String chestOwnerMessage(final String message, final Player player) {
		if (message.contains("{player}"))
			return message.replace("{player}", player.getName());

		return message;
	}

	@EventHandler
	public void onInventoryClose(final InventoryCloseEvent event) {
		final HumanEntity human = event.getPlayer();

		if (human instanceof Player) {
			final DeathChestRegistry registry = DeathChestRegistry.getInstance();

			for (final Location location : registry.getLocations()) {
				final Block block = registry.getBlock(location);

				if (registry.getInventory(block).isEmpty()) {
					block.setType(Material.AIR);

					final BukkitHologram hologram = registry.getHologram(block);
					if (hologram != null) {
						// Hide from all players and remove armor stands
						hologram.removeAll();
					}

					registry.unregister(block);
				}

				if (block.getType().toString().contains("CHEST"))
					Remain.sendChestClose(block);
			}
		}
	}

	@EventHandler
	public void onBlockBreak(final BlockBreakEvent event) {
		if (Settings.DeathStorageSection.ENABLE_DEATH_STORAGE) {
			final DeathChestRegistry registry = DeathChestRegistry.getInstance();
			final Block block = event.getBlock();

			if (registry.isRegistered(block)) {
				final ItemStack[] items = Stream.of(registry.getInventory(block).getContents()) // Create a stream of ItemStack
						.filter(Objects::nonNull) // Filter all non null values (removing empty slot)
						.toArray(ItemStack[]::new); // Convert the result to ItemStack array

				for (final ItemStack item : items) {
					block.getWorld().dropItem(block.getLocation(), item);

					if (item.getType() == Settings.DeathStorageSection.STORAGE_MATERIAL.getMaterial())
						item.setType(Material.AIR);
				}

				final BukkitHologram hologram = registry.getHologram(block);
				if (hologram != null) {
					// Hide from all players and remove armor stands
					hologram.removeAll();
				}

				registry.unregister(block);
			}
		}
	}
}
