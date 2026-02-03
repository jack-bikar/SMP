package games.coob.smp.duel;

import games.coob.smp.duel.model.ArenaRegistry;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles admin arena commands.
 * /arena create <name> - Create a new arena
 * /arena edit <name> - Enter edit mode for an arena
 * /arena setspawn1 - Set challenger spawn
 * /arena setspawn2 - Set opponent spawn
 * /arena setlobby - Set global lobby spawn
 * /arena save - Save and exit edit mode
 * /arena delete <name> - Delete an arena
 * /arena list - List all arenas
 */
public class ArenaCommand implements CommandExecutor, TabCompleter {

	private static final String PERMISSION = "smp.admin.arena";

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			ColorUtil.sendMessage(sender, "&cThis command can only be used by players.");
			return true;
		}

		if (!player.hasPermission(PERMISSION)) {
			ColorUtil.sendMessage(player, "&cYou don't have permission to use this command.");
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
			case "create" -> {
				if (args.length < 2) {
					ColorUtil.sendMessage(player, "&cUsage: /arena create <name>");
					return true;
				}
				String name = args[1];
				if (!isValidArenaName(name)) {
					ColorUtil.sendMessage(player, "&cInvalid arena name. Use only letters, numbers, and underscores.");
					return true;
				}
				ArenaManager.getInstance().createArena(player, name);
			}
			case "edit" -> {
				if (args.length < 2) {
					ColorUtil.sendMessage(player, "&cUsage: /arena edit <name>");
					return true;
				}
				ArenaManager.getInstance().editArena(player, args[1]);
			}
			case "setspawn1", "spawn1" -> {
				ArenaManager.getInstance().setSpawn1(player);
			}
			case "setspawn2", "spawn2" -> {
				ArenaManager.getInstance().setSpawn2(player);
			}
			case "setlobby", "lobby" -> {
				ArenaManager.getInstance().setLobbySpawn(player);
			}
			case "save" -> {
				ArenaManager.getInstance().saveArena(player);
			}
			case "cancel" -> {
				ArenaManager.getInstance().cancelEdit(player);
			}
			case "delete", "remove" -> {
				if (args.length < 2) {
					ColorUtil.sendMessage(player, "&cUsage: /arena delete <name>");
					return true;
				}
				ArenaManager.getInstance().deleteArena(player, args[1]);
			}
			case "list" -> {
				ArenaManager.getInstance().listArenas(player);
			}
			case "info" -> {
				if (args.length < 2) {
					// Show info about currently editing arena
					String editing = ArenaManager.getInstance().getEditingArena(player);
					if (editing != null) {
						showArenaInfo(player, editing);
					} else {
						ColorUtil.sendMessage(player, "&cUsage: /arena info <name>");
					}
				} else {
					showArenaInfo(player, args[1]);
				}
			}
			default -> {
				ColorUtil.sendMessage(player, "&cUnknown subcommand. Use /arena for help.");
			}
		}

		return true;
	}

	private void sendHelp(Player player) {
		ColorUtil.sendMessage(player, "&6&l⚔ Arena Commands &6&l⚔");
		ColorUtil.sendMessage(player, "&e/arena create <name> &7- Create a new arena");
		ColorUtil.sendMessage(player, "&e/arena edit <name> &7- Enter edit mode for an arena");
		ColorUtil.sendMessage(player, "&e/arena setspawn1 &7- Set challenger spawn point");
		ColorUtil.sendMessage(player, "&e/arena setspawn2 &7- Set opponent spawn point");
		ColorUtil.sendMessage(player, "&e/arena setlobby &7- Set global lobby spawn");
		ColorUtil.sendMessage(player, "&e/arena save &7- Save and exit edit mode");
		ColorUtil.sendMessage(player, "&e/arena cancel &7- Cancel edit mode without saving");
		ColorUtil.sendMessage(player, "&e/arena delete <name> &7- Delete an arena");
		ColorUtil.sendMessage(player, "&e/arena list &7- List all arenas");
		ColorUtil.sendMessage(player, "&e/arena info [name] &7- Show arena details");
	}

	private boolean isValidArenaName(String name) {
		return name.matches("[a-zA-Z0-9_]+");
	}

	private void showArenaInfo(Player player, String name) {
		games.coob.smp.duel.model.ArenaData arena = ArenaRegistry.getInstance().getArena(name);
		if (arena == null) {
			ColorUtil.sendMessage(player, "&cArena '&e" + name + "&c' not found.");
			return;
		}

		ColorUtil.sendMessage(player, "&6&l⚔ Arena: &e" + arena.getName() + " &6&l⚔");
		ColorUtil.sendMessage(player, "&7Status: " + (arena.isReady() ? "&a[Ready]" : "&c[Incomplete]"));

		if (arena.getSpawn1() != null) {
			ColorUtil.sendMessage(player, "&7Spawn 1: &e" + formatLocation(arena.getSpawn1()));
		} else {
			ColorUtil.sendMessage(player, "&7Spawn 1: &c[Not Set]");
		}

		if (arena.getSpawn2() != null) {
			ColorUtil.sendMessage(player, "&7Spawn 2: &e" + formatLocation(arena.getSpawn2()));
		} else {
			ColorUtil.sendMessage(player, "&7Spawn 2: &c[Not Set]");
		}

		if (arena.getCenter() != null) {
			ColorUtil.sendMessage(player, "&7Center: &e" + formatLocation(arena.getCenter()));
		}
	}

	private String formatLocation(org.bukkit.Location loc) {
		return String.format("%s: %.1f, %.1f, %.1f",
				loc.getWorld().getName(),
				loc.getX(),
				loc.getY(),
				loc.getZ());
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> completions = new ArrayList<>();

		if (!sender.hasPermission(PERMISSION)) {
			return completions;
		}

		if (args.length == 1) {
			String input = args[0].toLowerCase();
			String[] subcommands = { "create", "edit", "setspawn1", "setspawn2", "setlobby", "save", "cancel", "delete",
					"list", "info" };
			for (String sub : subcommands) {
				if (sub.startsWith(input)) {
					completions.add(sub);
				}
			}
		} else if (args.length == 2) {
			String sub = args[0].toLowerCase();
			String input = args[1].toLowerCase();

			if (sub.equals("edit") || sub.equals("delete") || sub.equals("info")) {
				Set<String> arenaNames = ArenaRegistry.getInstance().getArenaNames();
				for (String name : arenaNames) {
					if (name.toLowerCase().startsWith(input)) {
						completions.add(name);
					}
				}
			}
		}

		return completions;
	}
}
