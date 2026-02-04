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
		ColorUtil.sendMessage(sender, "&e/track &7- Open tracking menu (alias: /tr)");
		ColorUtil.sendMessage(sender, "&7  &7/track death &7- Track your death location");
		ColorUtil.sendMessage(sender, "&7  &7/track accept <player> &7- Accept tracking request");
		ColorUtil.sendMessage(sender, "&7  &7/track deny <player> &7- Deny tracking request");
		ColorUtil.sendMessage(sender, "&7  &7/track stop &7- Stop all tracking");
		ColorUtil.sendMessage(sender, "&7  &7/track stop <player> &7- Stop tracking specific player");
		ColorUtil.sendMessage(sender, "");
		ColorUtil.sendMessage(sender, "&e/tp &7- Teleport request menu and accept/deny");
		ColorUtil.sendMessage(sender, "&7  &7/tp &7- Open TP menu (select player to request)");
		ColorUtil.sendMessage(sender, "&7  &7/tp accept <player> &7- Accept TP request");
		ColorUtil.sendMessage(sender, "&7  &7/tp deny <player> &7- Deny TP request");
		ColorUtil.sendMessage(sender, "");
		ColorUtil.sendMessage(sender, "&e/duel &7- 1v1 duel system (alias: /d)");
		ColorUtil.sendMessage(sender, "&7  &7/duel <player> &7- Challenge a player to a duel");
		ColorUtil.sendMessage(sender, "&7  &7/duel accept <player> &7- Accept duel request");
		ColorUtil.sendMessage(sender, "&7  &7/duel deny <player> &7- Deny duel request");
		ColorUtil.sendMessage(sender, "&7  &7/duel queue &7- Join random matchmaking");
		ColorUtil.sendMessage(sender, "&7  &7/duel leave &7- Leave queue or loot phase");
		ColorUtil.sendMessage(sender, "&7  &7/duel stats [player] &7- View duel statistics");
		ColorUtil.sendMessage(sender, "");
		ColorUtil.sendMessage(sender, "&e/arena &7- Arena management (admin)");
		ColorUtil.sendMessage(sender, "&7  &7/arena create <name> &7- Create a new arena");
		ColorUtil.sendMessage(sender, "&7  &7/arena edit <name> &7- Enter edit mode for an arena");
		ColorUtil.sendMessage(sender, "&7  &7/arena setspawn1 &7- Set challenger spawn point");
		ColorUtil.sendMessage(sender, "&7  &7/arena setspawn2 &7- Set opponent spawn point");
		ColorUtil.sendMessage(sender, "&7  &7/arena setlobby &7- Set global lobby spawn");
		ColorUtil.sendMessage(sender, "&7  &7/arena save &7- Save and exit edit mode");
		ColorUtil.sendMessage(sender, "&7  &7/arena cancel &7- Cancel edit mode without saving");
		ColorUtil.sendMessage(sender, "&7  &7/arena delete <name> &7- Delete an arena");
		ColorUtil.sendMessage(sender, "&7  &7/arena list &7- List all arenas");
		ColorUtil.sendMessage(sender, "&7  &7/arena info [name] &7- Show arena details");
		ColorUtil.sendMessage(sender, "");
		ColorUtil.sendMessage(sender, "&e/inv <inv|enderchest|armour|clear> <player> &7- Edit player inventories (OP)");
		ColorUtil.sendMessage(sender, "");

		// Features
		ColorUtil.sendMessage(sender, "&6&lFeatures:");
		ColorUtil.sendMessage(sender, "");
		ColorUtil.sendMessage(sender, "&7- &eDeath Chest System &7- Items stored in chests on death");
		ColorUtil.sendMessage(sender, "&7- &eLocator Bar Tracking &7- Track players using the locator bar");
		ColorUtil.sendMessage(sender, "&7- &eProjectile Effects &7- Particle trails and knockback for projectiles");
		ColorUtil.sendMessage(sender, "&7- &eDeath Effects &7- Custom death particle effects");
		ColorUtil.sendMessage(sender, "&7- &eCombat System &7- Combat logging prevention (ghost body, lockout, debuff)");
		ColorUtil.sendMessage(sender, "&7- &eTP Request System &7- Teleport request menu and accept/deny in chat");
		ColorUtil.sendMessage(sender, "&7- &eDuel System &7- 1v1 duels, queue matchmaking, arenas, shrinking border");
		ColorUtil.sendMessage(sender, "");

		// Permissions (only restricted commands)
		ColorUtil.sendMessage(sender, "&6&lPermissions:");
		ColorUtil.sendMessage(sender, "");
		ColorUtil.sendMessage(sender, "&7- &esmp.command.spawn &7- Use /spawn command");
		ColorUtil.sendMessage(sender, "&7- &esmp.command.inv &7- Use /inv command");
		ColorUtil.sendMessage(sender, "&7- &esmp.duel &7- Use /duel command");
		ColorUtil.sendMessage(sender, "&7- &esmp.duel.queue &7- Use duel queue matchmaking");
		ColorUtil.sendMessage(sender, "&7- &esmp.admin.arena &7- Arena management (create, edit, delete)");
		ColorUtil.sendMessage(sender, "");

		ColorUtil.sendMessage(sender, "&6&l=== === ===");

		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		return new ArrayList<>();
	}
}
