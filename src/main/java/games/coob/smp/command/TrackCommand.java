package games.coob.smp.command;

import games.coob.smp.PlayerCache;
import games.coob.smp.menu.LocatorMenu;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.tracking.TrackingRegistry;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main tracking command: /track (alias: /tr)
 *
 * Usage:
 *   /track - Opens the tracking menu
 *   /track death - Tracks your death location
 *   /track stop - Stop all tracking
 *   /track stop <player> - Stop tracking a specific player
 */
public class TrackCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            ColorUtil.sendMessage(sender, "&cThis command can only be used by players.");
            return true;
        }

        // No args = open menu
        if (args.length == 0) {
            LocatorMenu.openMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "death" -> handleDeathTracking(player);
            case "stop" -> {
                if (args.length >= 2) {
                    // Stop tracking specific player
                    handleStopTrackingPlayer(player, args[1]);
                } else {
                    // Stop all tracking
                    handleStopAllTracking(player);
                }
            }
            default -> ColorUtil.sendMessage(sender, "&cUnknown subcommand. Use: /track, /track death, /track stop");
        }

        return true;
    }

    private void handleDeathTracking(Player player) {
        PlayerCache cache = PlayerCache.from(player);
        Location deathLocation = cache.getDeathLocation();

        if (deathLocation == null || deathLocation.getWorld() == null) {
            Messenger.info(player, "No death location was found.");
            return;
        }

        if (cache.isTrackingDeath()) {
            Messenger.info(player, "You are already tracking your death location.");
            return;
        }

        // Start tracking death
        cache.startTrackingDeath();
        TrackingRegistry.startTracking(player.getUniqueId());
        LocatorBarManager.enableReceive(player);

        if (deathLocation.getWorld().equals(player.getWorld())) {
            LocatorBarManager.setTarget(player, deathLocation);
        }

        Messenger.success(player, "You are now tracking your death location.");
    }

    private void handleStopTrackingPlayer(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            ColorUtil.sendMessage(player, "&cPlayer not found.");
            return;
        }

        PlayerCache cache = PlayerCache.from(player);
        if (cache.getTrackedTarget(target.getUniqueId()) == null) {
            ColorUtil.sendMessage(player, "&cYou are not tracking that player.");
            return;
        }

        cache.removeTrackedPlayer(target.getUniqueId());
        
        // If not tracking anything anymore, stop completely
        if (!cache.isTracking()) {
            TrackingRegistry.stopTracking(player.getUniqueId());
            LocatorBarManager.disableReceive(player);
            LocatorBarManager.clearTarget(player);
        }

        ColorUtil.sendMessage(player, "&aStopped tracking &3" + target.getName() + "&a.");
    }

    private void handleStopAllTracking(Player player) {
        PlayerCache cache = PlayerCache.from(player);
        
        if (!cache.isTracking()) {
            Messenger.info(player, "You are not tracking anything.");
            return;
        }

        cache.clearAllTracking();
        TrackingRegistry.stopTracking(player.getUniqueId());
        LocatorBarManager.disableReceive(player);
        LocatorBarManager.clearTarget(player);

        Messenger.success(player, "Stopped all tracking.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("death", "stop");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stop")) {
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                players.add(p.getName());
            }
            return players;
        }
        return new ArrayList<>();
    }
}
