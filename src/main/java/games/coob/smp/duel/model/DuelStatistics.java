package games.coob.smp.duel.model;

import games.coob.smp.config.ConfigFile;
import lombok.Getter;

import java.util.UUID;

/**
 * Tracks win/loss statistics for duels.
 * Persists to duel-stats.yml.
 */
public class DuelStatistics extends ConfigFile {

	@Getter
	private static final DuelStatistics instance = new DuelStatistics();

	private DuelStatistics() {
		super("duel-stats.yml");
	}

	@Override
	protected void onLoad() {
		// Stats are loaded on demand from config
	}

	@Override
	protected void onSave() {
		// Stats are saved immediately when modified
	}

	/**
	 * Get wins for a player.
	 */
	public int getWins(UUID playerId) {
		return getConfig().getInt("Players." + playerId.toString() + ".wins", 0);
	}

	/**
	 * Get losses for a player.
	 */
	public int getLosses(UUID playerId) {
		return getConfig().getInt("Players." + playerId.toString() + ".losses", 0);
	}

	/**
	 * Get current win streak for a player.
	 */
	public int getWinStreak(UUID playerId) {
		return getConfig().getInt("Players." + playerId.toString() + ".streak", 0);
	}

	/**
	 * Get best win streak for a player.
	 */
	public int getBestStreak(UUID playerId) {
		return getConfig().getInt("Players." + playerId.toString() + ".best_streak", 0);
	}

	/**
	 * Add a win for a player.
	 */
	public void addWin(UUID playerId) {
		String path = "Players." + playerId.toString();
		int wins = getConfig().getInt(path + ".wins", 0) + 1;
		int streak = getConfig().getInt(path + ".streak", 0) + 1;
		int bestStreak = getConfig().getInt(path + ".best_streak", 0);

		getConfig().set(path + ".wins", wins);
		getConfig().set(path + ".streak", streak);

		if (streak > bestStreak) {
			getConfig().set(path + ".best_streak", streak);
		}

		save();
	}

	/**
	 * Add a loss for a player.
	 */
	public void addLoss(UUID playerId) {
		String path = "Players." + playerId.toString();
		int losses = getConfig().getInt(path + ".losses", 0) + 1;

		getConfig().set(path + ".losses", losses);
		getConfig().set(path + ".streak", 0); // Reset streak on loss

		save();
	}

	/**
	 * Get win rate as a percentage.
	 */
	public double getWinRate(UUID playerId) {
		int wins = getWins(playerId);
		int losses = getLosses(playerId);
		int total = wins + losses;
		if (total == 0)
			return 0.0;
		return (wins * 100.0) / total;
	}

	/**
	 * Get total duels played.
	 */
	public int getTotalDuels(UUID playerId) {
		return getWins(playerId) + getLosses(playerId);
	}

	/**
	 * Holder for player statistics.
	 */
	public static class PlayerStats {
		public final int wins;
		public final int losses;
		public final int streak;
		public final int bestStreak;
		public final double winRate;
		public final int totalDuels;

		public PlayerStats(UUID playerId) {
			DuelStatistics stats = DuelStatistics.getInstance();
			this.wins = stats.getWins(playerId);
			this.losses = stats.getLosses(playerId);
			this.streak = stats.getWinStreak(playerId);
			this.bestStreak = stats.getBestStreak(playerId);
			this.winRate = stats.getWinRate(playerId);
			this.totalDuels = wins + losses;
		}
	}
}
