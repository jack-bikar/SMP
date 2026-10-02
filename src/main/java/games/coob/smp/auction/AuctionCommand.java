package games.coob.smp.auction;

import games.coob.smp.menu.AuctionListingMenu;
import games.coob.smp.menu.AuctionMenu;
import games.coob.smp.menu.CollectionBoxMenu;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /auction (alias /ah) - browse the auction house
 * /auction sell [what you want] - put items up
 * /auction mine - your listings
 * /auction collect - your collection box
 * /auction view &lt;id&gt; - one listing (the buttons in chat run this)
 */
public final class AuctionCommand implements CommandExecutor, TabCompleter {

	private static final List<String> SUBCOMMANDS = List.of("sell", "mine", "collect", "help");

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			ColorUtil.sendMessage(sender, "&cThis command can only be used by players.");
			return true;
		}
		String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";

		// Works even while the auction house is closed, so nothing can get stuck in a box
		if (sub.equals("collect") || sub.equals("box")) {
			new CollectionBoxMenu(player, null).displayTo(player);
			return true;
		}
		if (!Settings.AuctionSection.ENABLED) {
			ColorUtil.sendMessage(player, "&cThe auction house is closed. &7Anything waiting for you: &f/" + label + " collect");
			return true;
		}

		switch (sub) {
			case "" -> new AuctionMenu(player, AuctionMenu.Filter.ALL).displayTo(player);
			case "mine", "my" -> new AuctionMenu(player, AuctionMenu.Filter.MINE).displayTo(player);
			case "sell", "list" -> AuctionMenu.openSellPicker(player,
					AuctionHouse.cleanWants(String.join(" ", Arrays.copyOfRange(args, 1, args.length))), null);
			case "view" -> {
				AuctionListing listing = args.length > 1 ? AuctionHouse.getInstance().get(args[1]) : null;
				if (listing == null)
					ColorUtil.sendMessage(player, "&cThat listing has ended.");
				else
					new AuctionListingMenu(player, listing,
							() -> new AuctionMenu(player, AuctionMenu.Filter.ALL).displayTo(player)).displayTo(player);
			}
			default -> {
				ColorUtil.sendMessage(player, "&6&lAuction house &7- no money, only items.");
				ColorUtil.sendMessage(player, "&e/" + label + " &7- Browse what's up for offers");
				ColorUtil.sendMessage(player, "&e/" + label + " sell [what you want] &7- Put items up");
				ColorUtil.sendMessage(player, "&e/" + label + " mine &7- Your listings and the offers on them");
				ColorUtil.sendMessage(player, "&e/" + label + " collect &7- Items waiting for you");
			}
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> completions = new ArrayList<>();
		if (args.length == 1) {
			for (String sub : SUBCOMMANDS) {
				if (sub.startsWith(args[0].toLowerCase(Locale.ROOT)))
					completions.add(sub);
			}
		}
		return completions;
	}
}
