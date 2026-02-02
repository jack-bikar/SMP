package games.coob.smp.combat;

import games.coob.smp.SMPPlugin;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a ghost body NPC that spawns when a player logs out during combat.
 * The NPC can be killed by other players to get the combat-logger's loot.
 */
public class CombatNPC {

	private static final Map<UUID, CombatNPC> activeNPCs = new HashMap<>();
	private static final String METADATA_KEY = "combat_npc";

	private final UUID playerUUID;
	private final String playerName;
	private final Zombie npc;
	private final ItemStack[] inventory;
	private final ItemStack[] armor;
	private final double health;
	private final int despawnTaskId;

	private CombatNPC(UUID playerUUID, String playerName, Location location, ItemStack[] inventory,
			ItemStack[] armor, double health) {
		this.playerUUID = playerUUID;
		this.playerName = playerName;
		this.inventory = inventory;
		this.armor = armor;
		this.health = health;

		// Spawn zombie NPC
		this.npc = (Zombie) location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
		this.npc.setCustomName(ColorUtil.colorize("&c" + playerName + " &7(Combat Logger)"));
		this.npc.setCustomNameVisible(true);
		this.npc.setRemoveWhenFarAway(false);
		this.npc.setCanPickupItems(false);
		this.npc.setBaby(false);

		// Set metadata to identify this as a combat NPC
		this.npc.setMetadata(METADATA_KEY, new FixedMetadataValue(SMPPlugin.getInstance(), playerUUID.toString()));

		// Apply health
		if (Settings.CombatSection.GHOST_BODY_USE_PLAYER_HEALTH) {
			this.npc.setMaxHealth(health);
			this.npc.setHealth(health);
		}

		// Apply armor
		if (Settings.CombatSection.GHOST_BODY_USE_PLAYER_ARMOR) {
			EntityEquipment equipment = this.npc.getEquipment();
			if (equipment != null && armor != null) {
				equipment.setHelmet(armor[3]);
				equipment.setChestplate(armor[2]);
				equipment.setLeggings(armor[1]);
				equipment.setBoots(armor[0]);
				equipment.setHelmetDropChance(1.0f);
				equipment.setChestplateDropChance(1.0f);
				equipment.setLeggingsDropChance(1.0f);
				equipment.setBootsDropChance(1.0f);
			}
		}

		// Set held item if fight back is enabled
		if (Settings.CombatSection.GHOST_BODY_FIGHT_BACK && inventory != null && inventory.length > 0) {
			EntityEquipment equipment = this.npc.getEquipment();
			if (equipment != null) {
				equipment.setItemInMainHand(inventory[0]);
				equipment.setItemInMainHandDropChance(1.0f);
			}
		} else {
			// Don't fight back
			this.npc.setAI(false);
		}

		// Schedule despawn
		this.despawnTaskId = SchedulerUtil.runLater(20L * Settings.CombatSection.GHOST_BODY_DURATION, this::despawn);

		activeNPCs.put(playerUUID, this);
	}

	/**
	 * Spawns a combat NPC for a player who logged out during combat.
	 */
	public static void spawn(Player player) {
		UUID uuid = player.getUniqueId();

		// Remove existing NPC if any
		if (activeNPCs.containsKey(uuid)) {
			activeNPCs.get(uuid).remove();
		}

		// Get player data
		ItemStack[] inventory = player.getInventory().getContents();
		ItemStack[] armor = player.getInventory().getArmorContents();
		double health = player.getHealth();
		Location location = player.getLocation();

		// Clear player inventory so items don't drop twice
		player.getInventory().clear();

		// Create NPC
		new CombatNPC(uuid, player.getName(), location, inventory, armor, health);
	}

	/**
	 * Checks if an entity is a combat NPC.
	 */
	public static boolean isCombatNPC(Entity entity) {
		return entity.hasMetadata(METADATA_KEY);
	}

	/**
	 * Gets the player UUID associated with a combat NPC entity.
	 */
	public static UUID getPlayerUUID(Entity entity) {
		if (!isCombatNPC(entity))
			return null;
		String uuidStr = entity.getMetadata(METADATA_KEY).get(0).asString();
		return UUID.fromString(uuidStr);
	}

	/**
	 * Handles a combat NPC being killed. Drops the player's inventory.
	 */
	public static void onNPCKilled(Zombie npc, Player killer) {
		UUID playerUUID = getPlayerUUID(npc);
		if (playerUUID == null)
			return;

		CombatNPC combatNPC = activeNPCs.remove(playerUUID);
		if (combatNPC == null)
			return;

		// Cancel despawn task
		Bukkit.getScheduler().cancelTask(combatNPC.despawnTaskId);

		// Drop inventory at NPC location
		Location dropLocation = npc.getLocation();
		if (combatNPC.inventory != null) {
			for (ItemStack item : combatNPC.inventory) {
				if (item != null && item.getType() != org.bukkit.Material.AIR) {
					dropLocation.getWorld().dropItemNaturally(dropLocation, item);
				}
			}
		}

		// Notify killer
		if (killer != null) {
			ColorUtil.sendMessage(killer,
					"&aYou killed &c" + combatNPC.playerName + "&a's ghost body and claimed their loot!");
		}

		// Notify the combat logger when they rejoin
		CombatPunishmentManager.markPlayerAsKilledWhileOffline(playerUUID);
	}

	/**
	 * Removes a combat NPC for a player (called when they rejoin).
	 * Returns true if an NPC was removed.
	 */
	public static boolean remove(UUID playerUUID) {
		CombatNPC combatNPC = activeNPCs.remove(playerUUID);
		if (combatNPC != null) {
			combatNPC.removeNPC();
			return true;
		}
		return false;
	}

	/**
	 * Despawns this NPC (called when timer expires).
	 */
	private void despawn() {
		// Drop inventory at NPC location
		Location dropLocation = npc.getLocation();
		if (inventory != null) {
			for (ItemStack item : inventory) {
				if (item != null && item.getType() != org.bukkit.Material.AIR) {
					dropLocation.getWorld().dropItemNaturally(dropLocation, item);
				}
			}
		}

		// Remove NPC
		npc.remove();
		activeNPCs.remove(playerUUID);

		// Notify player when they rejoin that their ghost body despawned
		CombatPunishmentManager.markPlayerGhostBodyDespawned(playerUUID);
	}

	/**
	 * Removes this NPC without dropping items (called when player rejoins).
	 */
	private void removeNPC() {
		Bukkit.getScheduler().cancelTask(despawnTaskId);
		npc.remove();

		// Return inventory to player when they rejoin
		Player player = Bukkit.getPlayer(playerUUID);
		if (player != null && player.isOnline()) {
			player.getInventory().setContents(inventory);
			player.getInventory().setArmorContents(armor);
			ColorUtil.sendMessage(player,
					"&aYou rejoined before your ghost body was killed. Your items have been returned.");
		}
	}

	/**
	 * Cleans up all active NPCs (called on plugin disable).
	 */
	public static void cleanupAll() {
		for (CombatNPC npc : activeNPCs.values()) {
			npc.npc.remove();
		}
		activeNPCs.clear();
	}
}
