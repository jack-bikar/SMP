package games.coob.smp.command;

import games.coob.smp.SMPPlugin;
import games.coob.smp.model.DeathMessages;
import games.coob.smp.nickname.NicknameManager;
import games.coob.smp.settings.Settings;
import games.coob.smp.tracking.VanillaLocator;
import games.coob.smp.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

/**
 * Main SMP plugin command - displays help information, /smp reload reloads settings
 */
public class SMPCommand implements CommandExecutor, TabCompleter {

	private static final String RELOAD_PERMISSION = "smp.admin.reload";

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
			if (!sender.hasPermission(RELOAD_PERMISSION)) {
				ColorUtil.sendMessage(sender, "&cYou don't have permission to do that.");
				return true;
			}
			boolean locatorBar = Settings.LocatorSection.ENABLE_LOCATOR_BAR;
			Settings.loadSettings();
			DeathMessages.getInstance().reload();
			if (Settings.LocatorSection.ENABLE_LOCATOR_BAR != locatorBar) {
				// Switching between the vanilla bar and custom tracking needs a clean start for every player
				Settings.LocatorSection.ENABLE_LOCATOR_BAR = locatorBar;
				Settings.LocatorSection.ENABLE_TRACKING = !locatorBar;
				ColorUtil.sendMessage(sender, "&eThe locator bar mode (Enable_Locator_Bar) changes after a restart.");
			}
			VanillaLocator.getInstance().reapplyToOnlinePlayers();
			NicknameManager.getInstance().applyToOnlinePlayers();
			ColorUtil.sendMessage(sender, "&aSMP settings reloaded.");
			return true;
		}

		ColorUtil.sendMessage(sender, "&6&l=== SMP Plugin Help ===");
		ColorUtil.sendMessage(sender, "&eVersion: &f" + SMPPlugin.getInstance().getPluginMeta().getVersion()
				+ " &7| &eAuthor: &fJackOUT");
		ColorUtil.sendMessage(sender, "");

		ColorUtil.sendMessage(sender, "&6&lCommands:");
		ColorUtil.sendMessage(sender, "&e/smp &7- Show this help");
		ColorUtil.sendMessage(sender, "&e/smp reload &7- Reload settings.yml (admin)");
		ColorUtil.sendMessage(sender, "");
		if (sender.hasPermission("smp.command.spawn")) {
			ColorUtil.sendMessage(sender, "&e/spawn &7- Teleport to spawn");
			ColorUtil.sendMessage(sender, "&7  /spawn locate &7- Show spawn coordinates");
			if (sender.hasPermission("smp.command.spawn.set"))
				ColorUtil.sendMessage(sender, "&7  /spawn set &7- Set spawn to your location");
			ColorUtil.sendMessage(sender, "");
		}
		if (Settings.LocatorSection.ENABLE_LOCATOR_BAR) {
			ColorUtil.sendMessage(sender, "&e/track death &7- Put your death location on the locator bar");
			ColorUtil.sendMessage(sender, "&7  /track stop &7- Remove it again");
			ColorUtil.sendMessage(sender, "");
		} else {
			ColorUtil.sendMessage(sender, "&e/track &7- Open tracking menu (alias: /tr)");
			ColorUtil.sendMessage(sender, "&7  /track death &7- Track your death location");
			ColorUtil.sendMessage(sender, "&7  /track accept|deny <player> &7- Answer a tracking request");
			ColorUtil.sendMessage(sender, "&7  /track stop [player] &7- Stop tracking everyone (or one player)");
			ColorUtil.sendMessage(sender, "");
		}
		if (Settings.TpSection.ENABLE_TP) {
			ColorUtil.sendMessage(sender, "&e/tp &7- Open TP menu (select a player to request)");
			ColorUtil.sendMessage(sender, "&7  /tp <player> &7- Send a TP request");
			ColorUtil.sendMessage(sender, "&7  /tp accept|deny <player> &7- Answer a TP request");
			ColorUtil.sendMessage(sender, "");
		}
		if (Settings.DuelSection.ENABLE_DUELS) {
			ColorUtil.sendMessage(sender, "&e/duel &7- Duel menu: 1v1, team duels, queue, stats (alias: /d)");
			ColorUtil.sendMessage(sender, "&7  /duel <player> &7- Challenge a player");
			if (Settings.DuelSection.TEAMS_ENABLED)
				ColorUtil.sendMessage(sender, "&7  /duel team &7- Set up a 2v2, 3v3... and invite players");
			ColorUtil.sendMessage(sender, "&7  /duel accept|deny <player> &7- Answer a duel request");
			ColorUtil.sendMessage(sender, "&7  /duel queue &7- Join random matchmaking");
			ColorUtil.sendMessage(sender, "&7  /duel leave &7- Leave the queue or your team lobby");
			ColorUtil.sendMessage(sender, "&7  /duel return &7- Go back right away after a duel");
			ColorUtil.sendMessage(sender, "&7  /duel stats [player] &7- View duel statistics");
			ColorUtil.sendMessage(sender, "");
			ColorUtil.sendMessage(sender, "&e/arena &7- Arena management (admin)");
			ColorUtil.sendMessage(sender, "&7  /arena create|edit|delete|info <name>");
			ColorUtil.sendMessage(sender, "&7  /arena setspawn1|setspawn2|save|cancel|list");
			ColorUtil.sendMessage(sender, "");
		}
		if (Settings.NicknameSection.ENABLED) {
			ColorUtil.sendMessage(sender, "&e/nick &7- Colour your name with the nickname menu");
			ColorUtil.sendMessage(sender, "&7  /nick <name> &7- Set a nickname");
			ColorUtil.sendMessage(sender, "&7  /nick gradient <colour> <colour> &7- Your own gradient");
			ColorUtil.sendMessage(sender, "&7  /nick reset &7- Back to your normal name");
			ColorUtil.sendMessage(sender, "");
		}
		ColorUtil.sendMessage(sender, "&e/inv <inv|enderchest|armour|clear> <player> &7- Edit inventories (admin)");
		ColorUtil.sendMessage(sender, "");

		ColorUtil.sendMessage(sender, "&6&lFeatures:");
		ColorUtil.sendMessage(sender, "&7- &eDeath Chests &7- Your items are stored in a chest where you die");
		ColorUtil.sendMessage(sender, "&7- &eCombat Logging &7- Leaving mid-fight is punished");
		if (Settings.LocatorSection.ENABLE_LOCATOR_BAR)
			ColorUtil.sendMessage(sender, "&7- &eLocator Bar &7- Face a waypoint to see who it is");
		else
			ColorUtil.sendMessage(sender, "&7- &eLocator Bar Tracking &7- Track players and your death spot");
		if (Settings.DuelSection.ENABLE_DUELS)
			ColorUtil.sendMessage(sender, "&7- &eDuels &7- 1v1 and team fights in the wild or in arenas");
		ColorUtil.sendMessage(sender, "&6&l=== === ===");
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> completions = new ArrayList<>();
		if (args.length == 1 && sender.hasPermission(RELOAD_PERMISSION) && "reload".startsWith(args[0].toLowerCase()))
			completions.add("reload");
		return completions;
	}
}
