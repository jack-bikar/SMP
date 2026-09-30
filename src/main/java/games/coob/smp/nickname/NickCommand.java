package games.coob.smp.nickname;

import games.coob.smp.menu.NickMenu;
import games.coob.smp.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
 * /nick                              open the nickname menu
 * /nick &lt;name&gt;                        set your nickname text
 * /nick color &lt;colour|#hex&gt;           one colour
 * /nick gradient &lt;colour&gt; &lt;colour&gt;... a gradient (or a preset name, e.g. /nick gradient ocean)
 * /nick rainbow | bold | italic | underline
 * /nick reset [player]
 */
public final class NickCommand implements CommandExecutor, TabCompleter {

	private static final String OTHERS_PERMISSION = "smp.nick.others";
	private static final List<String> SUBCOMMANDS = List.of("color", "gradient", "rainbow", "bold", "italic",
			"underline", "reset");

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!NicknameManager.isEnabled()) {
			ColorUtil.sendMessage(sender, "&cNicknames are disabled on this server.");
			return true;
		}

		// Admins can reset someone else's nickname, also from the console
		if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
			if (!sender.hasPermission(OTHERS_PERMISSION)) {
				ColorUtil.sendMessage(sender, "&cYou can only reset your own nickname (/nick reset).");
				return true;
			}
			OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
			if (target == null) {
				ColorUtil.sendMessage(sender, "&cPlayer not found.");
				return true;
			}
			NicknameManager.getInstance().reset(target.getUniqueId());
			ColorUtil.sendMessage(sender, "&aReset " + target.getName() + "'s nickname.");
			return true;
		}

		if (!(sender instanceof Player player)) {
			ColorUtil.sendMessage(sender, "&cThis command can only be used by players.");
			return true;
		}

		NicknameManager manager = NicknameManager.getInstance();
		if (args.length == 0) {
			new NickMenu(player).displayTo(player);
			return true;
		}
		// Only now: get() creates an entry, which a plain /nick shouldn't
		Nickname nickname = manager.get(player);

		switch (args[0].toLowerCase(Locale.ROOT)) {
			case "color", "colour" -> {
				TextColor color = args.length > 1 ? NickStyles.parseColor(args[1]) : null;
				if (color == null) {
					ColorUtil.sendMessage(player, "&cUsage: /nick color <red, gold, ... or #ff8800>");
					return true;
				}
				nickname.setRainbow(false);
				nickname.setColors(new ArrayList<>(List.of(color)));
			}
			case "gradient" -> {
				if (args.length == 2 && NickStyles.findGradient(args[1]) != null) {
					NickStyles.Gradient preset = NickStyles.findGradient(args[1]);
					nickname.setRainbow(preset.isRainbow());
					nickname.setColors(new ArrayList<>(preset.colors()));
				} else {
					List<TextColor> colors = new ArrayList<>();
					for (String value : Arrays.copyOfRange(args, 1, args.length)) {
						TextColor color = NickStyles.parseColor(value);
						if (color == null) {
							ColorUtil.sendMessage(player, "&c'" + value + "' isn't a colour. Use a name (red, aqua...) or #hex.");
							return true;
						}
						colors.add(color);
					}
					if (colors.size() < 2 || colors.size() > 5) {
						ColorUtil.sendMessage(player, "&cUsage: /nick gradient <colour> <colour> [up to 5], or a preset like /nick gradient ocean");
						return true;
					}
					nickname.setRainbow(false);
					nickname.setColors(colors);
				}
			}
			case "rainbow" -> {
				nickname.setRainbow(true);
				nickname.getColors().clear();
			}
			case "bold" -> nickname.setBold(!nickname.isBold());
			case "italic" -> nickname.setItalic(!nickname.isItalic());
			case "underline", "underlined" -> nickname.setUnderlined(!nickname.isUnderlined());
			case "reset", "off" -> {
				manager.reset(player.getUniqueId());
				ColorUtil.sendMessage(player, "&aYour nickname was reset.");
				return true;
			}
			default -> {
				String problem = manager.setText(player, String.join(" ", args));
				if (problem != null) {
					ColorUtil.sendMessage(player, "&c" + problem);
					return true;
				}
				player.sendMessage(Component.text("Your nickname is now ", NamedTextColor.GREEN).append(manager.preview(player))
						.append(Component.text(". Use /nick to colour it.", NamedTextColor.GRAY)));
				return true;
			}
		}

		manager.update(player);
		player.sendMessage(Component.text("Your name now looks like ", NamedTextColor.GREEN).append(manager.preview(player)));
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> completions = new ArrayList<>();
		String input = args[args.length - 1].toLowerCase(Locale.ROOT);

		if (args.length == 1) {
			for (String sub : SUBCOMMANDS) {
				if (sub.startsWith(input))
					completions.add(sub);
			}
		} else if (args[0].equalsIgnoreCase("color") || args[0].equalsIgnoreCase("colour")
				|| (args[0].equalsIgnoreCase("gradient") && args.length > 2)) {
			for (NickStyles.SolidColor color : NickStyles.COLORS) {
				String name = color.name().toLowerCase(Locale.ROOT).replace(' ', '_');
				if (name.startsWith(input))
					completions.add(name);
			}
		} else if (args[0].equalsIgnoreCase("gradient") && args.length == 2) {
			for (NickStyles.Gradient gradient : NickStyles.GRADIENTS) {
				if (gradient.name().toLowerCase(Locale.ROOT).startsWith(input))
					completions.add(gradient.name().toLowerCase(Locale.ROOT));
			}
			for (NickStyles.SolidColor color : NickStyles.COLORS) {
				String name = color.name().toLowerCase(Locale.ROOT).replace(' ', '_');
				if (name.startsWith(input))
					completions.add(name);
			}
		} else if (args[0].equalsIgnoreCase("reset") && args.length == 2 && sender.hasPermission(OTHERS_PERMISSION)) {
			for (Player online : Bukkit.getOnlinePlayers()) {
				if (online.getName().toLowerCase(Locale.ROOT).startsWith(input))
					completions.add(online.getName());
			}
		}
		return completions;
	}
}
