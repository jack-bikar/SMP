package games.coob.smp.command;

import games.coob.smp.menu.LocatorMenu;
import games.coob.smp.settings.Settings;
import games.coob.smp.tracking.TrackingRequestManager;
import games.coob.smp.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrackingCommand implements CommandExecutor, TabCompleter {

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			ColorUtil.sendMessage(sender, "&cThis command can only be used by players.");
			return true;
		}

		// Disable tracking command if locator bar is enabled
		if (Settings.LocatorSection.ENABLE_LOCATOR_BAR) {
			ColorUtil.sendMessage(sender, "&cTracking commands are disabled when the locator bar is enabled.");
			return true;
		}

		if (args.length == 0) {
			ColorUtil.sendMessage(sender, "&cUsage: /tracking <accept|deny|cancel|menu|give> [player]");
			return true;
		}

		String subCommand = args[0].toLowerCase();

		switch (subCommand) {
			case "accept":
				if (args.length < 2) {
					ColorUtil.sendMessage(sender, "&cUsage: /tracking accept <player>");
					return true;
				}
				TrackingRequestManager.getInstance().acceptRequest(player, args[1]);
				break;

			case "deny":
				if (args.length < 2) {
					ColorUtil.sendMessage(sender, "&cUsage: /tracking deny <player>");
					return true;
				}
				TrackingRequestManager.getInstance().denyRequest(player, args[1]);
				break;

			case "cancel":
				TrackingRequestManager.getInstance().cancelTracking(player);
				break;

			case "menu":
				if (!player.hasPermission("smp.command.tracking.menu") && !player.isOp()) {
					ColorUtil.sendMessage(sender, "&cYou don't have permission to use this command.");
					return true;
				}
				LocatorMenu.openMenu(player);
				break;

			case "give":
				if (!player.hasPermission("smp.command.tracking.give") && !player.isOp()) {
					ColorUtil.sendMessage(sender, "&cYou don't have permission to use this command.");
					return true;
				}
				Player target = args.length > 1 ? Bukkit.getPlayer(args[1]) : player;
				if (target == null) {
					ColorUtil.sendMessage(sender, "&cPlayer not found.");
					return true;
				}
				// Note: Locator bar tracking is built-in, no item needed
				// This command is kept for backwards compatibility but just informs the player
				ColorUtil.sendMessage(target,
						"&aLocator bar tracking is now built-in! Use /tracking menu to track players.");
				if (!target.equals(player)) {
					ColorUtil.sendMessage(sender, "&aLocator bar tracking is built-in for all players.");
				}
				break;

			default:
				ColorUtil.sendMessage(sender, "&cUnknown subcommand. Use: accept, deny, cancel, menu, or give");
				break;
		}

		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return Arrays.asList("accept", "deny", "cancel", "menu", "give");
		}
		if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny")
				|| args[0].equalsIgnoreCase("give"))) {
			List<String> players = new ArrayList<>();
			for (Player p : Bukkit.getOnlinePlayers()) {
				players.add(p.getName());
			}
			return players;
		}
		return new ArrayList<>();
	}
}
