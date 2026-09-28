package games.coob.smp.duel;

import games.coob.smp.settings.Settings;
import games.coob.smp.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

/**
 * Picks a random spot in the overworld for a natural duel.
 * Chunks are loaded (or generated) in the background, so searching never
 * freezes the server.
 */
public final class NaturalArenaFinder {

	private NaturalArenaFinder() {
	}

	/**
	 * Finds an arena, or completes with null if no safe spot was found.
	 * The future always completes on the main thread.
	 */
	/**
	 * @param cancelled stops the search early (e.g. a player left meanwhile)
	 */
	public static CompletableFuture<DuelArena> find(int teamSize, BooleanSupplier cancelled) {
		CompletableFuture<DuelArena> result = new CompletableFuture<>();
		World world = getOverworld();
		if (world == null) {
			result.complete(null);
		} else {
			attempt(world, teamSize, 0, cancelled, result);
		}
		return result;
	}

	private static World getOverworld() {
		for (World world : Bukkit.getWorlds()) {
			if (world.getEnvironment() == World.Environment.NORMAL)
				return world;
		}
		return null;
	}

	private static void attempt(World world, int teamSize, int attempt, BooleanSupplier cancelled,
			CompletableFuture<DuelArena> result) {
		if (attempt >= Settings.DuelSection.NATURAL_MAX_SEARCH_ATTEMPTS || cancelled.getAsBoolean()) {
			result.complete(null);
			return;
		}

		ThreadLocalRandom random = ThreadLocalRandom.current();
		int radius = Settings.DuelSection.NATURAL_SEARCH_RADIUS;
		Location spawn = world.getSpawnLocation();
		tryCenter(world, spawn.getBlockX() + random.nextInt(-radius, radius + 1),
				spawn.getBlockZ() + random.nextInt(-radius, radius + 1), teamSize, attempt, cancelled, result);
	}

	private static void tryCenter(World world, int centerX, int centerZ, int teamSize, int attempt,
			BooleanSupplier cancelled, CompletableFuture<DuelArena> result) {
		if (cancelled.getAsBoolean()) {
			result.complete(null);
			return;
		}
		ThreadLocalRandom random = ThreadLocalRandom.current();

		// Teams land on opposite sides of the centre; bigger teams start further apart
		double half = (Settings.DuelSection.NATURAL_MIN_PLAYER_DISTANCE + 4.0 * (teamSize - 1)) / 2.0;
		double angle = random.nextDouble(Math.PI * 2);
		int offsetX = (int) Math.round(Math.cos(angle) * half);
		int offsetZ = (int) Math.round(Math.sin(angle) * half);
		int[][] columns = {
				{ centerX, centerZ },
				{ centerX + offsetX, centerZ + offsetZ },
				{ centerX - offsetX, centerZ - offsetZ } };

		// Also load the chunks next to each spawn (the landing area is loaded fully before players move)
		int spread = teamSize > 1 ? 2 : 0;
		Set<Long> chunks = new HashSet<>();
		for (int[] column : columns) {
			for (int dx : new int[] { -spread, 0, spread }) {
				for (int dz : new int[] { -spread, 0, spread })
					chunks.add(chunkKey((column[0] + dx) >> 4, (column[1] + dz) >> 4));
			}
		}
		List<CompletableFuture<?>> loads = new ArrayList<>();
		for (long key : chunks)
			loads.add(world.getChunkAtAsync((int) (key >> 32), (int) key, true));

		CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenComplete((ignored, error) ->
				runOnMainThread(() -> {
					DuelArena arena = error == null ? evaluate(world, columns) : null;
					if (arena != null) {
						result.complete(arena);
					} else {
						attempt(world, teamSize, attempt + 1, cancelled, result);
					}
				}));
	}

	private static DuelArena evaluate(World world, int[][] columns) {
		Location center = surface(world, columns[0][0], columns[0][1]);
		if (center == null || isBannedBiome(center))
			return null;

		Location spawn1 = surface(world, columns[1][0], columns[1][1]);
		Location spawn2 = surface(world, columns[2][0], columns[2][1]);
		if (spawn1 == null || spawn2 == null)
			return null;

		return new DuelArena(center, spawn1, spawn2, null);
	}

	/**
	 * The standing spot on top of the highest block at x/z, or null if it is
	 * water, lava, a banned block or has no headroom.
	 */
	private static Location surface(World world, int x, int z) {
		int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
		if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 3)
			return null;

		Block ground = world.getBlockAt(x, y, z);
		if (ground.isLiquid() || !ground.getType().isSolid())
			return null;

		Block feet = ground.getRelative(0, 1, 0);
		Block head = ground.getRelative(0, 2, 0);
		if (!feet.isPassable() || feet.isLiquid() || !head.isPassable() || head.isLiquid())
			return null;

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				// Don't load a neighbouring chunk just for this check
				if (!world.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4))
					continue;
				if (isBannedBlock(ground.getRelative(dx, 0, dz).getType())
						|| isBannedBlock(ground.getRelative(dx, 1, dz).getType()))
					return null;
			}
		}

		return new Location(world, x + 0.5, y + 1, z + 0.5);
	}

	private static long chunkKey(int chunkX, int chunkZ) {
		return (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
	}

	private static boolean isBannedBiome(Location location) {
		String biome = location.getBlock().getBiome().getKey().getKey().toUpperCase(Locale.ROOT);
		for (String banned : Settings.DuelSection.NATURAL_BANNED_BIOMES) {
			if (biome.contains(banned))
				return true;
		}
		return false;
	}

	private static boolean isBannedBlock(Material material) {
		return Settings.DuelSection.NATURAL_BANNED_BLOCKS.contains(material.name());
	}

	private static void runOnMainThread(Runnable runnable) {
		if (Bukkit.isPrimaryThread()) {
			runnable.run();
		} else {
			SchedulerUtil.runTask(runnable);
		}
	}
}
