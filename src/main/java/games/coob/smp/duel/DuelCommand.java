package games.coob.smp.duel;

import games.coob.smp.duel.model.DuelStatistics;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles player duel commands.
 * /duel <player> - Challenge a player
 * /duel accept <player> - Accept a duel request
 * /duel deny <player> - Deny a duel request
 * /duel queue - Join random matchmaking
 * /duel leave - Leave queue or loot phase
 * /duel stats [player] - View statistics
 */
public class DuelCommand implements CommandExecutor, TabCompleter {

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			ColorUtil.sendMessage(sender, "&cThis command can only be used by players.");
			return true;
		}

		if (!Settings.DuelSection.ENABLE_DUELS) {
			ColorUtil.sendMessage(player, "&cDuels are currently disabled.");
			return true;
		}

		if (args.length == 0) {
			sendHelp(player);
			return true;
		}

		String sub = args[0].toLowerCase();

		switch (sub) {
			case "accept" -> {
				if (args.length < 2) {
					ColorUtil.sendMessage(player, "&cUsage: /duel accept <player>");
					return true;
				}
				DuelManager.getInstance().acceptRequest(player, args[1]);
			}
			case "deny", "decline" -> {
				if (args.length < 2) {
					ColorUtil.sendMessage(player, "&cUsage: /duel deny <player>");
					return true;
				}
				DuelManager.getInstance().denyRequest(player, args[1]);
			}
			case "queue", "q" -> {
				DuelQueueManager.getInstance().joinQueue(player);
			}
			case "leave", "exit" -> {
				handleLeave(player);
			}
			case "return", "back" -> {
				if (DuelManager.getInstance().returnNow(player)) {
					ColorUtil.sendMessage(player, "&aReturned to your previous location.");
				} else {
					ColorUtil.sendMessage(player, "&cYou are not in a duel return countdown.");
				}
			}
			case "stats", "statistics" -> {
				if (args.length < 2) {
					showStats(player, player);
				} else {
					Player target = Bukkit.getPlayer(args[1]);
					if (target == null) {
						// Try to get offline player stats
						@SuppressWarnings("deprecation")
						org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
						if (offline.hasPlayedBefore()) {
							showOfflineStats(player, offline);
						} else {
							ColorUtil.sendMessage(player, "&cPlayer not found.");
						}
					} else {
						showStats(player, target);
					}
				}
			}
			default -> {
				// Treat as player name for challenge
				Player target = Bukkit.getPlayer(sub);
				if (target == null) {
					// Check if it could be a partial match
					for (Player online : Bukkit.getOnlinePlayers()) {
						if (online.getName().toLowerCase().startsWith(sub)) {
							target = online;
							break;
						}
					}
				}

				if (target == null) {
					ColorUtil.sendMessage(player, "&cPlayer '&e" + sub + "&c' not found.");
					return true;
				}

				DuelManager.getInstance().sendRequest(player, target);
			}
		}

		return true;
	}

	private void sendHelp(Player player) {
		ColorUtil.sendMessage(player, "&6&l⚔ Duel Commands &6&l⚔");
		ColorUtil.sendMessage(player, "&e/duel <player> &7- Challenge a player to a duel");
		ColorUtil.sendMessage(player, "&e/duel accept <player> &7- Accept a duel request");
		ColorUtil.sendMessage(player, "&e/duel deny <player> &7- Deny a duel request");
		ColorUtil.sendMessage(player, "&e/duel queue &7- Join random matchmaking");
		ColorUtil.sendMessage(player, "&e/duel leave &7- Leave queue or loot phase");
		ColorUtil.sendMessage(player, "&e/duel return &7- Teleport back now (after duel ends)");
		ColorUtil.sendMessage(player, "&e/duel stats [player] &7- View duel statistics");
	}

	private void handleLeave(Player player) {
		// Check if in queue
		if (DuelQueueManager.getInstance().isInQueue(player)) {
			DuelQueueManager.getInstance().leaveQueue(player);
			return;
		}

		ActiveDuel duel = DuelManager.getInstance().getActiveDuel(player);
		if (duel != null && duel.getState() == ActiveDuel.DuelState.LOOT_PHASE) {
			duel.leaveLootPhase(player);
			return;
		}
		if (duel != null && duel.getState() == ActiveDuel.DuelState.RETURN_COUNTDOWN) {
			if (DuelManager.getInstance().returnNow(player)) {
				ColorUtil.sendMessage(player, "&aReturned to your previous location.");
			}
			return;
		}

		ColorUtil.sendMessage(player, "&cYou are not in the queue or in a loot phase.");
	}

	private void showStats(Player viewer, Player target) {
		DuelStatistics.PlayerStats stats = new DuelStatistics.PlayerStats(target.getUniqueId());

		ColorUtil.sendMessage(viewer, "&6&l⚔ Duel Stats: &e" + target.getName() + " &6&l⚔");
		ColorUtil.sendMessage(viewer, "&7Wins: &a" + stats.wins);
		ColorUtil.sendMessage(viewer, "&7Losses: &c" + stats.losses);
		ColorUtil.sendMessage(viewer, "&7Win Rate: &e" + String.format("%.1f", stats.winRate) + "%");
		ColorUtil.sendMessage(viewer, "&7Current Streak: &6" + stats.streak);
		ColorUtil.sendMessage(viewer, "&7Best Streak: &6" + stats.bestStreak);
		ColorUtil.sendMessage(viewer, "&7Total Duels: &7" + stats.totalDuels);
	}

	private void showOfflineStats(Player viewer, org.bukkit.OfflinePlayer target) {
		UUID targetId = target.getUniqueId();
		DuelStatistics.PlayerStats stats = new DuelStatistics.PlayerStats(targetId);

		String name = target.getName() != null ? target.getName() : "Unknown";

		ColorUtil.sendMessage(viewer, "&6&l⚔ Duel Stats: &e" + name + " &7(Offline) &6&l⚔");
		ColorUtil.sendMessage(viewer, "&7Wins: &a" + stats.wins);
		ColorUtil.sendMessage(viewer, "&7Losses: &c" + stats.losses);
		ColorUtil.sendMessage(viewer, "&7Win Rate: &e" + String.format("%.1f", stats.winRate) + "%");
		ColorUtil.sendMessage(viewer, "&7Current Streak: &6" + stats.streak);
		ColorUtil.sendMessage(viewer, "&7Best Streak: &6" + stats.bestStreak);
		ColorUtil.sendMessage(viewer, "&7Total Duels: &7" + stats.totalDuels);
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> completions = new ArrayList<>();

		if (args.length == 1) {
			String input = args[0].toLowerCase();

			// Subcommands
			if ("accept".startsWith(input))
				completions.add("accept");
			if ("deny".startsWith(input))
				completions.add("deny");
			if ("queue".startsWith(input))
				completions.add("queue");
			if ("leave".startsWith(input))
				completions.add("leave");
			if ("return".startsWith(input))
				completions.add("return");
			if ("stats".startsWith(input))
				completions.add("stats");

			// Player names
			for (Player player : Bukkit.getOnlinePlayers()) {
				if (player.getName().toLowerCase().startsWith(input) && !player.equals(sender)) {
					completions.add(player.getName());
				}
			}
		} else if (args.length == 2) {
			String sub = args[0].toLowerCase();
			String input = args[1].toLowerCase();

			if (sub.equals("accept") || sub.equals("deny") || sub.equals("stats")) {
				for (Player player : Bukkit.getOnlinePlayers()) {
					if (player.getName().toLowerCase().startsWith(input)) {
						completions.add(player.getName());
					}
				}
			}
		}

		return completions;
	}
}
