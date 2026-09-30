package games.coob.smp.duel;

import games.coob.smp.SMPPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The mobs fighting for each side of a duel.
 * <ul>
 * <li>They spawn behind their team when the countdown starts, frozen and
 * unhurtable until the fight begins.</li>
 * <li>They go after the nearest enemy, player or mob, and never hurt their own
 * side or anyone outside the duel (see {@link DuelMobListener}).</li>
 * <li>They don't decide the duel: a side is out when its players are. Once the
 * duel is decided they are removed.</li>
 * </ul>
 * They are never saved with the world, so a crash can't leave them behind.
 */
public final class DuelMobs {

	/** How often mobs pick targets and are kept inside the arena (ticks). */
	static final int TICK_PERIOD = 10;
	/**
	 * How far mobs look for enemies, and can find a path. Most mobs only follow
	 * 16 blocks, less than the distance between the teams, so this is raised to
	 * cover the whole arena.
	 */
	private static final double TARGET_RANGE = 96;

	private record DuelMob(Mob entity, DuelSide side, Location home) {
	}

	private final ActiveDuel duel;
	/** What the lobby asked for: side -> type -> how many. */
	private final Map<DuelSide, Map<EntityType, Integer>> requested = new EnumMap<>(DuelSide.class);
	private final Map<UUID, DuelMob> mobs = new HashMap<>();

	DuelMobs(ActiveDuel duel, Map<DuelSide, Map<EntityType, Integer>> requested) {
		this.duel = duel;
		for (Map.Entry<DuelSide, Map<EntityType, Integer>> entry : requested.entrySet()) {
			if (!entry.getValue().isEmpty())
				this.requested.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
		}
	}

	/** Whether any mobs were asked for, on either side. */
	boolean isEmpty() {
		return requested.isEmpty();
	}

	Map<EntityType, Integer> getRequested(DuelSide side) {
		return Collections.unmodifiableMap(requested.getOrDefault(side, Map.of()));
	}

	// -------------------------------------------------------------------------
	// Lifecycle
	// -------------------------------------------------------------------------

	/** Spawns every side's mobs, frozen, in a line behind their team. */
	void spawn(DuelArena arena) {
		for (Map.Entry<DuelSide, Map<EntityType, Integer>> entry : requested.entrySet()) {
			DuelSide side = entry.getKey();
			int total = 0;
			for (int count : entry.getValue().values())
				total += count;

			List<Location> spots = arena.mobSpots(side, total);
			int next = 0;
			for (Map.Entry<EntityType, Integer> mob : entry.getValue().entrySet()) {
				for (int i = 0; i < mob.getValue(); i++) {
					Location spot = spots.get(next++);
					Mob spawned = spawnOne(spot, mob.getKey(), side);
					if (spawned != null) {
						mobs.put(spawned.getUniqueId(), new DuelMob(spawned, side, spot));
						DuelManager.getInstance().registerMob(spawned.getUniqueId(), duel);
					}
				}
			}
		}
	}

	private static Mob spawnOne(Location spot, EntityType type, DuelSide side) {
		Class<? extends Entity> entityClass = type.getEntityClass();
		if (entityClass == null || !Mob.class.isAssignableFrom(entityClass) || spot.getWorld() == null)
			return null;

		Entity entity;
		try {
			// Random data on, so skeletons get their bow and pillagers their crossbow
			entity = spot.getWorld().spawn(spot, entityClass.asSubclass(Mob.class), CreatureSpawnEvent.SpawnReason.CUSTOM,
					true, spawned -> prepare(spawned, type, side));
		} catch (RuntimeException e) {
			SMPPlugin.getInstance().getLogger().log(Level.WARNING, "Could not spawn a duel " + type, e);
			return null;
		}
		// Another plugin may have cancelled the spawn
		if (!(entity instanceof Mob mob) || !mob.isValid())
			return null;

		// No jockeys: a baby zombie on a chicken, a skeleton on a spider
		for (Entity passenger : new ArrayList<>(mob.getPassengers()))
			passenger.remove();
		Entity vehicle = mob.getVehicle();
		if (vehicle != null)
			vehicle.remove();
		return mob;
	}

	private static void prepare(Entity entity, EntityType type, DuelSide side) {
		if (!(entity instanceof Mob mob))
			return;
		mob.setPersistent(false);
		mob.setRemoveWhenFarAway(false);
		mob.setCanPickupItems(false);
		mob.customName(Component.text(side.getDisplayName() + " " + displayName(type), side.getColor()));
		mob.setCustomNameVisible(true);
		// Frozen until the fight starts
		mob.setAware(false);

		AttributeInstance followRange = mob.getAttribute(Attribute.FOLLOW_RANGE);
		if (followRange != null && followRange.getBaseValue() < TARGET_RANGE)
			followRange.setBaseValue(TARGET_RANGE);
		// On hard difficulty zombies call in wild zombies when hurt
		AttributeInstance reinforcements = mob.getAttribute(Attribute.SPAWN_REINFORCEMENTS);
		if (reinforcements != null)
			reinforcements.setBaseValue(0);

		if (mob instanceof Ageable ageable)
			ageable.setAdult();
		if (mob instanceof PiglinAbstract piglin)
			piglin.setImmuneToZombification(true);
		if (mob instanceof Hoglin hoglin)
			hoglin.setImmuneToZombification(true);
		if (mob instanceof Raider raider) {
			raider.setCanJoinRaid(false);
			raider.setPatrolLeader(false);
		}
	}

	/** The fight started: the mobs can move. */
	void activate() {
		for (DuelMob mob : mobs.values()) {
			if (mob.entity().isValid())
				mob.entity().setAware(true);
		}
	}

	/**
	 * Keeps every mob inside the arena and on a valid target: the nearest enemy
	 * player still fighting, or the nearest enemy mob.
	 */
	void tick() {
		Iterator<DuelMob> iterator = mobs.values().iterator();
		while (iterator.hasNext()) {
			DuelMob duelMob = iterator.next();
			Mob mob = duelMob.entity();
			if (!mob.isValid() || mob.isDead()) {
				iterator.remove();
				DuelManager.getInstance().unregisterMob(mob.getUniqueId());
				continue;
			}

			if (!duel.arenaContains(mob.getLocation())) {
				mob.teleport(duelMob.home());
				mob.setTarget(null);
			}

			LivingEntity target = mob.getTarget();
			if (target == null || !isEnemy(duelMob.side(), target) || !inRange(mob.getLocation(), target.getLocation())) {
				LivingEntity nearest = nearestEnemy(duelMob.side(), mob.getLocation());
				if (nearest != target)
					mob.setTarget(nearest);
			}
		}
	}

	/** Removes every mob (the duel was decided or cancelled). */
	void removeAll() {
		for (DuelMob duelMob : mobs.values()) {
			Mob mob = duelMob.entity();
			DuelManager.getInstance().unregisterMob(mob.getUniqueId());
			if (!mob.isValid())
				continue;
			Location location = mob.getLocation().add(0, mob.getHeight() / 2, 0);
			mob.getWorld().spawnParticle(Particle.POOF, location, 12, 0.3, mob.getHeight() / 3, 0.3, 0.02);
			mob.remove();
		}
		mobs.clear();
	}

	// -------------------------------------------------------------------------
	// Queries
	// -------------------------------------------------------------------------

	/** The side a duel mob fights for, or null if the entity isn't one of this duel's mobs. */
	DuelSide getSide(Entity entity) {
		DuelMob mob = mobs.get(entity.getUniqueId());
		return mob != null ? mob.side() : null;
	}

	/**
	 * Whether a mob of {@code side} may attack this entity: a player of the
	 * other side who is still fighting, or a mob of the other side.
	 */
	boolean isEnemy(DuelSide side, Entity entity) {
		if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead())
			return false;
		if (entity instanceof Player player)
			return duel.isFightingPlayer(player) && duel.getSide(player) == side.other();
		return getSide(entity) == side.other();
	}

	private LivingEntity nearestEnemy(DuelSide side, Location from) {
		LivingEntity nearest = null;
		double nearestDistance = TARGET_RANGE * TARGET_RANGE;

		List<LivingEntity> candidates = new ArrayList<>(duel.getTeam(side.other()));
		for (DuelMob mob : mobs.values()) {
			if (mob.side() == side.other())
				candidates.add(mob.entity());
		}

		for (LivingEntity candidate : candidates) {
			if (!isEnemy(side, candidate) || !candidate.getWorld().equals(from.getWorld()))
				continue;
			double distance = candidate.getLocation().distanceSquared(from);
			if (distance < nearestDistance) {
				nearest = candidate;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private static boolean inRange(Location from, Location to) {
		return from.getWorld() != null && from.getWorld().equals(to.getWorld())
				&& from.distanceSquared(to) <= TARGET_RANGE * TARGET_RANGE;
	}

	// -------------------------------------------------------------------------
	// Names and icons (also used by the menus)
	// -------------------------------------------------------------------------

	/** e.g. "Iron Golem" */
	public static String displayName(EntityType type) {
		StringBuilder name = new StringBuilder();
		for (String word : type.getKey().getKey().split("_")) {
			if (word.isEmpty())
				continue;
			if (!name.isEmpty())
				name.append(' ');
			name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return name.toString();
	}

	/** The mob's spawn egg, or a plain egg if it has none. */
	public static Material icon(EntityType type) {
		Material egg = Material.matchMaterial(type.getKey().getKey() + "_spawn_egg");
		return egg != null ? egg : Material.EGG;
	}

	/** e.g. "2x Zombie, 1x Iron Golem", or null when there are none. */
	public static String describe(Map<EntityType, Integer> mobs) {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<EntityType, Integer> entry : mobs.entrySet()) {
			if (entry.getValue() > 0)
				parts.add(entry.getValue() + "x " + displayName(entry.getKey()));
		}
		return parts.isEmpty() ? null : String.join(", ", parts);
	}
}
