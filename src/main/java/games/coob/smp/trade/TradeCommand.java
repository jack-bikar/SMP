package games.coob.smp.trade;

import games.coob.smp.duel.DuelManager;
import games.coob.smp.menu.PlayerPickerMenu;
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
import java.util.Locale;

/**
 * /trade - pick an online player to trade with
 * /trade &lt;player&gt; - send a trade request
 * /trade accept|deny &lt;player&gt; - answer one (the buttons in chat run these)
 */
public final class TradeCommand implements CommandExecutor, TabCompleter {

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			ColorUtil.sendMessage(sender, "&cThis command can only be used by players.");
			return true;
		}
		if (!Settings.TradeSection.ENABLED) {
			ColorUtil.sendMessage(player, "&cTrading is disabled.");
			return true;
		}

		if (args.length == 0) {
			new PlayerPickerMenu(player, "&8Trade with...",
					target -> !DuelManager.getInstance().isInDuel(target),
					"Click to send a trade request",
					target -> {
						player.closeInventory();
						TradeManager.getInstance().sendRequest(player, target);
					},
					null).displayTo(player);
			return true;
		}

		switch (args[0].toLowerCase(Locale.ROOT)) {
			case "accept" -> {
				if (args.length < 2)
					ColorUtil.sendMessage(player, "&cUsage: /trade accept <player>");
				else
					TradeManager.getInstance().accept(player, args[1]);
			}
			case "deny", "decline" -> {
				if (args.length < 2)
					ColorUtil.sendMessage(player, "&cUsage: /trade deny <player>");
				else
					TradeManager.getInstance().deny(player, args[1]);
			}
			default -> {
				// Exact names only: a trade must never go to someone whose name merely starts the same
				Player target = Bukkit.getPlayerExact(args[0]);
				if (target == null)
					ColorUtil.sendMessage(player, "&cPlayer '&e" + args[0] + "&c' is not online.");
				else
					TradeManager.getInstance().sendRequest(player, target);
			}
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> completions = new ArrayList<>();
		String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
		if (args.length == 1) {
			for (String sub : new String[] { "accept", "deny" }) {
				if (sub.startsWith(prefix))
					completions.add(sub);
			}
		}
		if (args.length == 1 || (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny")))) {
			for (Player online : Bukkit.getOnlinePlayers()) {
				if (online != sender && online.getName().toLowerCase(Locale.ROOT).startsWith(prefix))
					completions.add(online.getName());
			}
		}
		return completions;
	}
}
