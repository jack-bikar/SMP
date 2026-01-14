package games.coob.smp.command;

import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.MathUtil;
import games.coob.smp.util.Messenger;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpawnCommand implements CommandExecutor, TabCompleter {

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			ColorUtil.sendMessage(sender, "&cThis command can only be used by players.");
			return true;
		}

		// Check permission
		if (!player.hasPermission("smp.command.spawn") && !player.isOp()) {
			Messenger.error(player, "You don't have permission to use this command.");
			return true;
		}

		final World world = player.getWorld();

		if (args.length == 0) {
			player.teleport(world.getSpawnLocation());
			Messenger.success(player, "Teleported to spawn.");
		} else {
			final String param = args[0];

			if (param.equals("locate")) {
				Messenger.info(player,
						"The spawn is located at &e" + MathUtil.formatTwoDigits(world.getSpawnLocation().getX())
								+ "&a, &e" + MathUtil.formatTwoDigits(world.getSpawnLocation().getY()) + "&a, &e"
								+ MathUtil.formatTwoDigits(world.getSpawnLocation().getZ()) + "&a.");
			} else if (param.equals("set")) {
				world.setSpawnLocation(player.getLocation());
				Messenger.success(player,
						"Set the spawn location at &3" + MathUtil.formatTwoDigits(player.getLocation().getX())
								+ "&a, &3 " + MathUtil.formatTwoDigits(player.getLocation().getY()) + "&a, &3"
								+ MathUtil.formatTwoDigits(player.getLocation().getZ()) + "&a.");
			}
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return Arrays.asList("locate", "set");
		}
		return new ArrayList<>();
	}
}
