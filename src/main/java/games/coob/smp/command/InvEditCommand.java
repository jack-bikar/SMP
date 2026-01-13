package games.coob.smp.command;

import de.tr7zw.changeme.nbtapi.NBTCompoundList;
import de.tr7zw.changeme.nbtapi.NBTFile;
import de.tr7zw.changeme.nbtapi.NBTItem;
import de.tr7zw.changeme.nbtapi.NBTListCompound;
import games.coob.smp.menu.SimpleMenu;
import games.coob.smp.util.ItemCreator;
import games.coob.smp.util.Messenger;
import games.coob.smp.util.PlayerUtil;
import games.coob.smp.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Command to edit player inventories
 */
public final class InvEditCommand implements CommandExecutor, TabCompleter {

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage("This command can only be used by players.");
			return true;
		}

		if (args.length < 2) {
			Messenger.error(player, "Usage: /inv <inv|enderchest|armour|clear> <player>");
			return true;
		}

		final String param = args[0];
		final String name = args[1];

		Player targetPlayer = Bukkit.getPlayer(name);
		@SuppressWarnings("deprecation")
		final OfflinePlayer targetOfflinePlayer = targetPlayer != null ? targetPlayer : Bukkit.getOfflinePlayer(name);
		final boolean isOnline = targetPlayer != null;

		ValidationUtil.checkBoolean(targetOfflinePlayer != null && (targetOfflinePlayer.hasPlayedBefore() || isOnline), 
				name + " has never played before nor is online.");

		if ("inv".equals(param)) {
			if (isOnline) {
				player.openInventory(targetPlayer.getInventory());
			} else {
				openOfflineInventoryMenu(player, targetOfflinePlayer);
			}
		} else if ("enderchest".equals(param)) {
			if (isOnline) {
				player.openInventory(targetPlayer.getEnderChest());
			} else {
				openOfflineEnderChestMenu(player, targetOfflinePlayer);
			}
		} else if ("armour".equals(param) || "armor".equals(param)) {
			if (isOnline) {
				ArmorMenu.showTo(player, targetPlayer);
			} else {
				openOfflineArmourMenu(player, targetOfflinePlayer);
			}
		} else if ("clear".equals(param)) {
			if (isOnline) {
				clearInventory(targetPlayer);
				Messenger.success(player, targetPlayer.getName() + "'s inventory has been cleared.");
			} else {
				Messenger.error(player, targetOfflinePlayer.getName() + " isn't online so their inventory can't be cleared.");
			}
		} else {
			Messenger.error(player, "Invalid parameter. Use: inv, enderchest, armour, or clear");
		}

		return true;
	}

	private void clearInventory(final Player player) {
		player.getInventory().clear();
	}

	private static class OfflineInvMenu extends SimpleMenu {
		private final ViewMode viewMode;
		private NBTFile nbtFile;
		private NBTCompoundList nbtInventory;
		private NBTCompoundList nbtEnderItems;
		private NBTCompoundList nbtArmour;
		private ItemStack[] content;
		private Map<String, ItemStack> armourContent;

		private OfflineInvMenu(final Player viewer, final OfflinePlayer target, final ViewMode viewMode) {
			super(viewer, getSizeForMode(viewMode), getTitleForMode(target, viewMode));
			this.viewMode = viewMode;

			try {
				this.nbtFile = new NBTFile(new File(Bukkit.getWorldContainer(), "world/playerdata/" + target.getUniqueId() + ".dat"));

				if (viewMode == ViewMode.INVENTORY) {
					this.nbtInventory = this.nbtFile.getCompoundList("Inventory");
					this.content = readData();
				} else if (viewMode == ViewMode.ENDER_CHEST) {
					this.nbtEnderItems = this.nbtFile.getCompoundList("EnderItems");
					this.content = readData();
				} else if (viewMode == ViewMode.ARMOUR) {
					this.nbtArmour = this.nbtFile.getCompoundList("Inventory");
					this.armourContent = readArmourData();
				}

				// Populate inventory
				if (viewMode != ViewMode.ARMOUR) {
					for (int i = 0; i < content.length && i < inventory.getSize(); i++) {
						inventory.setItem(i, content[i]);
					}
				} else {
					if (armourContent.containsKey("Helmet")) inventory.setItem(0, armourContent.get("Helmet"));
					if (armourContent.containsKey("Chestplate")) inventory.setItem(1, armourContent.get("Chestplate"));
					if (armourContent.containsKey("Leggings")) inventory.setItem(2, armourContent.get("Leggings"));
					if (armourContent.containsKey("Boots")) inventory.setItem(3, armourContent.get("Boots"));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		private static int getSizeForMode(ViewMode mode) {
			return mode == ViewMode.ARMOUR ? 9 : PlayerUtil.USABLE_PLAYER_INV_SIZE;
		}

		private static String getTitleForMode(OfflinePlayer target, ViewMode mode) {
			return switch (mode) {
				case INVENTORY -> "&4" + target.getName() + "'s offline inventory";
				case ENDER_CHEST -> "&5" + target.getName() + "'s offline ender chest";
				case ARMOUR -> "&9" + target.getName() + "'s offline armour";
			};
		}

		private ItemStack[] readData() {
			final ItemStack[] content = new ItemStack[PlayerUtil.USABLE_PLAYER_INV_SIZE];
			NBTCompoundList list = viewMode == ViewMode.INVENTORY ? nbtInventory : nbtEnderItems;

			if (list != null) {
				for (int i = 0; i < list.size(); i++) {
					NBTListCompound item = list.get(i);
					final int slot = item.getByte("Slot");
					if (slot >= 0 && slot < PlayerUtil.USABLE_PLAYER_INV_SIZE) {
						try {
							// NBTAPI: Create ItemStack from NBTCompound
							ItemStack itemStack = NBTItem.convertNBTtoItem(item);
							content[slot] = itemStack;
						} catch (Exception e) {
							// If conversion method doesn't exist, try alternative approach
							try {
								// Create empty item and apply NBT
								Material material = Material.matchMaterial(item.getString("id"));
								if (material != null) {
									ItemStack itemStack = new ItemStack(material);
									NBTItem nbtItem = new NBTItem(itemStack);
									nbtItem.mergeCompound(item);
									content[slot] = nbtItem.getItem();
								}
							} catch (Exception ex) {
								content[slot] = null;
							}
						}
					}
				}
			}

			return content;
		}

		private Map<String, ItemStack> readArmourData() {
			final Map<String, ItemStack> armourContent = new HashMap<>();

			if (nbtArmour != null) {
				for (int i = 0; i < nbtArmour.size(); i++) {
					NBTListCompound item = nbtArmour.get(i);
					final int slot = item.getByte("Slot");
					try {
						// NBTAPI: Create ItemStack from NBTCompound
						ItemStack itemStack = NBTItem.convertNBTtoItem(item);
						switch (slot) {
							case 103 -> armourContent.put("Helmet", itemStack);
							case 102 -> armourContent.put("Chestplate", itemStack);
							case 101 -> armourContent.put("Leggings", itemStack);
							case 100 -> armourContent.put("Boots", itemStack);
						}
					} catch (Exception e) {
						// If conversion method doesn't exist, try alternative
						try {
							Material material = Material.matchMaterial(item.getString("id"));
							if (material != null) {
								ItemStack itemStack = new ItemStack(material);
								NBTItem nbtItem = new NBTItem(itemStack);
								nbtItem.mergeCompound(item);
								ItemStack finalItem = nbtItem.getItem();
								switch (slot) {
									case 103 -> armourContent.put("Helmet", finalItem);
									case 102 -> armourContent.put("Chestplate", finalItem);
									case 101 -> armourContent.put("Leggings", finalItem);
									case 100 -> armourContent.put("Boots", finalItem);
								}
							}
						} catch (Exception ex) {
							// Skip invalid items
						}
					}
				}
			}

			return armourContent;
		}

		@Override
		protected void onMenuClick(Player player, int slot, ItemStack clicked) {
			// Allow all interactions
		}

		@Override
		protected void onMenuClose(Player player, Inventory inventory) {
			try {
				final ItemStack[] editedContent = inventory.getContents();

				if (viewMode == ViewMode.INVENTORY && nbtInventory != null) {
					nbtInventory.clear();
					for (int slot = 0; slot < editedContent.length && slot < PlayerUtil.USABLE_PLAYER_INV_SIZE; slot++) {
						final ItemStack item = editedContent[slot];
						if (item != null) {
							NBTItem nbtItem = new NBTItem(item);
							NBTListCompound compound = nbtInventory.addCompound();
							// Copy NBT data from item - getCompound returns NBTCompound
							Object compoundObj = nbtItem.getCompound();
							if (compoundObj instanceof de.tr7zw.changeme.nbtapi.NBTCompound itemCompound) {
								compound.mergeCompound(itemCompound);
							}
							compound.setByte("Slot", (byte) slot);
							// Set item ID (required for Minecraft 1.21+)
							compound.setString("id", item.getType().getKey().toString());
						}
					}
				} else if (viewMode == ViewMode.ENDER_CHEST && nbtEnderItems != null) {
					nbtEnderItems.clear();
					for (int slot = 0; slot < editedContent.length && slot < PlayerUtil.USABLE_PLAYER_INV_SIZE; slot++) {
						final ItemStack item = editedContent[slot];
						if (item != null) {
							NBTItem nbtItem = new NBTItem(item);
							NBTListCompound compound = nbtEnderItems.addCompound();
							Object compoundObj = nbtItem.getCompound();
							if (compoundObj instanceof de.tr7zw.changeme.nbtapi.NBTCompound itemCompound) {
								compound.mergeCompound(itemCompound);
							}
							compound.setByte("Slot", (byte) slot);
							compound.setString("id", item.getType().getKey().toString());
						}
					}
				} else if (viewMode == ViewMode.ARMOUR && nbtArmour != null) {
					nbtArmour.clear();
					for (int slot = 0; slot <= 3; slot++) {
						final ItemStack item = editedContent[slot];
						if (item != null) {
							String key = switch (slot) {
								case 0 -> "Helmet";
								case 1 -> "Chestplate";
								case 2 -> "Leggings";
								case 3 -> "Boots";
								default -> null;
							};
							if (key != null) {
								NBTItem nbtItem = new NBTItem(item);
								NBTListCompound compound = nbtArmour.addCompound();
								Object compoundObj = nbtItem.getCompound();
								if (compoundObj instanceof de.tr7zw.changeme.nbtapi.NBTCompound itemCompound) {
									compound.mergeCompound(itemCompound);
								}
								compound.setByte("Slot", (byte) (103 - slot));
								compound.setString("id", item.getType().getKey().toString());
							}
						}
					}
				}

				if (nbtFile != null) {
					nbtFile.save();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@RequiredArgsConstructor
	public enum ViewMode {
		INVENTORY,
		ENDER_CHEST,
		ARMOUR
	}

	public static void openOfflineInventoryMenu(final Player viewer, final OfflinePlayer target) {
		new OfflineInvMenu(viewer, target, ViewMode.INVENTORY).displayTo(viewer);
	}

	public static void openOfflineEnderChestMenu(final Player viewer, final OfflinePlayer target) {
		new OfflineInvMenu(viewer, target, ViewMode.ENDER_CHEST).displayTo(viewer);
	}

	public static void openOfflineArmourMenu(final Player viewer, final OfflinePlayer target) {
		new OfflineInvMenu(viewer, target, ViewMode.ARMOUR).displayTo(viewer);
	}

	private static class ArmorMenu extends SimpleMenu {
		private final Player targetPlayer;
		private static final ItemStack EMPTY_SLOT_FILLER = ItemCreator.of(Material.GRAY_STAINED_GLASS_PANE, "").make();

		private ArmorMenu(final Player viewer, final Player targetPlayer) {
			super(viewer, 9, targetPlayer.getName() + "'s armor");
			this.targetPlayer = targetPlayer;
			setupItems();
		}

		private void setupItems() {
			final PlayerInventory inv = this.targetPlayer.getInventory();
			inventory.setItem(0, inv.getHelmet());
			inventory.setItem(1, inv.getChestplate());
			inventory.setItem(2, inv.getLeggings());
			inventory.setItem(3, inv.getBoots());
			inventory.setItem(8, inv.getItemInOffHand());
			// Fill empty slots
			for (int i = 4; i < 8; i++) {
				inventory.setItem(i, EMPTY_SLOT_FILLER);
			}
		}

		@Override
		protected void onMenuClick(Player player, int slot, ItemStack clicked) {
			// Prevent interaction with filler slots
			if (slot >= 4 && slot < 8) {
				return;
			}
		}

		@Override
		protected void onMenuClose(Player player, Inventory inventory) {
			final PlayerInventory targetPlayerInv = this.targetPlayer.getInventory();
			targetPlayerInv.setItemInOffHand(inventory.getItem(8));
			targetPlayerInv.setHelmet(inventory.getItem(0));
			targetPlayerInv.setChestplate(inventory.getItem(1));
			targetPlayerInv.setLeggings(inventory.getItem(2));
			targetPlayerInv.setBoots(inventory.getItem(3));
		}

		private static void showTo(final Player viewer, final Player targetPlayer) {
			new ArmorMenu(viewer, targetPlayer).displayTo(viewer);
		}
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return Arrays.asList("inv", "enderchest", "armour", "armor", "clear").stream()
					.filter(s -> s.startsWith(args[0].toLowerCase()))
					.collect(Collectors.toList());
		} else if (args.length == 2) {
			return Bukkit.getOnlinePlayers().stream()
					.map(Player::getName)
					.filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
					.collect(Collectors.toList());
		}
		return new ArrayList<>();
	}
}
