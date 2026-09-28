package games.coob.smp.combat;

import games.coob.smp.SMPPlugin;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a ghost body NPC that spawns when a player logs out during combat.
 * The NPC can be killed by other players to get the combat-logger's loot.
 */
public class CombatNPC {

	private static final Map<UUID, CombatNPC> activeNPCs = new HashMap<>();
	private static final NamespacedKey OWNER_KEY = new NamespacedKey(SMPPlugin.getInstance(), "combat_npc");

	private final UUID playerUUID;
	private final String playerName;
	private final Zombie npc;
	private final ItemStack[] inventory;
	private final ItemStack[] armor;
	private final double health;
	private final org.bukkit.scheduler.BukkitTask despawnTask;
	private int chunkX;
	private int chunkZ;

	private CombatNPC(UUID playerUUID, String playerName, Location location, ItemStack[] inventory,
			ItemStack[] armor, double health) {
		this.playerUUID = playerUUID;
		this.playerName = playerName;
		this.inventory = inventory;
		this.armor = armor;
		this.health = health;

		// Spawn zombie NPC
		this.npc = (Zombie) location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
		this.npc.customName(ColorUtil.toComponent("&c" + playerName + " &7(Combat Logger)"));
		this.npc.setCustomNameVisible(true);
		this.npc.setRemoveWhenFarAway(false);
		this.npc.setCanPickupItems(false);
		this.npc.setAdult();
		this.npc.setShouldBurnInDay(false);
		// Never saved with the chunk: loot is handled in memory and dropped on shutdown
		this.npc.setPersistent(false);

		// Set metadata to identify this as a combat NPC
		this.npc.getPersistentDataContainer().set(OWNER_KEY, PersistentDataType.STRING, playerUUID.toString());

		// Apply health
		if (Settings.CombatSection.GHOST_BODY_USE_PLAYER_HEALTH && health > 0) {
			this.npc.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(health);
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
		this.despawnTask = SchedulerUtil.runLater(20L * Settings.CombatSection.GHOST_BODY_DURATION, this::despawn);

		// Keep the chunk loaded so the body stays killable even if everyone walks away
		this.chunkX = location.getBlockX() >> 4;
		this.chunkZ = location.getBlockZ() >> 4;
		location.getWorld().addPluginChunkTicket(chunkX, chunkZ, SMPPlugin.getInstance());

		activeNPCs.put(playerUUID, this);
	}

	/**
	 * Spawns a combat NPC for a player who logged out during combat.
	 */
	public static void spawn(Player player) {
		UUID uuid = player.getUniqueId();

		// Remove existing NPC if any
		if (activeNPCs.containsKey(uuid)) {
			activeNPCs.get(uuid).removeNPC();
		}

		// Get player data
		ItemStack[] inventory = player.getInventory().getContents();
		ItemStack[] armor = player.getInventory().getArmorContents();
		double health = player.getHealth();
		Location location = player.getLocation();

		// Clear player inventory so items don't drop twice, and keep a copy on disk in case of a crash
		player.getInventory().clear();
		GhostLootStore.getInstance().store(uuid, inventory);

		// Create NPC
		new CombatNPC(uuid, player.getName(), location, inventory, armor, health);
	}

	/**
	 * Checks if an entity is a combat NPC.
	 */
	public static boolean isCombatNPC(Entity entity) {
		return entity.getPersistentDataContainer().has(OWNER_KEY, PersistentDataType.STRING);
	}

	/**
	 * Gets the player UUID associated with a combat NPC entity.
	 */
	public static UUID getPlayerUUID(Entity entity) {
		if (!isCombatNPC(entity))
			return null;
		String uuidStr = entity.getPersistentDataContainer().get(OWNER_KEY, PersistentDataType.STRING);
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
		combatNPC.despawnTask.cancel();
		combatNPC.releaseChunk();

		// Drop inventory at NPC location
		combatNPC.dropInventory();
		GhostLootStore.getInstance().clear(playerUUID);

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
		dropInventory();
		GhostLootStore.getInstance().clear(playerUUID);

		// Remove NPC
		npc.remove();
		releaseChunk();
		activeNPCs.remove(playerUUID);

		// Notify player when they rejoin that their ghost body despawned
		CombatPunishmentManager.markPlayerGhostBodyDespawned(playerUUID);
	}

	private void releaseChunk() {
		// Tickets are per plugin and chunk, so keep it while another body is in the same chunk
		for (CombatNPC other : activeNPCs.values()) {
			if (other != this && other.chunkX == chunkX && other.chunkZ == chunkZ
					&& other.npc.getWorld().equals(npc.getWorld()))
				return;
		}
		npc.getWorld().removePluginChunkTicket(chunkX, chunkZ, SMPPlugin.getInstance());
	}

	private void dropInventory() {
		Location dropLocation = npc.getLocation();
		if (inventory != null && dropLocation.getWorld() != null) {
			for (ItemStack item : inventory) {
				if (item != null && !item.getType().isAir()) {
					dropLocation.getWorld().dropItemNaturally(dropLocation, item);
				}
			}
		}
	}

	/**
	 * Removes this NPC without dropping items (called when player rejoins).
	 */
	private void removeNPC() {
		despawnTask.cancel();
		double bodyHealth = npc.isValid() ? npc.getHealth() : -1;
		npc.remove();
		releaseChunk();
		GhostLootStore.getInstance().clear(playerUUID);

		// Return inventory to player when they rejoin
		Player player = Bukkit.getPlayer(playerUUID);
		if (player != null && player.isOnline()) {
			player.getInventory().setContents(inventory);
			player.getInventory().setArmorContents(armor);
			// A quick relog doesn't reset the fight: damage the body took counts, and they are still in combat
			if (Settings.CombatSection.GHOST_BODY_USE_PLAYER_HEALTH && bodyHealth > 0)
				player.setHealth(Math.min(player.getHealth(), bodyHealth));
			CombatTracker.tag(player);
			ColorUtil.sendMessage(player,
					"&aYou rejoined before your ghost body was killed. Your items have been returned.");
		}
	}

	/**
	 * Cleans up all active NPCs (called on plugin disable).
	 */
	public static void cleanupAll() {
		for (CombatNPC npc : activeNPCs.values()) {
			// The owner is offline and their inventory was cleared, so drop the loot
			// rather than lose it
			npc.despawnTask.cancel();
			npc.dropInventory();
			GhostLootStore.getInstance().clear(npc.playerUUID);
			npc.npc.remove();
			npc.releaseChunk();
		}
		activeNPCs.clear();
	}
}
