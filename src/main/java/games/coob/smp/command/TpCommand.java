package games.coob.smp.command;

import games.coob.smp.menu.TpPlayersMenu;
import games.coob.smp.settings.Settings;
import games.coob.smp.tp.TpRequestManager;
import games.coob.smp.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * TP command: /tp opens paginated menu; /tp accept &lt;player&gt; and /tp deny
 * &lt;player&gt; used by chat [ACCEPT]/[DENY]. No permission required.
 */
public class TpCommand implements CommandExecutor, TabCompleter {

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			ColorUtil.sendMessage(sender, "&cThis command can only be used by players.");
			return true;
		}

		if (!Settings.TpSection.ENABLE_TP) {
			if (sender.isOp() && args.length > 0) {
				Bukkit.dispatchCommand(sender, "minecraft:tp " + String.join(" ", args));
				return true;
			}
			ColorUtil.sendMessage(sender, "&cTP requests are disabled.");
			return true;
		}

		if (args.length == 0) {
			TpPlayersMenu.openMenu(player);
			return true;
		}

		String sub = args[0].toLowerCase();
		switch (sub) {
			case "accept" -> {
				if (args.length < 2) {
					ColorUtil.sendMessage(sender, "&cUsage: /tp accept <player>");
					return true;
				}
				TpRequestManager.getInstance().acceptRequest(player, args[1]);
			}
			case "deny" -> {
				if (args.length < 2) {
					ColorUtil.sendMessage(sender, "&cUsage: /tp deny <player>");
					return true;
				}
				TpRequestManager.getInstance().denyRequest(player, args[1]);
			}
			default -> ColorUtil.sendMessage(sender,
					"&cUnknown subcommand. Use /tp, /tp accept <player>, or /tp deny <player>");
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> out = new ArrayList<>();
		if (!Settings.TpSection.ENABLE_TP && sender.isOp()) {
			// Forward tab completion for vanilla /tp (player names)
			String prefix = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
			for (Player p : Bukkit.getOnlinePlayers()) {
				if (p.getName().toLowerCase().startsWith(prefix)) {
					out.add(p.getName());
				}
			}
			return out;
		}
		if (!Settings.TpSection.ENABLE_TP) {
			return out;
		}
		if (args.length == 1) {
			String a = args[0].toLowerCase();
			if ("accept".startsWith(a))
				out.add("accept");
			if ("deny".startsWith(a))
				out.add("deny");
			for (Player p : Bukkit.getOnlinePlayers()) {
				if (p != sender && p.getName().toLowerCase().startsWith(a)) {
					out.add(p.getName());
				}
			}
			return out;
		}
		if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny"))) {
			String prefix = args[1].toLowerCase();
			for (Player p : Bukkit.getOnlinePlayers()) {
				if (p.getName().toLowerCase().startsWith(prefix)) {
					out.add(p.getName());
				}
			}
		}
		return out;
	}
}
