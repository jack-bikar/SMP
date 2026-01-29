package games.coob.smp.command;

import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.Messenger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main SMP plugin command - displays help information
 */
public class SMPCommand implements CommandExecutor, TabCompleter {

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		// Send header
		ColorUtil.sendMessage(sender, "&6&l=== SMP Plugin Help ===");
		ColorUtil.sendMessage(sender, "");

		// Plugin info
		ColorUtil.sendMessage(sender, "&ePlugin Version: &f1.0.0");
		ColorUtil.sendMessage(sender, "&eAuthor: &fJackOUT");
		ColorUtil.sendMessage(sender, "");

		// Commands
		ColorUtil.sendMessage(sender, "&6&lCommands:");
		ColorUtil.sendMessage(sender, "");
		ColorUtil.sendMessage(sender, "&e/smp &7- Show this help (all players)");
		ColorUtil.sendMessage(sender, "");
		ColorUtil.sendMessage(sender, "&e/spawn &7- Teleport to spawn");
		ColorUtil.sendMessage(sender, "&7  &7/spawn locate &7- Show spawn coordinates");
		ColorUtil.sendMessage(sender, "&7  &7/spawn set &7- Set spawn to your location");
		ColorUtil.sendMessage(sender, "");
		ColorUtil.sendMessage(sender, "&e/tracking &7- Locator bar tracking (all players)");
		ColorUtil.sendMessage(sender, "&7  &7/tracking menu &7- Open locator tracking menu");
		ColorUtil.sendMessage(sender, "&7  &7/tracking cancel &7- Cancel current tracking");
		ColorUtil.sendMessage(sender, "&7  &7/tracking give [player] &7- Give locator tracker (built-in, OP)");
		ColorUtil.sendMessage(sender, "");
		ColorUtil.sendMessage(sender, "&e/inv <inv|enderchest|armour|clear> <player> &7- Edit player inventories (OP)");
		ColorUtil.sendMessage(sender, "");

		// Features
		ColorUtil.sendMessage(sender, "&6&lFeatures:");
		ColorUtil.sendMessage(sender, "");
		ColorUtil.sendMessage(sender, "&7- &eDeath Chest System &7- Items stored in chests on death");
		ColorUtil.sendMessage(sender, "&7- &eLocator Bar Tracking &7- Track players using the locator bar");
		ColorUtil.sendMessage(sender, "&7- &eProjectile Effects &7- Particle trails and knockback for projectiles");
		ColorUtil.sendMessage(sender, "&7- &eCombat System &7- Combat logging prevention");
		ColorUtil.sendMessage(sender, "");

		// Permissions (only restricted commands)
		ColorUtil.sendMessage(sender, "&6&lPermissions:");
		ColorUtil.sendMessage(sender, "");
		ColorUtil.sendMessage(sender, "&7- &esmp.command.spawn &7- Use /spawn command");
		ColorUtil.sendMessage(sender, "&7- &esmp.command.inv &7- Use /inv command");
		ColorUtil.sendMessage(sender, "&7- &esmp.command.tracking.menu &7- Open /tracking menu");
		ColorUtil.sendMessage(sender, "&7- &esmp.command.tracking.give &7- Use /tracking give");
		ColorUtil.sendMessage(sender, "");

		ColorUtil.sendMessage(sender, "&6&l=== === ===");

		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		return new ArrayList<>();
	}
}
