package games.coob.smp.command;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.command.SimpleCommand;
import org.mineacademy.fo.menu.Menu;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.menu.model.MenuClickLocation;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.nbt.*;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A sample command to open a menu we create in the course.
 */
public final class InvEditCommand extends SimpleCommand {

	public InvEditCommand() {
		super("inv|inventory");

		setMinArguments(2);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		checkConsole();

		final String param = args[0];
		final String name = args[1];

		// Try to get online player first (non-blocking)
		Player targetPlayer = Bukkit.getPlayer(name);
		// Note: getOfflinePlayer is deprecated but still needed for offline player lookup
		@SuppressWarnings("deprecation")
		final OfflinePlayer targetOfflinePlayer = targetPlayer != null ? targetPlayer : Bukkit.getOfflinePlayer(name);
		final boolean isOnline = targetPlayer != null;

		checkBoolean(targetOfflinePlayer != null && (targetOfflinePlayer.hasPlayedBefore() || isOnline), "{1} has never played before nor is online.");

		if ("inv".equals(param)) {
			if (isOnline)
				getPlayer().openInventory(targetPlayer.getInventory());
			else
				openOfflineInventoryMenu(getPlayer(), targetOfflinePlayer);

		} else if ("enderchest".equals(param)) {
			if (isOnline)
				getPlayer().openInventory(targetPlayer.getEnderChest());
			else
				openOfflineEnderChestMenu(getPlayer(), targetOfflinePlayer);

		} else if ("armour".equals(param)) {
			if (isOnline)
				ArmorMenu.showTo(getPlayer(), targetPlayer);
			else
				openOfflineArmourMenu(getPlayer(), targetOfflinePlayer);
		} else if ("clear".equals(param)) {
			if (isOnline) {
				clearInventory(targetPlayer);
				Common.tell(getPlayer(), "&6" + getPlayer() + "&e's inventory has been cleared.");
			} else Common.tell(getPlayer(), "&6" + getPlayer() + "&c isn't online so his inventory can't be cleared.");
		}
		// level 1 > beginners...
		// level 2 >
	}

	private void clearInventory(final Player player) {
		if (player.getInventory().getContents() != null)
			player.getInventory().setContents(null);
	}

	private static class OfflineInvMenu extends Menu {

		/**
		 * The way we open this menu. It can be opened to select kits or edit them
		 */
		private final ViewMode viewMode;

		private final NBTFile nbtFile;

		private NBTCompoundList nbtInventory;
		private NBTCompoundList nbtEnderItems;
		private NBTCompoundList nbtArmour;

		private ItemStack[] content;
		private Map<String, ItemStack> armourContent;

		@SneakyThrows
		private OfflineInvMenu(final OfflinePlayer target, final ViewMode viewMode) {
			this.viewMode = viewMode;

			this.nbtFile = new NBTFile(new File(Bukkit.getWorldContainer(), "world/playerdata/" + target.getUniqueId() + ".dat"));

			if (viewMode == ViewMode.INVENTORY) {
				setSize(PlayerUtil.USABLE_PLAYER_INV_SIZE);
				setTitle("&4" + target.getName() + "'s offline inventory");

				this.nbtInventory = this.nbtFile.getCompoundList("Inventory");
			} else if (viewMode == ViewMode.ENDER_CHEST) {
				setSize(PlayerUtil.USABLE_PLAYER_INV_SIZE);
				setTitle("&5" + target.getName() + "'s offline ender chest");

				this.nbtEnderItems = this.nbtFile.getCompoundList("EnderItems");
			} else if (viewMode == ViewMode.ARMOUR) {
				setSize(9);
				setTitle("&9" + target.getName() + "'s offline armour");

				this.nbtArmour = this.nbtFile.getCompoundList("Inventory");
				this.armourContent = this.readArmourData(target);
			}

			// this.nbtFile = new NBTFile(new File(Bukkit.getWorldContainer(), "world/playerdata/" + target.getUniqueId() + ".dat"));

			System.out.println(nbtFile);

			//this.nbtInventory = this.nbtFile.getCompoundList("Inventory");
			if (viewMode != ViewMode.ARMOUR)
				this.content = this.readData(target);
		}

		private ItemStack[] readData(final OfflinePlayer player) {
			final ItemStack[] content = new ItemStack[PlayerUtil.USABLE_PLAYER_INV_SIZE];

			if (viewMode == ViewMode.INVENTORY) {
				for (final NBTListCompound item : this.nbtInventory) {
					final int slot = item.getByte("Slot");

					if (slot >= 0 && slot <= PlayerUtil.USABLE_PLAYER_INV_SIZE)
						content[slot] = NBTItem.convertNBTtoItem(item);
				}
			}

			if (viewMode == ViewMode.ENDER_CHEST) {
				for (final NBTListCompound item : this.nbtEnderItems) {
					final int slot = item.getByte("Slot");

					if (slot >= 0 && slot <= PlayerUtil.USABLE_PLAYER_INV_SIZE)
						content[slot] = NBTItem.convertNBTtoItem(item);
				}
			}

			return content;
		}

		private Map<String, ItemStack> readArmourData(final OfflinePlayer player) {
			final Map<String, ItemStack> armourContent = new HashMap<>();

			for (final NBTListCompound item : this.nbtArmour) {
				final int slot = item.getByte("Slot");

				if (slot == 103)
					armourContent.put("Helmet", NBTItem.convertNBTtoItem(item));
				if (slot == 102)
					armourContent.put("Chestplate", NBTItem.convertNBTtoItem(item));
				if (slot == 101)
					armourContent.put("Leggings", NBTItem.convertNBTtoItem(item));
				if (slot == 100)
					armourContent.put("Boots", NBTItem.convertNBTtoItem(item));
			}

			return armourContent;
		}

		@Override
		public ItemStack getItemAt(final int slot) {
			if (viewMode != ViewMode.ARMOUR)
				return this.content[slot];
			else
				for (final Map.Entry<String, ItemStack> entry : armourContent.entrySet()) {
					if (slot == 0 && entry.getKey().equals("Helmet"))
						return entry.getValue();

					if (slot == 1 && entry.getKey().equals("Chestplate"))
						return entry.getValue();

					if (slot == 2 && entry.getKey().equals("Leggings"))
						return entry.getValue();

					if (slot == 3 && entry.getKey().equals("Boots"))
						return entry.getValue();
				}

			return null;
		}

		@Override
		protected boolean isActionAllowed(final MenuClickLocation location, final int slot, @Nullable final ItemStack clicked, @Nullable final ItemStack cursor, final InventoryAction action) {
			return true;
		}

		@Override
		protected void onMenuClick(final Player player, final int slot, final ItemStack clicked) {
			final Inventory inventory = getViewer().getOpenInventory().getBottomInventory();

			if (clicked != null) {
				if (clicked.getType().name().endsWith("HELMET")) {
					if (armourContent.containsKey("Helmet")) {
						getInventory().remove(clicked);
						inventory.addItem(clicked);
						armourContent.remove("Helmet");
					} else {
						getInventory().addItem(clicked);
						armourContent.put("Helmet", clicked);
					}

				} else if (clicked.getType().name().endsWith("CHESTPLATE")) {
					if (armourContent.containsKey("Chestplate")) {
						getInventory().remove(clicked);
						inventory.addItem(clicked);
						armourContent.remove("Chestplate");
					} else {
						getInventory().addItem(clicked);
						armourContent.put("Chestplate", clicked);
					}

				} else if (clicked.getType().name().endsWith("LEGGINGS")) {
					if (armourContent.containsKey("Leggings")) {
						getInventory().remove(clicked);
						inventory.addItem(clicked);
						armourContent.remove("Leggings");
					} else {
						getInventory().addItem(clicked);
						armourContent.put("Leggings", clicked);
					}

				} else if (clicked.getType().name().endsWith("BOOTS")) {
					if (armourContent.containsKey("Boots")) {
						getInventory().remove(clicked);
						inventory.addItem(clicked);
						armourContent.remove("Boots");
					} else {
						getInventory().addItem(clicked);
						armourContent.put("Boots", clicked);
					}
				}
			}
		}

		@Override
		@SneakyThrows
		protected void onMenuClose(final Player player, final Inventory inventory) {
			final ItemStack[] editedContent = inventory.getContents();

			if (viewMode == ViewMode.INVENTORY)
				this.nbtInventory.clear();
			if (viewMode == ViewMode.ENDER_CHEST)
				this.nbtEnderItems.clear();

			if (viewMode != ViewMode.ARMOUR) {
				for (int slot = 0; slot < editedContent.length; slot++) {
					final ItemStack item = editedContent[slot];

					if (item != null) {
						final NBTContainer container = NBTItem.convertItemtoNBT(item);
						container.setByte("Slot", (byte) slot);

						if (viewMode == ViewMode.INVENTORY)
							this.nbtInventory.addCompound(container); // arraylist#add

						if (viewMode == ViewMode.ENDER_CHEST)
							this.nbtEnderItems.addCompound(container);
					}
				}

			} else {
				this.nbtArmour.clear();

				for (int slot = 0; slot <= 8; slot++) {
					final ItemStack item = editedContent[slot];

					if (item != null) {
						if (item.getType().name().endsWith("HELMET"))
							armourContent.put("Helmet", item);

						else if (item.getType().name().endsWith("CHESTPLATE"))
							armourContent.put("Chestplate", item);

						else if (item.getType().name().endsWith("LEGGINGS"))
							armourContent.put("Leggings", item);

						else if (item.getType().name().endsWith("BOOTS"))
							armourContent.put("Boots", item);
					}
				}

				for (final Map.Entry<String, ItemStack> entry : armourContent.entrySet()) {
					final ItemStack itemArmor = entry.getValue();

					if (itemArmor != null) {
						final NBTContainer containerArmor = NBTItem.convertItemtoNBT(itemArmor);

						switch (entry.getKey()) {
							case "Helmet" -> containerArmor.setByte("Slot", (byte) 103);
							case "Chestplate" -> containerArmor.setByte("Slot", (byte) 102);
							case "Leggings" -> containerArmor.setByte("Slot", (byte) 101);
							case "Boots" -> containerArmor.setByte("Slot", (byte) 100);
						}

						this.nbtArmour.addCompound(containerArmor);
					}
				}
			}

			this.nbtFile.save();
		}
	}

	/**
	 * The menu view mode
	 */
	@RequiredArgsConstructor
	public enum ViewMode {

		INVENTORY,

		ENDER_CHEST,

		ARMOUR
	}

	/**
	 * Open the menu that allows you to edit the target player's inventory
	 */
	public static void openOfflineInventoryMenu(final Player viewer, final OfflinePlayer target) {
		new OfflineInvMenu(target, ViewMode.INVENTORY).displayTo(viewer);
	}

	/**
	 * Open the menu that allows you to edit the target player's ender chest
	 */
	public static void openOfflineEnderChestMenu(final Player viewer, final OfflinePlayer target) {
		new OfflineInvMenu(target, ViewMode.ENDER_CHEST).displayTo(viewer);
	}

	/**
	 * Open the menu that allows you to edit the target player's armour contents
	 */
	public static void openOfflineArmourMenu(final Player viewer, final OfflinePlayer target) {
		new OfflineInvMenu(target, ViewMode.ARMOUR).displayTo(viewer);
	}

	private static class ArmorMenu extends Menu {

		// ONE ROW - 9 slot
		// HELMET 0, CHESTPLATE 1, LEGGINGS 2, BOOTS 3, EMPTY SLOT 4, EMPTY SLOT 5, EMPTY 6, EMPTY 7, OFFHAND 8

		private final Player targetPlayer;

		private final static ItemStack EMPTY_SLOT_FILLER = ItemCreator
				.of(CompMaterial.GRAY_STAINED_GLASS_PANE, "").make();

		private ArmorMenu(final Player targetPlayer) {
			setTitle(targetPlayer.getName() + "'s armor");
			setSize(9);
			//setSlotNumbersVisible();

			this.targetPlayer = targetPlayer;
		}

		@Override
		public ItemStack getItemAt(final int slot) {
			final PlayerInventory inv = this.targetPlayer.getInventory();

			if (slot == 0)
				return inv.getHelmet();

			else if (slot == 1)
				return inv.getChestplate();

			else if (slot == 2)
				return inv.getLeggings();

			else if (slot == 3)
				return inv.getBoots();

			else if (slot == this.getSize() - 1)
				return inv.getItemInOffHand();

			return NO_ITEM;
		}

		@Override
		protected boolean isActionAllowed(final MenuClickLocation location, final int slot, @Nullable final ItemStack clicked, @Nullable final ItemStack cursor, final InventoryAction action) {
			return location != MenuClickLocation.MENU || (slot != 4 && slot != 5 && slot != 6 && slot != 7);
		}

		@Override
		protected void onMenuClose(final Player player, final Inventory inventory) {
			final PlayerInventory targetPlayerInv = this.targetPlayer.getInventory();

			targetPlayerInv.setItemInOffHand(inventory.getItem(this.getSize() - 1));

			targetPlayerInv.setHelmet(inventory.getItem(0));
			targetPlayerInv.setChestplate(inventory.getItem(1));
			targetPlayerInv.setLeggings(inventory.getItem(2));
			targetPlayerInv.setBoots(inventory.getItem(3));
		}

		private static void showTo(final Player viewer, final Player targetPlayer) {
			new ArmorMenu(targetPlayer).displayTo(viewer);
		}

	}

	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return this.completeLastWord("inv", "enderchest", "armour");

		else if (args.length == 2)
			return this.completeLastWordPlayerNames();

		return NO_COMPLETE;
	}
}