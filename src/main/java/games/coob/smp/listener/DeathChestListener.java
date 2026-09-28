package games.coob.smp.listener;

import games.coob.smp.duel.DuelManager;
import games.coob.smp.model.DeathChest;
import games.coob.smp.model.DeathChestRegistry;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DeathChestListener implements Listener {

	private static final DeathChestListener instance = new DeathChestListener();

	/** How far up from the death spot we look for room to place the chest. */
	private static final int MAX_SEARCH_UP = 8;
	/** Spots tried for a body, the death spot first. */
	private static final int[][] NEARBY = { { 0, 0 }, { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { -1, -1 },
			{ 1, -1 }, { -1, 1 } };

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
		// No bodies or chests in duel arenas; duels handle their own loot
		if (DuelManager.getInstance().isInDuel(event.getEntity()))
			return;

		final List<ItemStack> items = new ArrayList<>();
		for (ItemStack item : event.getDrops()) {
			if (item != null && !item.getType().isAir())
				items.add(item);
		}
		if (items.isEmpty())
			return;

		final Player player = event.getEntity();
		final boolean useBody = Settings.DeathStorageSection.USE_BODY;
		final Block block = useBody ? findBodySpot(player.getLocation()) : findChestSpot(player.getLocation());
		if (block == null)
			return; // No room (or fell into the void), items drop normally

		// A chest holds at most 54 stacks; anything beyond that drops normally
		final int stored = Math.min(items.size(), 54);
		final ItemStack[] storedItems = items.subList(0, stored).toArray(new ItemStack[0]);
		final DeathChestRegistry registry = DeathChestRegistry.getInstance();
		final DeathChest chest = useBody
				? registry.createBody(block, player, storedItems)
				: registry.create(block, player.getUniqueId(), player.getName(), storedItems);
		if (chest == null)
			return; // Spot taken after all: items drop normally

		event.getDrops().clear();
		event.getDrops().addAll(items.subList(stored, items.size()));

		ColorUtil.sendMessage(player, "&7Your items are " + (useBody ? "with your body" : "in a chest") + " at &e"
				+ chest.getX() + ", " + chest.getY() + ", " + chest.getZ() + "&7. Use &e/track death &7to find it.");

		// Save the (now empty) inventory right away, so a crash can't bring the items back as well
		SchedulerUtil.runLater(1, () -> {
			if (player.isOnline())
				player.saveData();
		});
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

	/**
	 * Where the body lies: on the ground below the death spot (never inside a
	 * wall, and floating on top of lava rather than in it). Null for deaths in
	 * the void.
	 */
	private Block findBodySpot(final Location location) {
		final World world = location.getWorld();
		final int minY = world.getMinHeight();
		final int maxY = world.getMaxHeight() - 2;
		if (location.getY() < minY)
			return null;

		final int x = location.getBlockX();
		final int z = location.getBlockZ();
		int y = Math.clamp(location.getBlockY(), minY, maxY);

		// Out of walls, lava and portals
		for (int i = 0; i < MAX_SEARCH_UP && y < maxY; i++) {
			Block block = world.getBlockAt(x, y, z);
			if (!block.getType().isSolid() && block.getType() != Material.LAVA && !blocksBody(block))
				break;
			y++;
		}
		// Down to the ground (or the surface of water/lava); the column is in a loaded chunk
		final int fallStart = y;
		while (y > minY) {
			Block below = world.getBlockAt(x, y - 1, z);
			if (below.getType().isSolid() || below.isLiquid())
				break;
			y--;
		}
		if (y <= minY)
			y = fallStart; // Nothing below (e.g. over the void in the End): stay where they died

		// Two bodies can't share a spot, and bodies stay off portals and pressure plates
		final DeathChestRegistry registry = DeathChestRegistry.getInstance();
		for (int[] offset : NEARBY) {
			Block spot = world.getBlockAt(x + offset[0], y, z + offset[1]);
			if (!registry.isDeathChest(spot) && !blocksBody(spot))
				return spot;
		}
		return null; // No room: items drop normally
	}

	/** Blocks a body must not lie in: portals would carry it away, plates and tripwires would stay pressed. */
	private static boolean blocksBody(Block block) {
		Material type = block.getType();
		return type == Material.NETHER_PORTAL || type == Material.END_PORTAL || type == Material.END_GATEWAY
				|| type == Material.TRIPWIRE || org.bukkit.Tag.PRESSURE_PLATES.isTagged(type);
	}

	// -------------------------------------------------------------------------
	// Opening / claiming
	// -------------------------------------------------------------------------

	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerInteract(final PlayerInteractEvent event) {
		// Right-clicks fire once per hand; only handle the main hand
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
				|| event.getHand() != EquipmentSlot.HAND)
			return;

		final DeathChest chest = DeathChestRegistry.getInstance().getChestBlock(event.getClickedBlock());
		if (chest == null)
			return;

		event.setCancelled(true);
		open(event.getPlayer(), chest);
	}

	/** Right-clicking a body (or its click box). */
	@EventHandler(priority = EventPriority.HIGH)
	public void onInteractBody(final PlayerInteractEntityEvent event) {
		if (!DeathChestRegistry.isBodyEntity(event.getRightClicked()))
			return;
		event.setCancelled(true);
		if (event.getHand() != EquipmentSlot.HAND)
			return;

		final DeathChest chest = DeathChestRegistry.getInstance().get(event.getRightClicked());
		if (chest != null)
			open(event.getPlayer(), chest);
	}

	/** Fired alongside the event above for some entities; only cancelled here. */
	@EventHandler(priority = EventPriority.HIGH)
	public void onInteractBodyAt(final PlayerInteractAtEntityEvent event) {
		if (DeathChestRegistry.isBodyEntity(event.getRightClicked()))
			event.setCancelled(true);
	}

	/** Hitting a body works like right-clicking it. */
	@EventHandler(priority = EventPriority.HIGH)
	public void onAttackBody(final PrePlayerAttackEntityEvent event) {
		if (!DeathChestRegistry.isBodyEntity(event.getAttacked()))
			return;
		event.setCancelled(true);

		final DeathChest chest = DeathChestRegistry.getInstance().get(event.getAttacked());
		if (chest != null)
			open(event.getPlayer(), chest);
	}

	/**
	 * The owner gets everything back automatically; anyone else opens it and
	 * takes items by hand.
	 */
	private void open(final Player player, final DeathChest chest) {
		if (chest.getOwnerId().equals(player.getUniqueId())) {
			claim(player, chest, false);
		} else {
			player.openInventory(chest.getInventory());
		}
	}

	/**
	 * Gives the owner their items back: armour goes straight onto empty armour
	 * slots, everything else into the inventory. What doesn't fit stays in the
	 * chest, or drops on the ground if the chest is being broken.
	 */
	private void claim(final Player player, final DeathChest chest, final boolean breaking) {
		final DeathChestRegistry registry = DeathChestRegistry.getInstance();
		final PlayerInventory target = player.getInventory();
		final ItemStack[] contents = chest.getInventory().getContents();
		int claimed = 0;

		for (int slot = 0; slot < contents.length; slot++) {
			ItemStack item = contents[slot];
			if (item == null || item.isEmpty())
				continue;

			if (equipIfFree(target, item)) {
				chest.getInventory().setItem(slot, null);
				claimed += item.getAmount();
				continue;
			}

			int before = item.getAmount();
			Map<Integer, ItemStack> leftover = target.addItem(item.clone());
			ItemStack rest = leftover.isEmpty() ? null : leftover.values().iterator().next();
			chest.getInventory().setItem(slot, rest);
			claimed += before - (rest == null ? 0 : rest.getAmount());
		}

		if (claimed > 0) {
			player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.0f);
			chest.markDirty();
			// Save right away, so a crash can't lose the claimed items
			player.saveData();
		}

		if (chest.isEmpty()) {
			registry.remove(chest, false);
			ColorUtil.sendMessage(player, "&aYou claimed all your items.");
		} else if (breaking) {
			registry.remove(chest, true);
			ColorUtil.sendMessage(player, "&eYour inventory is full; the rest of your items dropped on the ground.");
		} else {
			registry.refresh(chest);
			registry.save();
			ColorUtil.sendMessage(player, "&eYour inventory is full; the rest of your items are still "
					+ (chest.isBody() ? "with your body." : "in the chest."));
		}
	}

	/** Puts armour into its slot if that slot is empty. */
	private static boolean equipIfFree(final PlayerInventory inventory, final ItemStack item) {
		final String name = item.getType().name();
		if (name.endsWith("_HELMET") && isEmpty(inventory.getHelmet())) {
			inventory.setHelmet(item);
		} else if ((name.endsWith("_CHESTPLATE") || item.getType() == Material.ELYTRA) && isEmpty(inventory.getChestplate())) {
			inventory.setChestplate(item);
		} else if (name.endsWith("_LEGGINGS") && isEmpty(inventory.getLeggings())) {
			inventory.setLeggings(item);
		} else if (name.endsWith("_BOOTS") && isEmpty(inventory.getBoots())) {
			inventory.setBoots(item);
		} else {
			return false;
		}
		return true;
	}

	private static boolean isEmpty(final ItemStack item) {
		return item == null || item.isEmpty();
	}

	@EventHandler
	public void onInventoryClose(final InventoryCloseEvent event) {
		if (!(event.getInventory().getHolder(false) instanceof DeathChest chest))
			return;

		chest.markDirty();
		final HumanEntity viewer = event.getPlayer();
		// Next tick: removing closes the chest for all viewers, which must not happen inside this close event
		SchedulerUtil.runTask(() -> {
			if (viewer instanceof Player looter && looter.isOnline())
				looter.saveData();
			final DeathChestRegistry registry = DeathChestRegistry.getInstance();
			if (chest.isEmpty()) {
				registry.remove(chest, false);
			} else {
				registry.refresh(chest);
				registry.save();
			}
		});
	}

	// -------------------------------------------------------------------------
	// Bodies can't be hurt, burnt or looted as an entity
	// -------------------------------------------------------------------------

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBodyDamage(final EntityDamageEvent event) {
		if (event.getEntity() instanceof Mannequin && DeathChestRegistry.isBodyEntity(event.getEntity()))
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBodyCombust(final EntityCombustEvent event) {
		if (event.getEntity() instanceof Mannequin && DeathChestRegistry.isBodyEntity(event.getEntity()))
			event.setCancelled(true);
	}

	/** Bodies never travel through portals or get teleported. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBodyPortal(final EntityPortalEvent event) {
		if (DeathChestRegistry.isBodyEntity(event.getEntity()))
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBodyTeleport(final EntityTeleportEvent event) {
		if (DeathChestRegistry.isBodyEntity(event.getEntity()))
			event.setCancelled(true);
	}

	/**
	 * Older versions of the plugin used persistent armor stands as "X's loot"
	 * holograms, and some were left behind. Remove them when their chunk loads.
	 */
	@EventHandler
	public void onEntitiesLoad(final EntitiesLoadEvent event) {
		for (Entity entity : event.getEntities()) {
			if (entity instanceof ArmorStand stand && stand.isMarker() && stand.isSmall() && !stand.isVisible()
					&& stand.isCustomNameVisible() && stand.customName() != null
					&& PlainTextComponentSerializer.plainText().serialize(stand.customName()).endsWith("'s loot"))
				stand.remove();
		}
	}

	/** e.g. /kill: the armour a body wears is only a copy, so it must never drop. */
	@EventHandler
	public void onBodyDeath(final EntityDeathEvent event) {
		if (event.getEntity() instanceof Mannequin && DeathChestRegistry.isBodyEntity(event.getEntity())) {
			event.getDrops().clear();
			event.setDroppedExp(0);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockBreak(final BlockBreakEvent event) {
		final DeathChest chest = DeathChestRegistry.getInstance().getChestBlock(event.getBlock());
		if (chest != null) {
			// The block is removed by the registry; don't drop a free chest item
			event.setCancelled(true);
			if (chest.getOwnerId().equals(event.getPlayer().getUniqueId())) {
				claim(event.getPlayer(), chest, true);
			} else {
				DeathChestRegistry.getInstance().remove(chest, true);
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onEntityExplode(final EntityExplodeEvent event) {
		final DeathChestRegistry registry = DeathChestRegistry.getInstance();
		if (!registry.isEmpty())
			event.blockList().removeIf(registry::isChestBlock);
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockExplode(final BlockExplodeEvent event) {
		final DeathChestRegistry registry = DeathChestRegistry.getInstance();
		if (!registry.isEmpty())
			event.blockList().removeIf(registry::isChestBlock);
	}
}
