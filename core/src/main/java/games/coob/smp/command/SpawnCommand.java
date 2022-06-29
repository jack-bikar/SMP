package games.coob.smp.command;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.MathUtil;
import org.mineacademy.fo.command.SimpleCommand;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.visual.BlockVisualizer;

import java.util.List;

public class SpawnCommand extends SimpleCommand {

	public SpawnCommand() {
		super("spawn");
	}

	@Override
	protected void onCommand() {
		checkConsole();

		final Player player = getPlayer();
		final World world = player.getWorld();

		if (args.length == 0) {
			player.teleport(world.getSpawnLocation());
			Common.tell(player, "&aTeleported to spawn.");
		} else {
			final String param = args[0];

			if (param.equals("locate")) {
				BlockVisualizer.visualize(world.getSpawnLocation().getBlock(), CompMaterial.DIAMOND_BLOCK, "&b&lSpawn location");
				Common.tell(player, "&aThe spawn is located at &e" + MathUtil.formatTwoDigits(world.getSpawnLocation().getX()) + "&a, &e" + MathUtil.formatTwoDigits(world.getSpawnLocation().getY()) + "&a, &e" + MathUtil.formatTwoDigits(world.getSpawnLocation().getZ()) + "&a.");
				Common.runLater(200, () -> BlockVisualizer.stopVisualizing(world.getSpawnLocation().getBlock()));
			} else if (param.equals("set")) {
				world.setSpawnLocation(player.getLocation());
				Common.tell(player, "&aSet the spawn location at &3" + MathUtil.formatTwoDigits(player.getLocation().getX()) + "&a, &3 " + MathUtil.formatTwoDigits(player.getLocation().getY()) + "&a, &3" + MathUtil.formatTwoDigits(player.getLocation().getZ()) + "&a.");
			}
		}
	}

	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return this.completeLastWord("locate", "set");

		return NO_COMPLETE;
	}
}
