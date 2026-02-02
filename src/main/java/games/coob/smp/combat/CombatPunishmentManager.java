package games.coob.smp.combat;

import games.coob.smp.PlayerCache;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages combat punishments for players who log out during PvP combat.
 * Handles instant death, ghost bodies, PvP lockout, and debuffs.
 */
public class CombatPunishmentManager {

	private static final Map<UUID, PunishmentData> pendingPunishments = new HashMap<>();
	private static final Map<UUID, Long> pvpLockouts = new HashMap<>();

	/**
	 * Applies the configured punishment when a player logs out during combat.
	 */
	public static void applyPunishment(Player player) {
		if (!Settings.CombatSection.ENABLE_COMBAT_PUNISHMENTS) {
			return;
		}

		PlayerCache cache = PlayerCache.from(player);
		cache.setInCombat(false);

		switch (Settings.CombatSection.PUNISHMENT_TYPE) {
			case INSTANT_DEATH -> {
				player.setHealth(0);
				ColorUtil.sendMessage(player, "&cYou died for logging out during combat!");
			}
			case GHOST_BODY -> {
				CombatNPC.spawn(player);
				// Player's inventory is cleared by CombatNPC.spawn()
			}
			case RANDOM_ITEM_DROP -> {
				dropRandomItems(player);
			}
			case PVP_LOCKOUT -> {
				long lockoutUntil = System.currentTimeMillis()
						+ (Settings.CombatSection.PVP_LOCKOUT_DURATION_MINUTES * 60L * 1000L);
				pvpLockouts.put(player.getUniqueId(), lockoutUntil);
				cache.setPvpLockoutExpiry(lockoutUntil);
			}
			case DEBUFF -> {
				long debuffUntil = System.currentTimeMillis()
						+ (Settings.CombatSection.DEBUFF_DURATION_MINUTES * 60L * 1000L);
				cache.setDebuffExpiry(debuffUntil);
			}
			case NONE -> {
				// No punishment
			}
		}
	}

	/**
	 * Applies pending punishments when a player rejoins after combat-logging.
	 */
	public static void applyRejoinPunishments(Player player) {
		UUID uuid = player.getUniqueId();
		PlayerCache cache = PlayerCache.from(player);

		// Check for ghost body return
		if (CombatNPC.remove(uuid)) {
			// Items were returned by CombatNPC.remove()
			return;
		}

		// Check for pending punishment notifications
		PunishmentData data = pendingPunishments.remove(uuid);
		if (data != null) {
			if (data.killedWhileOffline) {
				ColorUtil.sendMessage(player,
						"&cYour ghost body was killed while you were offline. You lost your items!");
			} else if (data.ghostBodyDespawned) {
				ColorUtil.sendMessage(player,
						"&cYour ghost body despawned. Your items were dropped at your logout location.");
			}
		}

		// Apply PvP lockout
		long lockoutExpiry = cache.getPvpLockoutExpiry();
		if (lockoutExpiry > System.currentTimeMillis()) {
			pvpLockouts.put(uuid, lockoutExpiry);
			long minutesLeft = (lockoutExpiry - System.currentTimeMillis()) / 1000L / 60L;
			ColorUtil.sendMessage(player,
					"&cYou are locked out of PvP for &e" + minutesLeft + " &cminutes for combat-logging!");
		}

		// Apply debuffs
		long debuffExpiry = cache.getDebuffExpiry();
		if (debuffExpiry > System.currentTimeMillis()) {
			applyDebuffs(player, debuffExpiry);
			long minutesLeft = (debuffExpiry - System.currentTimeMillis()) / 1000L / 60L;
			ColorUtil.sendMessage(player,
					"&cYou have debuffs for &e" + minutesLeft + " &cminutes for combat-logging!");
		}
	}

	/**
	 * Applies debuff effects to a player.
	 */
	private static void applyDebuffs(Player player, long expiryTime) {
		int durationTicks = (int) ((expiryTime - System.currentTimeMillis()) / 50L);

		if (Settings.CombatSection.DEBUFF_SLOWNESS_LEVEL > 0) {
			player.addPotionEffect(
					new PotionEffect(PotionEffectType.SLOWNESS, durationTicks,
							Settings.CombatSection.DEBUFF_SLOWNESS_LEVEL - 1, false, true));
		}
		if (Settings.CombatSection.DEBUFF_WEAKNESS_LEVEL > 0) {
			player.addPotionEffect(
					new PotionEffect(PotionEffectType.WEAKNESS, durationTicks,
							Settings.CombatSection.DEBUFF_WEAKNESS_LEVEL - 1, false, true));
		}
		if (Settings.CombatSection.DEBUFF_MINING_FATIGUE_LEVEL > 0) {
			player.addPotionEffect(
					new PotionEffect(PotionEffectType.MINING_FATIGUE, durationTicks,
							Settings.CombatSection.DEBUFF_MINING_FATIGUE_LEVEL - 1, false, true));
		}

		// Schedule cleanup
		SchedulerUtil.runLater(durationTicks, () -> {
			PlayerCache cache = PlayerCache.from(player);
			cache.setDebuffExpiry(0);
		});
	}

	/**
	 * Checks if a player is locked out of PvP.
	 */
	public static boolean isPvpLocked(Player player) {
		Long lockoutExpiry = pvpLockouts.get(player.getUniqueId());
		if (lockoutExpiry == null)
			return false;

		if (System.currentTimeMillis() >= lockoutExpiry) {
			pvpLockouts.remove(player.getUniqueId());
			PlayerCache.from(player).setPvpLockoutExpiry(0);
			return false;
		}

		return true;
	}

	/**
	 * Gets remaining PvP lockout time in minutes.
	 */
	public static long getRemainingLockoutMinutes(Player player) {
		Long lockoutExpiry = pvpLockouts.get(player.getUniqueId());
		if (lockoutExpiry == null)
			return 0;

		long remaining = lockoutExpiry - System.currentTimeMillis();
		return Math.max(0, remaining / 1000L / 60L);
	}

	/**
	 * Marks a player as killed while offline (for notification on rejoin).
	 */
	public static void markPlayerAsKilledWhileOffline(UUID playerUUID) {
		PunishmentData data = pendingPunishments.computeIfAbsent(playerUUID, k -> new PunishmentData());
		data.killedWhileOffline = true;
	}

	/**
	 * Marks a player's ghost body as despawned (for notification on rejoin).
	 */
	public static void markPlayerGhostBodyDespawned(UUID playerUUID) {
		PunishmentData data = pendingPunishments.computeIfAbsent(playerUUID, k -> new PunishmentData());
		data.ghostBodyDespawned = true;
	}

	/**
	 * Drops random items from a player's inventory as punishment for
	 * combat-logging.
	 */
	private static void dropRandomItems(Player player) {
		org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
		org.bukkit.Location dropLocation = player.getLocation();

		int amount = Settings.CombatSection.RANDOM_ITEM_DROP_AMOUNT;
		boolean includeArmor = Settings.CombatSection.RANDOM_ITEM_DROP_INCLUDE_ARMOR;
		boolean includeHotbar = Settings.CombatSection.RANDOM_ITEM_DROP_INCLUDE_HOTBAR;

		// Collect all eligible items
		java.util.List<Integer> eligibleSlots = new java.util.ArrayList<>();

		// Add main inventory slots (9-35)
		for (int i = 9; i < 36; i++) {
			if (inventory.getItem(i) != null && inventory.getItem(i).getType() != org.bukkit.Material.AIR) {
				eligibleSlots.add(i);
			}
		}

		// Add hotbar slots (0-8) if enabled
		if (includeHotbar) {
			for (int i = 0; i < 9; i++) {
				if (inventory.getItem(i) != null && inventory.getItem(i).getType() != org.bukkit.Material.AIR) {
					eligibleSlots.add(i);
				}
			}
		}

		// Add armor slots if enabled
		if (includeArmor) {
			org.bukkit.inventory.ItemStack[] armor = inventory.getArmorContents();
			for (int i = 0; i < armor.length; i++) {
				if (armor[i] != null && armor[i].getType() != org.bukkit.Material.AIR) {
					eligibleSlots.add(100 + i); // Use 100+ for armor slots
				}
			}
		}

		// If amount is -1, drop all items
		if (amount == -1) {
			amount = eligibleSlots.size();
		}

		// Limit amount to available items
		amount = Math.min(amount, eligibleSlots.size());

		if (amount == 0) {
			ColorUtil.sendMessage(player, "&cYou combat-logged but had no items to drop!");
			return;
		}

		// Shuffle and select random slots
		java.util.Collections.shuffle(eligibleSlots);
		int droppedCount = 0;

		for (int i = 0; i < amount && i < eligibleSlots.size(); i++) {
			int slot = eligibleSlots.get(i);
			org.bukkit.inventory.ItemStack item;

			if (slot >= 100) {
				// Armor slot
				int armorSlot = slot - 100;
				org.bukkit.inventory.ItemStack[] armor = inventory.getArmorContents();
				item = armor[armorSlot];
				armor[armorSlot] = null;
				inventory.setArmorContents(armor);
			} else {
				// Regular inventory slot
				item = inventory.getItem(slot);
				inventory.setItem(slot, null);
			}

			if (item != null && item.getType() != org.bukkit.Material.AIR) {
				dropLocation.getWorld().dropItemNaturally(dropLocation, item);
				droppedCount++;
			}
		}

		ColorUtil.sendMessage(player, "&cYou combat-logged and lost &e" + droppedCount + " &crandom items!");
	}

	/**
	 * Cleans up all punishment data (called on plugin disable).
	 */
	public static void cleanup() {
		pendingPunishments.clear();
		pvpLockouts.clear();
	}

	private static class PunishmentData {
		boolean killedWhileOffline = false;
		boolean ghostBodyDespawned = false;
	}
}
