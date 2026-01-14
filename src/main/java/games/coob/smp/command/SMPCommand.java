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
		// Check permission (plugin.yml handles this, but double-check)
		if (!sender.hasPermission("smp.command.help") && !sender.isOp()) {
			ColorUtil.sendMessage(sender, "&cYou don't have permission to use this command.");
			return true;
		}

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
		
		ColorUtil.sendMessage(sender, "&e/spawn &7- Teleport to spawn");
		ColorUtil.sendMessage(sender, "&7  &7/spawn locate &7- Show spawn coordinates");
		ColorUtil.sendMessage(sender, "&7  &7/spawn set &7- Set spawn to your location");
		ColorUtil.sendMessage(sender, "");
		
		ColorUtil.sendMessage(sender, "&e/inv <inv|enderchest|armour|clear> <player> &7- Edit player inventories");
		ColorUtil.sendMessage(sender, "&7  &7/inv inv <player> &7- View/edit player inventory");
		ColorUtil.sendMessage(sender, "&7  &7/inv enderchest <player> &7- View/edit player ender chest");
		ColorUtil.sendMessage(sender, "&7  &7/inv armour <player> &7- View/edit player armor");
		ColorUtil.sendMessage(sender, "&7  &7/inv clear <player> &7- Clear player inventory");
		ColorUtil.sendMessage(sender, "");
		
		// Features
		ColorUtil.sendMessage(sender, "&6&lFeatures:");
		ColorUtil.sendMessage(sender, "");
		ColorUtil.sendMessage(sender, "&7- &eDeath Chest System &7- Items stored in chests on death");
		ColorUtil.sendMessage(sender, "&7- &eCompass Tracking &7- Right-click compass to track players/locations");
		ColorUtil.sendMessage(sender, "&7- &eProjectile Effects &7- Particle trails and knockback for projectiles");
		ColorUtil.sendMessage(sender, "&7- &eCombat System &7- Combat logging prevention");
		ColorUtil.sendMessage(sender, "");
		
		ColorUtil.sendMessage(sender, "&6&lPermissions:");
		ColorUtil.sendMessage(sender, "");
		ColorUtil.sendMessage(sender, "&7- &esmp.command.spawn &7- Use /spawn command");
		ColorUtil.sendMessage(sender, "&7- &esmp.command.inv &7- Use /inv command");
		ColorUtil.sendMessage(sender, "&7- &esmp.command.help &7- Use /smp help command");
		ColorUtil.sendMessage(sender, "");
		
		ColorUtil.sendMessage(sender, "&6&l=== === ===");
		
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		return new ArrayList<>();
	}
}
