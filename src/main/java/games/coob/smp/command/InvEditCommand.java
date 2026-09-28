package games.coob.smp.command;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBTCompoundList;
import games.coob.smp.SMPPlugin;
import games.coob.smp.menu.SimpleMenu;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.ItemCreator;
import games.coob.smp.util.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * /inv &lt;inv|enderchest|armour|clear&gt; &lt;player&gt; - view and edit inventories.
 * Offline players are edited directly in their player data file.
 */
public final class InvEditCommand implements CommandExecutor, TabCompleter, Listener {

	/** Offline players whose data file is open in an editor right now. */
	private static final Set<UUID> BEING_EDITED = ConcurrentHashMap.newKeySet();

	/** Menu slots for armour + offhand, in helmet, chest, legs, boots order. */
	private static final int[] ARMOUR_SLOTS = { 0, 1, 2, 3 };
	private static final String[] EQUIPMENT_KEYS = { "head", "chest", "legs", "feet" };
	private static final int OFFHAND_SLOT = 8;

	@Override
	public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
		if (!(sender instanceof final Player player)) {
			ColorUtil.sendMessage(sender, "&cThis command can only be used by players.");
			return true;
		}

		if (!player.hasPermission("smp.command.inv")) {
			Messenger.error(player, "You don't have permission to use this command.");
			return true;
		}

		if (args.length < 2) {
			Messenger.error(player, "Usage: /inv <inv|enderchest|armour|clear> <player>");
			return true;
		}

		final String param = args[0].toLowerCase(Locale.ROOT);
		final Player online = Bukkit.getPlayerExact(args[1]);
		final OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayerIfCached(args[1]);

		if (target == null || (online == null && !target.hasPlayedBefore())) {
			Messenger.error(player, args[1] + " has never played on this server.");
			return true;
		}

		switch (param) {
			case "inv", "inventory" -> {
				if (online != null)
					player.openInventory(online.getInventory());
				else
					openOffline(player, target, ViewMode.INVENTORY);
			}
			case "enderchest", "ec" -> {
				if (online != null)
					player.openInventory(online.getEnderChest());
				else
					openOffline(player, target, ViewMode.ENDER_CHEST);
			}
			case "armour", "armor" -> {
				if (online != null)
					new ArmourMenu(player, online).displayTo(player);
				else
					openOffline(player, target, ViewMode.ARMOUR);
			}
			case "clear" -> {
				if (online != null) {
					online.getInventory().clear();
					Messenger.success(player, online.getName() + "'s inventory has been cleared.");
				} else {
					Messenger.error(player, target.getName() + " isn't online so their inventory can't be cleared.");
				}
			}
			default -> Messenger.error(player, "Invalid parameter. Use: inv, enderchest, armour, or clear");
		}
		return true;
	}

	private static void openOffline(Player viewer, OfflinePlayer target, ViewMode mode) {
		if (BEING_EDITED.contains(target.getUniqueId())) {
			Messenger.error(viewer, "Someone else is already editing " + target.getName() + "'s data.");
			return;
		}
		File file = findPlayerDataFile(target);
		if (file == null) {
			Messenger.error(viewer, "Could not find the saved data for " + target.getName() + ".");
			return;
		}
		try {
			new OfflineInventoryMenu(viewer, target, file, mode).displayTo(viewer);
			BEING_EDITED.add(target.getUniqueId());
		} catch (Exception e) {
			Messenger.error(viewer, "Could not read " + target.getName() + "'s data: " + e.getMessage());
		}
	}

	/**
	 * Player files live in the main world folder: players/data in 26.x,
	 * playerdata in older versions.
	 */
	private static File findPlayerDataFile(OfflinePlayer target) {
		File levelFolder = new File(Bukkit.getWorldContainer(), Bukkit.getWorlds().getFirst().getName());
		String fileName = target.getUniqueId() + ".dat";
		return Stream.of(new File(levelFolder, "players/data/" + fileName), new File(levelFolder, "playerdata/" + fileName))
				.filter(File::exists)
				.findFirst()
				.orElse(null);
	}

	private enum ViewMode {
		INVENTORY,
		ENDER_CHEST,
		ARMOUR
	}

	/**
	 * Edits an offline player's saved inventory, ender chest or armour. Changes are
	 * written to their data file when the menu is closed.
	 */
	private static final class OfflineInventoryMenu extends SimpleMenu {

		private final OfflinePlayer target;
		private final File file;
		private final ViewMode mode;

		private OfflineInventoryMenu(Player viewer, OfflinePlayer target, File file, ViewMode mode) throws Exception {
			super(viewer, mode == ViewMode.INVENTORY ? 36 : mode == ViewMode.ENDER_CHEST ? 27 : 9, switch (mode) {
				case INVENTORY -> "&4" + target.getName() + "'s inventory (offline)";
				case ENDER_CHEST -> "&5" + target.getName() + "'s ender chest (offline)";
				case ARMOUR -> "&9" + target.getName() + "'s armour (offline)";
			});
			this.target = target;
			this.file = file;
			this.mode = mode;

			ReadWriteNBT data = NBT.readFile(file);
			// Files from an older Minecraft version are upgraded by the server when the player joins;
			// rewriting them here first could damage items
			if (data.hasTag("DataVersion") && data.getInteger("DataVersion") != currentDataVersion())
				throw new IllegalStateException(target.getName()
						+ " hasn't joined since the server was updated; they need to join once before their items can be edited offline");

			if (mode == ViewMode.ARMOUR) {
				ReadWriteNBT equipment = data.getOrCreateCompound("equipment");
				for (int i = 0; i < ARMOUR_SLOTS.length; i++)
					inventory.setItem(ARMOUR_SLOTS[i], readItem(equipment.getCompound(EQUIPMENT_KEYS[i])));
				inventory.setItem(OFFHAND_SLOT, readItem(equipment.getCompound("offhand")));
				fillArmourMenu(inventory);
			} else {
				ReadWriteNBTCompoundList list = data.getCompoundList(listKey());
				for (int i = 0; i < list.size(); i++) {
					ReadWriteNBT entry = list.get(i);
					int slot = entry.getByte("Slot");
					if (slot >= 0 && slot < inventory.getSize())
						inventory.setItem(slot, readItem(entry));
				}
			}
			for (int slot = 0; slot < inventory.getSize(); slot++) {
				ItemStack item = inventory.getItem(slot);
				opened[slot] = item == null ? null : item.clone();
			}
		}

		/** What the menu held when it opened; nothing is written if it wasn't changed. */
		private final ItemStack[] opened = new ItemStack[54];

		@SuppressWarnings("deprecation")
		private static int currentDataVersion() {
			return Bukkit.getUnsafe().getDataVersion();
		}

		private boolean changed() {
			for (int slot = 0; slot < inventory.getSize(); slot++) {
				if (!java.util.Objects.equals(inventory.getItem(slot), opened[slot]))
					return true;
			}
			return false;
		}

		private String listKey() {
			return mode == ViewMode.ENDER_CHEST ? "EnderItems" : "Inventory";
		}

		@Override
		protected boolean isEditable() {
			return true;
		}

		@Override
		protected boolean isLockedSlot(int slot) {
			return mode == ViewMode.ARMOUR && isArmourFiller(slot);
		}

		@Override
		protected void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
		}

		@Override
		protected void onMenuClose(Player player, Inventory inventory) {
			BEING_EDITED.remove(target.getUniqueId());
			// The server owns the data of online players; writing the file now would be lost or undone
			if (target.isOnline()) {
				Messenger.error(player, target.getName() + " joined while you were editing; changes were not saved.");
				return;
			}

			if (!changed())
				return;

			try {
				ReadWriteNBT data = NBT.readFile(file);
				if (mode == ViewMode.ARMOUR) {
					ReadWriteNBT equipment = data.getOrCreateCompound("equipment");
					for (int i = 0; i < ARMOUR_SLOTS.length; i++)
						writeItem(equipment, EQUIPMENT_KEYS[i], inventory.getItem(ARMOUR_SLOTS[i]));
					writeItem(equipment, "offhand", inventory.getItem(OFFHAND_SLOT));
				} else {
					ReadWriteNBTCompoundList list = data.getCompoundList(listKey());
					// Keep entries outside the edited range (e.g. armour in pre-1.21.5 files)
					List<ReadWriteNBT> kept = new ArrayList<>();
					for (int i = 0; i < list.size(); i++) {
						ReadWriteNBT entry = list.get(i);
						int slot = entry.getByte("Slot");
						if (slot < 0 || slot >= inventory.getSize()) {
							ReadWriteNBT copy = NBT.createNBTObject();
							copy.mergeCompound(entry);
							kept.add(copy);
						}
					}
					list.clear();
					for (ReadWriteNBT entry : kept)
						list.addCompound(entry);
					for (int slot = 0; slot < inventory.getSize(); slot++) {
						ItemStack item = inventory.getItem(slot);
						if (item != null && !item.isEmpty()) {
							ReadWriteNBT entry = list.addCompound(NBT.itemStackToNBT(item));
							entry.setByte("Slot", (byte) slot);
						}
					}
				}
				NBT.writeFile(file, data);
				Messenger.success(player, "Saved " + target.getName() + "'s " + switch (mode) {
					case INVENTORY -> "inventory";
					case ENDER_CHEST -> "ender chest";
					case ARMOUR -> "armour";
				} + ".");
			} catch (Exception e) {
				Messenger.error(player, "Could not save " + target.getName() + "'s data: " + e.getMessage());
			}
		}

		private static ItemStack readItem(ReadWriteNBT compound) {
			if (compound == null || !compound.hasTag("id"))
				return null;
			ReadWriteNBT copy = NBT.createNBTObject();
			copy.mergeCompound(compound);
			copy.removeKey("Slot");
			return NBT.itemStackFromNBT(copy);
		}

		private static void writeItem(ReadWriteNBT equipment, String key, ItemStack item) {
			equipment.removeKey(key);
			if (item != null && !item.isEmpty())
				equipment.getOrCreateCompound(key).mergeCompound(NBT.itemStackToNBT(item));
		}
	}

	/**
	 * Edits an online player's armour and offhand.
	 */
	private static final class ArmourMenu extends SimpleMenu {

		private final Player target;
		/** What each slot held when the menu opened, so only changed slots are written back. */
		private final ItemStack[] original = new ItemStack[9];

		private ArmourMenu(Player viewer, Player target) {
			super(viewer, 9, "&9" + target.getName() + "'s armour");
			this.target = target;

			PlayerInventory inv = target.getInventory();
			inventory.setItem(0, inv.getHelmet());
			inventory.setItem(1, inv.getChestplate());
			inventory.setItem(2, inv.getLeggings());
			inventory.setItem(3, inv.getBoots());
			inventory.setItem(OFFHAND_SLOT, inv.getItemInOffHand());
			fillArmourMenu(inventory);
			for (int slot : new int[] { 0, 1, 2, 3, OFFHAND_SLOT })
				original[slot] = copy(inventory.getItem(slot));
		}

		private static ItemStack copy(ItemStack item) {
			return item == null || item.isEmpty() ? null : item.clone();
		}

		private boolean changed(int slot) {
			ItemStack now = copy(inventory.getItem(slot));
			return now == null ? original[slot] != null : !now.equals(original[slot]);
		}

		@Override
		protected boolean isEditable() {
			return true;
		}

		@Override
		protected boolean isLockedSlot(int slot) {
			return isArmourFiller(slot);
		}

		@Override
		protected void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
		}

		@Override
		protected void onMenuClose(Player player, Inventory inventory) {
			if (!target.isOnline()) {
				Messenger.error(player, target.getName() + " left while you were editing; changes were not saved.");
				return;
			}
			// Only touch slots the admin changed, so the player's own changes meanwhile aren't overwritten
			PlayerInventory inv = target.getInventory();
			if (changed(0))
				inv.setHelmet(inventory.getItem(0));
			if (changed(1))
				inv.setChestplate(inventory.getItem(1));
			if (changed(2))
				inv.setLeggings(inventory.getItem(2));
			if (changed(3))
				inv.setBoots(inventory.getItem(3));
			if (changed(OFFHAND_SLOT))
				inv.setItemInOffHand(inventory.getItem(OFFHAND_SLOT));
		}
	}

	/**
	 * A player whose offline data is being edited is logging in: save and close
	 * the editor first (on the main thread), before the server loads their data.
	 * Only this player's login waits; the server keeps running.
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPreLogin(AsyncPlayerPreLoginEvent event) {
		UUID id = event.getUniqueId();
		if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED || !BEING_EDITED.contains(id))
			return;
		try {
			Bukkit.getScheduler().callSyncMethod(SMPPlugin.getInstance(), () -> {
				for (Player admin : Bukkit.getOnlinePlayers()) {
					if (admin.getOpenInventory().getTopInventory().getHolder(false) instanceof OfflineInventoryMenu menu
							&& menu.target.getUniqueId().equals(id)) {
						admin.closeInventory();
						Messenger.info(admin, menu.target.getName() + " is joining; your changes were saved.");
					}
				}
				return null;
			}).get(5, TimeUnit.SECONDS);
		} catch (Exception e) {
			event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
					ColorUtil.toComponent("&cYour data is being edited, please try again in a moment."));
		}
	}

	private static boolean isArmourFiller(int slot) {
		return slot >= 4 && slot < OFFHAND_SLOT;
	}

	private static void fillArmourMenu(Inventory inventory) {
		ItemStack filler = ItemCreator.of(Material.GRAY_STAINED_GLASS_PANE, " ").make();
		for (int slot = 4; slot < OFFHAND_SLOT; slot++)
			inventory.setItem(slot, filler);
	}

	@Override
	public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
		if (args.length == 1) {
			return Stream.of("inv", "enderchest", "armour", "clear")
					.filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
					.toList();
		} else if (args.length == 2) {
			return Bukkit.getOnlinePlayers().stream()
					.map(Player::getName)
					.filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
					.toList();
		}
		return new ArrayList<>();
	}
}
