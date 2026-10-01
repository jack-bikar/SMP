package games.coob.smp.duel.kit;

import games.coob.smp.duel.ActiveDuel;
import games.coob.smp.duel.DuelManager;
import games.coob.smp.menu.SimpleMenu;
import games.coob.smp.util.ColorUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;

/**
 * Kit items never leave the duel they were given in:
 * <ul>
 * <li>During a kit duel, players can't open containers (chests, ender chests,
 * furnaces...) or put items into item frames, armour stands, allays or animals.</li>
 * <li>Items dropped in a kit duel can only be picked up by its players (not by
 * outsiders, mobs or hoppers), and are removed when the duel is cleaned up.</li>
 * <li>Kit items found anywhere else are removed: on pickup, in containers anyone
 * opens, and in players' inventories when they join.</li>
 * </ul>
 * Players' own gear is stored before the kit and given back after (see {@link KitStash}).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DuelKitListener implements Listener {

	private static final DuelKitListener instance = new DuelKitListener();

	public static DuelKitListener getInstance() {
		return instance;
	}

	private static ActiveDuel kitDuelOf(Player player) {
		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(player);
		return duel != null && duel.isKits() ? duel : null;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onOpen(final InventoryOpenEvent event) {
		if (!(event.getPlayer() instanceof Player player))
			return;
		// The plugin's own menus (the kit menu itself) are fine
		if (event.getInventory().getHolder(false) instanceof SimpleMenu)
			return;

		if (kitDuelOf(player) != null) {
			InventoryType type = event.getInventory().getType();
			if (type != InventoryType.PLAYER && type != InventoryType.CRAFTING && type != InventoryType.WORKBENCH) {
				event.setCancelled(true);
				ColorUtil.sendMessage(player, "&cYou can't use containers during a kit duel.");
			}
			return;
		}
		// Someone outside a kit duel: whatever kit item ended up in here goes. Not a duelist's own
		// inventory though (an admin looking with /inv): that kit belongs where it is.
		if (event.getInventory().getHolder(false) instanceof Player holder && kitDuelOf(holder) != null)
			return;
		KitStash.removeKitItems(event.getInventory());
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onInteractEntity(final PlayerInteractEntityEvent event) {
		if (kitDuelOf(event.getPlayer()) == null)
			return;
		if (event.getRightClicked() instanceof ItemFrame || event.getRightClicked() instanceof Allay
				|| event.getRightClicked() instanceof ChestedHorse)
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onArmorStand(final PlayerArmorStandManipulateEvent event) {
		if (kitDuelOf(event.getPlayer()) != null)
			event.setCancelled(true);
	}

	/**
	 * Only the duel's own players pick up what was dropped in it, and kit duelists
	 * pick up nothing else: their inventory is replaced by their own gear after
	 * the duel, so anything else they picked up would be lost.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPickup(final EntityPickupItemEvent event) {
		Item item = event.getItem();
		ActiveDuel droppedIn = kitDuelOfItem(item);
		boolean kitItem = DuelKit.isKitItem(item.getItemStack());
		ActiveDuel picker = event.getEntity() instanceof Player player ? kitDuelOf(player) : null;

		if (droppedIn == null && !kitItem) {
			// Someone's own item (or one lying in the arena): it stays there for its owner
			if (picker != null)
				event.setCancelled(true);
			return;
		}
		boolean allowed = picker != null && (droppedIn == null || droppedIn == picker);
		if (!allowed) {
			event.setCancelled(true);
			item.remove();
		}
	}

	/** Kit arrows that flew out of the arena (arrows are picked up through their own event). */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPickupArrow(final PlayerPickupArrowEvent event) {
		boolean kitArrow = DuelKit.isKitItem(event.getArrow().getItemStack()) || DuelKit.isKitItem(event.getItem().getItemStack());
		ActiveDuel picker = kitDuelOf(event.getPlayer());
		if (kitArrow && picker == null) {
			event.setCancelled(true);
			event.getArrow().remove();
		} else if (!kitArrow && picker != null) {
			event.setCancelled(true);
		}
	}

	/** Own items can't be dropped while a kit duel starts: after that they are put away until it ends. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDrop(final PlayerDropItemEvent event) {
		ActiveDuel duel = kitDuelOf(event.getPlayer());
		if (duel != null && duel.getState() == ActiveDuel.DuelState.PREPARING) {
			event.setCancelled(true);
			ColorUtil.sendMessage(event.getPlayer(), "&cYou can't drop items while a kit duel starts.");
		}
	}

	/**
	 * Blocks that take items without opening anything (decorated pots, shelves,
	 * lecterns, jukeboxes, campfires, composters, cauldrons...) are off limits in a
	 * kit duel too.
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onUseBlock(final PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
				|| kitDuelOf(event.getPlayer()) == null)
			return;
		Block block = event.getClickedBlock();
		Material type = block.getType();
		boolean takesItems = block.getState(false) instanceof TileState || type == Material.COMPOSTER
				|| Tag.CAULDRONS.isTagged(type);
		if (takesItems) {
			event.setUseInteractedBlock(Event.Result.DENY);
			if (event.getItem() == null || !event.getItem().getType().isBlock())
				event.setUseItemInHand(Event.Result.DENY);
			// Sneaking players place blocks against it instead, no need to tell them
			if (!event.getPlayer().isSneaking())
				ColorUtil.sendMessage(event.getPlayer(), "&cYou can't use containers during a kit duel.");
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onHopperPickup(final InventoryPickupItemEvent event) {
		Item item = event.getItem();
		if (DuelKit.isKitItem(item.getItemStack()) || kitDuelOfItem(item) != null) {
			event.setCancelled(true);
			item.remove();
		}
	}

	/** What a block breaks into during a kit duel stays in the duel too. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockDrop(final BlockDropItemEvent event) {
		ActiveDuel duel = kitDuelOf(event.getPlayer());
		if (duel == null)
			return;
		for (Item item : event.getItems())
			duel.trackDroppedItem(item.getUniqueId());
	}

	private static ActiveDuel kitDuelOfItem(Item item) {
		// Duels whose arena is still protected: their items are removed at cleanup, a little after the end
		for (ActiveDuel duel : DuelManager.getInstance().getProtectedDuels()) {
			if (duel.isKits() && duel.isDroppedHere(item.getUniqueId()))
				return duel;
		}
		return null;
	}
}
