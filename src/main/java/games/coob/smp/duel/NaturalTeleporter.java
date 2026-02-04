package games.coob.smp.duel;

import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Handles random teleportation for natural arena duels.
 * Picks completely random coordinates within radius (no unexplored check) for scalability on large SMPs.
 * Players still teleport down from the sky (cosmic-style). Based on CosmicTeleportTask.
 */
public class NaturalTeleporter {

	private static final Random random = new Random();

	/**
	 * Result of a natural teleport operation.
	 */
	public static class TeleportResult {
		public final boolean success;
		public final Location center;
		public final Location spawn1;
		public final Location spawn2;
		public final Set<Chunk> usedChunks;
		public final String errorMessage;

		private TeleportResult(boolean success, Location center, Location spawn1, Location spawn2,
				Set<Chunk> usedChunks, String errorMessage) {
			this.success = success;
			this.center = center;
			this.spawn1 = spawn1;
			this.spawn2 = spawn2;
			this.usedChunks = usedChunks;
			this.errorMessage = errorMessage;
		}

		public static TeleportResult success(Location center, Location spawn1, Location spawn2, Set<Chunk> usedChunks) {
			return new TeleportResult(true, center, spawn1, spawn2, usedChunks, null);
		}

		public static TeleportResult failure(String error) {
			return new TeleportResult(false, null, null, null, new HashSet<>(), error);
		}
	}

	/**
	 * Teleports two players to a random natural arena location.
	 *
	 * @param player1 First player (challenger)
	 * @param player2 Second player (opponent)
	 * @return CompletableFuture with the teleport result
	 */
	public static CompletableFuture<TeleportResult> teleportPlayers(Player player1, Player player2) {
		CompletableFuture<TeleportResult> future = new CompletableFuture<>();

		// Run location search on main thread so chunk access works (purely random, no unexplored check)
		SchedulerUtil.runTask(() -> {
			World world = player1.getWorld();
			int searchRadius = Settings.DuelSection.NATURAL_SEARCH_RADIUS;
			int minPlayerDistance = Settings.DuelSection.NATURAL_MIN_PLAYER_DISTANCE;
			int maxAttempts = Settings.DuelSection.NATURAL_MAX_SEARCH_ATTEMPTS;

			Location centerLocation = findRandomLocation(world, searchRadius, maxAttempts);
			if (centerLocation == null) {
				future.complete(TeleportResult.failure(
						"Could not find a suitable location after " + maxAttempts + " attempts. Try again."));
				return;
			}

			// Find two spawn points with minimum distance
			Location spawn1 = findSpawnPoint(centerLocation, minPlayerDistance / 2, 15);
			Location spawn2 = findOppositeSpawnPoint(centerLocation, spawn1, minPlayerDistance);

			if (spawn1 == null || spawn2 == null) {
				future.complete(TeleportResult.failure(
						"Could not find suitable spawn points near the arena center. Try again."));
				return;
			}

			// Collect chunks that will be used (for cleanup after duel)
			Set<Chunk> usedChunks = new HashSet<>();
			usedChunks.add(centerLocation.getChunk());
			usedChunks.add(spawn1.getChunk());
			usedChunks.add(spawn2.getChunk());

			// Add surrounding chunks for border area
			int borderRadius = Settings.DuelSection.BORDER_START_RADIUS;
			int chunkRadius = (borderRadius / 16) + 1;
			int centerChunkX = centerLocation.getBlockX() >> 4;
			int centerChunkZ = centerLocation.getBlockZ() >> 4;

			for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
				for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
					Chunk chunk = world.getChunkAt(centerChunkX + dx, centerChunkZ + dz);
					usedChunks.add(chunk);
				}
			}

			final Location finalCenter = centerLocation;
			final Location finalSpawn1 = spawn1;
			final Location finalSpawn2 = spawn2;
			final Set<Chunk> finalUsedChunks = usedChunks;

			// Load chunks before teleporting
			for (Chunk chunk : finalUsedChunks) {
				if (!chunk.isLoaded()) {
					chunk.load(true);
				}
			}

			// Set yaw to face each other (used when they land)
			final Location spawn1WithYaw = finalSpawn1.clone();
			final Location spawn2WithYaw = finalSpawn2.clone();
			spawn1WithYaw.setYaw(getYawToFace(finalSpawn1, finalSpawn2));
			spawn1WithYaw.setPitch(0);
			spawn2WithYaw.setYaw(getYawToFace(finalSpawn2, finalSpawn1));
			spawn2WithYaw.setPitch(0);

			// Fly down from sky to surface (cosmic-style); complete future when both have landed
			final int[] landed = { 0 };
			Runnable onBothLanded = () -> {
				landed[0]++;
				if (landed[0] == 2) {
					future.complete(TeleportResult.success(finalCenter, finalSpawn1, finalSpawn2, finalUsedChunks));
				}
			};

			DuelFlyDownTeleporter.teleportToGround(player1, spawn1WithYaw, onBothLanded);
			DuelFlyDownTeleporter.teleportToGround(player2, spawn2WithYaw, onBothLanded);
		});

		return future;
	}

	/**
	 * Finds a random location suitable for a duel arena.
	 * Purely random within radius (no unexplored check) for scalability on large SMPs.
	 */
	private static Location findRandomLocation(World world, int searchRadius, int maxAttempts) {
		Location worldSpawn = world.getSpawnLocation();

		for (int attempt = 0; attempt < maxAttempts; attempt++) {
			// Completely random coordinates within search radius
			int x = worldSpawn.getBlockX() + random.nextInt(searchRadius * 2) - searchRadius;
			int z = worldSpawn.getBlockZ() + random.nextInt(searchRadius * 2) - searchRadius;

			// Ensure chunk is loaded for block access (main thread)
			Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
			if (!chunk.isLoaded()) {
				chunk.load(true);
			}

			Location location = new Location(world, x + 0.5, 0, z + 0.5);

			// Optional: skip banned biomes (can be disabled in config for more success)
			if (Settings.DuelSection.NATURAL_BANNED_BIOMES != null && !Settings.DuelSection.NATURAL_BANNED_BIOMES.isEmpty()
					&& isBannedBiome(location)) {
				continue;
			}

			// Find highest safe block at this x,z
			int highestY = findHighestSafeBlock(location);
			if (highestY == -1) {
				continue;
			}

			location.setY(highestY);

			if (isSafeLocation(location)) {
				return location;
			}
		}

		return null;
	}

	/**
	 * Finds a spawn point at a given distance from center.
	 */
	private static Location findSpawnPoint(Location center, int distance, int attempts) {
		for (int i = 0; i < attempts; i++) {
			double angle = random.nextDouble() * 2 * Math.PI;
			int x = center.getBlockX() + (int) (distance * Math.cos(angle));
			int z = center.getBlockZ() + (int) (distance * Math.sin(angle));

			Location spawn = new Location(center.getWorld(), x + 0.5, 0, z + 0.5);
			int y = findHighestSafeBlock(spawn);

			if (y != -1) {
				spawn.setY(y);
				if (isSafeLocation(spawn)) {
					return spawn;
				}
			}
		}
		return null;
	}

	/**
	 * Finds a spawn point opposite to the first spawn.
	 */
	private static Location findOppositeSpawnPoint(Location center, Location spawn1, int minDistance) {
		// Calculate opposite direction from center
		double dx = spawn1.getX() - center.getX();
		double dz = spawn1.getZ() - center.getZ();

		// Normalize and reverse
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length > 0) {
			dx = -dx / length * (minDistance / 2);
			dz = -dz / length * (minDistance / 2);
		}

		for (int attempt = 0; attempt < 10; attempt++) {
			int x = center.getBlockX() + (int) dx + random.nextInt(5) - 2;
			int z = center.getBlockZ() + (int) dz + random.nextInt(5) - 2;

			Location spawn = new Location(center.getWorld(), x + 0.5, 0, z + 0.5);
			int y = findHighestSafeBlock(spawn);

			if (y != -1) {
				spawn.setY(y);
				if (isSafeLocation(spawn) && spawn.distance(spawn1) >= minDistance * 0.8) {
					return spawn;
				}
			}
		}

		return null;
	}

	/**
	 * Finds the highest safe block at a location.
	 */
	private static int findHighestSafeBlock(Location location) {
		World world = location.getWorld();
		int x = location.getBlockX();
		int z = location.getBlockZ();

		// Start from world max height
		for (int y = world.getMaxHeight() - 1; y > world.getMinHeight(); y--) {
			Block block = world.getBlockAt(x, y, z);
			Block blockAbove = block.getRelative(BlockFace.UP);
			Block blockAbove2 = blockAbove.getRelative(BlockFace.UP);

			// Ground must be solid, two blocks above must be air
			if (block.getType().isSolid() && blockAbove.getType().isAir() && blockAbove2.getType().isAir()) {
				// Check for banned blocks
				if (!isBannedBlock(block)) {
					return y + 1; // Return the air block above ground
				}
			}
		}

		return -1;
	}

	/**
	 * Checks if a location is safe for teleportation.
	 */
	private static boolean isSafeLocation(Location location) {
		Block block = location.getBlock();
		Block blockBelow = block.getRelative(BlockFace.DOWN);
		Block blockAbove = block.getRelative(BlockFace.UP);

		// Must have solid ground and 2 air blocks
		if (!blockBelow.getType().isSolid())
			return false;
		if (!block.getType().isAir())
			return false;
		if (!blockAbove.getType().isAir())
			return false;

		// Check for nearby hazards
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Block nearby = location.getWorld().getBlockAt(
						location.getBlockX() + dx,
						location.getBlockY() - 1,
						location.getBlockZ() + dz);
				if (isBannedBlock(nearby)) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Checks if a biome is banned for duels.
	 */
	private static boolean isBannedBiome(Location location) {
		String biomeName = location.getBlock().getBiome().name();
		for (String banned : Settings.DuelSection.NATURAL_BANNED_BIOMES) {
			if (biomeName.contains(banned.toUpperCase())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if a block type is banned.
	 */
	private static boolean isBannedBlock(Block block) {
		String blockName = block.getType().name();
		for (String banned : Settings.DuelSection.NATURAL_BANNED_BLOCKS) {
			if (blockName.equalsIgnoreCase(banned)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Calculates yaw to face from one location to another.
	 */
	/**
	 * Returns the yaw angle (in degrees) to face from one location toward another.
	 * Public so arena teleport can set spawn yaw for facing each other.
	 */
	public static float getYawToFace(Location from, Location to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		double yaw = Math.atan2(-dx, dz) * 180 / Math.PI;
		return (float) yaw;
	}

	/**
	 * Unloads chunks used by a duel (for cleanup).
	 */
	public static void unloadChunks(Set<Chunk> chunks) {
		if (!Settings.DuelSection.CLEANUP_UNLOAD_NATURAL_CHUNKS) {
			return;
		}

		for (Chunk chunk : chunks) {
			if (chunk.isLoaded()) {
				// Check if any players are in the chunk
				boolean hasPlayers = false;
				for (org.bukkit.entity.Entity entity : chunk.getEntities()) {
					if (entity instanceof Player) {
						hasPlayers = true;
						break;
					}
				}

				if (!hasPlayers) {
					chunk.unload(true);
				}
			}
		}
	}

	/**
	 * No-op; kept for API compatibility (explored cache removed for scalability).
	 */
	public static void clearExploredCache() {
	}
}
