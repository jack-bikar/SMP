package games.coob.smp.duel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Stops duels from griefing the world around them. While an arena is in use
 * (and until it is cleaned up):
 * <ul>
 * <li>Fire doesn't spread or burn blocks, and lava doesn't set things alight.
 * Fire a duelist lights is removed afterwards.</li>
 * <li>Lava and water can't flow out of the arena; where they flowed inside is
 * put back afterwards.</li>
 * <li>Explosions don't reach outside the arena. In natural arenas the terrain
 * they destroy drops nothing and comes back; chests and other block entities
 * are never blown up. Admin-built arenas only lose blocks placed in the duel.</li>
 * <li>Everything else that changes arena blocks (pistons, falling sand, cobble
 * forming, bone meal, dispensers, sponges...) is undone afterwards too, and no
 * withers or golems can be built.</li>
 * </ul>
 * Players breaking and placing blocks is handled by {@link DuelListener}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DuelArenaListener implements Listener {

	private static final DuelArenaListener instance = new DuelArenaListener();

	public static DuelArenaListener getInstance() {
		return instance;
	}

	private static ActiveDuel duelAt(Block block) {
		return DuelManager.getInstance().getDuelAt(block.getWorld(), block.getX() + 0.5, block.getZ() + 0.5);
	}

	/** No arena is in use: these events fire all the time, so most handlers stop right here. */
	private static boolean idle() {
		return !DuelManager.getInstance().hasProtectedArenas();
	}

	// -------------------------------------------------------------------------
	// Fire
	// -------------------------------------------------------------------------

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onFireSpread(final BlockSpreadEvent event) {
		if (idle())
			return;
		Material type = event.getNewState().getType();
		if ((type == Material.FIRE || type == Material.SOUL_FIRE)
				&& (duelAt(event.getBlock()) != null || duelAt(event.getSource()) != null))
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBurn(final BlockBurnEvent event) {
		if (idle())
			return;
		Block source = event.getIgnitingBlock();
		if (duelAt(event.getBlock()) != null || (source != null && duelAt(source) != null))
			event.setCancelled(true);
	}

	/**
	 * Only a player can light a fire in an arena (flint and steel, fire charges),
	 * and it is removed after the duel. Lava, spreading fire, lightning and
	 * explosions don't light anything.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onIgnite(final BlockIgniteEvent event) {
		if (idle())
			return;
		Block block = event.getBlock();
		Block source = event.getIgnitingBlock();
		ActiveDuel duel = duelAt(block);
		if (duel == null && source != null)
			duel = duelAt(source);
		if (duel == null)
			return;

		Player player = igniter(event.getIgnitingEntity());
		if (player == null || !duel.arenaContains(block.getLocation())) {
			event.setCancelled(true);
			return;
		}
		// Duelists and outsiders are held to the same rules as for placing blocks
		ActiveDuel playerDuel = DuelManager.getInstance().getActiveDuel(player);
		if (playerDuel != duel) {
			event.setCancelled(true);
			return;
		}
		duel.trackChangedBlock(block);
	}

	private static Player igniter(Entity entity) {
		if (entity instanceof Player player)
			return player;
		if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter)
			return shooter;
		return null;
	}

	// -------------------------------------------------------------------------
	// Liquids and other block changes
	// -------------------------------------------------------------------------

	/** Lava and water: stay inside the arena, and are removed afterwards. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onFlow(final BlockFromToEvent event) {
		if (idle())
			return;
		ActiveDuel from = duelAt(event.getBlock());
		ActiveDuel to = duelAt(event.getToBlock());
		if (from != null && to != from) {
			event.setCancelled(true);
			return;
		}
		if (to != null)
			to.trackChangedBlock(event.getToBlock());
	}

	/** Cobblestone and obsidian from lava meeting water, snow, ice, frost walker... */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onForm(final BlockFormEvent event) {
		if (idle())
			return;
		track(event.getBlock());
	}

	/** Falling sand and gravel, trampled farmland, mobs eating grass... */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityChangeBlock(final EntityChangeBlockEvent event) {
		if (idle())
			return;
		track(event.getBlock());
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onLeavesDecay(final LeavesDecayEvent event) {
		if (idle())
			return;
		// Logs broken in the duel come back, so the leaves must stay
		if (duelAt(event.getBlock()) != null)
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPistonExtend(final BlockPistonExtendEvent event) {
		if (idle())
			return;
		trackPiston(event, event.getBlocks());
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPistonRetract(final BlockPistonRetractEvent event) {
		if (idle())
			return;
		trackPiston(event, event.getBlocks());
	}

	/**
	 * Records every block a piston moves and both of its neighbours along the
	 * piston's axis (where it lands, whichever way it moves); pistons can't move
	 * anything across the arena's edge.
	 */
	private static void trackPiston(BlockPistonEvent event, List<Block> moved) {
		ActiveDuel duel = duelAt(event.getBlock());
		BlockFace direction = event.getDirection();
		List<Block> affected = new ArrayList<>();
		affected.add(event.getBlock().getRelative(direction));
		affected.add(event.getBlock().getRelative(direction.getOppositeFace()));
		for (Block block : moved) {
			affected.add(block);
			affected.add(block.getRelative(direction));
			affected.add(block.getRelative(direction.getOppositeFace()));
		}

		for (Block block : affected) {
			if (duelAt(block) != duel) {
				event.setCancelled(true);
				return;
			}
		}
		if (duel != null) {
			for (Block block : affected)
				duel.trackChangedBlock(block);
		}
	}

	/** Trees and flowers grown with bone meal. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onGrow(final StructureGrowEvent event) {
		if (idle())
			return;
		trackAll(event.getLocation(), event.getBlocks());
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onFertilize(final BlockFertilizeEvent event) {
		if (idle())
			return;
		trackAll(event.getBlock().getLocation(), event.getBlocks());
	}

	/**
	 * Growth started inside an arena: what it replaces is recorded, and the parts
	 * that would grow outside the arena are dropped.
	 */
	private static void trackAll(Location origin, List<BlockState> newStates) {
		ActiveDuel duel = DuelManager.getInstance().getDuelAt(origin);
		if (duel == null)
			return;
		newStates.removeIf(state -> !duel.arenaContains(state.getLocation()));
		for (BlockState state : newStates)
			duel.trackChangedBlock(state.getBlock());
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onSpongeAbsorb(final SpongeAbsorbEvent event) {
		if (idle())
			return;
		ActiveDuel duel = duelAt(event.getBlock());
		if (duel == null)
			return;
		for (BlockState state : event.getBlocks())
			duel.trackChangedBlock(state.getBlock());
		event.getBlocks().removeIf(state -> !duel.arenaContains(state.getLocation()));
	}

	/** Dispensers placing lava, water or fire in front of them. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDispense(final BlockDispenseEvent event) {
		if (idle())
			return;
		Block dispenser = event.getBlock();
		ActiveDuel duel = duelAt(dispenser);
		if (duel == null || !(dispenser.getBlockData() instanceof Directional directional))
			return;
		Block target = dispenser.getRelative(directional.getFacing());
		if (!duel.arenaContains(target.getLocation())) {
			event.setCancelled(true);
			return;
		}
		duel.trackChangedBlock(target);
	}

	/** Withers, iron golems, snow golems and copper golems built from blocks. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBuiltMob(final CreatureSpawnEvent event) {
		if (idle())
			return;
		if (event.getSpawnReason().name().startsWith("BUILD_") && DuelManager.getInstance().getDuelAt(event.getLocation()) != null)
			event.setCancelled(true);
	}

	private static void track(Block block) {
		ActiveDuel duel = duelAt(block);
		if (duel != null)
			duel.trackChangedBlock(block);
	}

	// -------------------------------------------------------------------------
	// Explosions
	// -------------------------------------------------------------------------

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityExplode(final EntityExplodeEvent event) {
		if (idle())
			return;
		protect(event.getLocation(), event.blockList());
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockExplode(final BlockExplodeEvent event) {
		if (idle())
			return;
		protect(event.getBlock().getLocation(), event.blockList());
	}

	/**
	 * Explosions (TNT, crystals, anchors, creepers):
	 * <ul>
	 * <li>One that goes off inside an arena doesn't break anything outside it.</li>
	 * <li>Blocks made during the duel are blown up as usual.</li>
	 * <li>Admin-built arenas and block entities (chests...) are never damaged.</li>
	 * <li>Natural terrain is removed without drops and comes back afterwards.</li>
	 * </ul>
	 */
	private static void protect(Location center, List<Block> blocks) {
		if (blocks.isEmpty())
			return;
		// The explosion's reach, so arenas it can't touch are skipped
		World world = center.getWorld();
		double minX = center.getX();
		double maxX = minX;
		double minZ = center.getZ();
		double maxZ = minZ;
		for (Block block : blocks) {
			minX = Math.min(minX, block.getX());
			maxX = Math.max(maxX, block.getX() + 1);
			minZ = Math.min(minZ, block.getZ());
			maxZ = Math.max(maxZ, block.getZ() + 1);
		}

		for (ActiveDuel duel : DuelManager.getInstance().getProtectedDuels()) {
			if (!duel.arenaOverlaps(world, minX, minZ, maxX, maxZ))
				continue;
			boolean fromInside = duel.arenaContains(center);
			Iterator<Block> iterator = blocks.iterator();
			while (iterator.hasNext()) {
				Block block = iterator.next();
				if (!duel.arenaContains(world, block.getX() + 0.5, block.getZ() + 0.5)) {
					if (fromInside)
						iterator.remove();
					continue;
				}
				if (duel.isDuelMade(block.getLocation()))
					continue;

				iterator.remove();
				if (duel.getArena().created() || block.getState(false) instanceof TileState)
					continue;
				// Removed here instead of by the explosion, so it drops nothing
				duel.trackChangedBlock(block);
				block.setType(Material.AIR, false);
			}
		}
	}
}
