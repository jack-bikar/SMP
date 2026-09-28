package games.coob.smp.command;

import games.coob.smp.PlayerCache;
import games.coob.smp.menu.LocatorMenu;
import games.coob.smp.settings.Settings;
import games.coob.smp.task.LocatorTask;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.tracking.TrackingRegistry;
import games.coob.smp.tracking.TrackingRequestManager;
import games.coob.smp.tracking.VanillaLocator;
import games.coob.smp.tracking.WaypointPacketSender;
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
 *   /track accept <player> - Accept a tracking request (used by chat button)
 *   /track deny <player> - Deny a tracking request (used by chat button)
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

        String subCommand = args.length == 0 ? "" : args[0].toLowerCase();

        // Vanilla locator bar: everyone is already on the bar, only the death spot can be added
        if (Settings.LocatorSection.ENABLE_LOCATOR_BAR) {
            switch (subCommand) {
                case "death" -> handleVanillaDeathTracking(player);
                case "stop" -> handleVanillaStop(player);
                default -> ColorUtil.sendMessage(sender, "&eEveryone near you is already on your locator bar. "
                        + "Use &6/track death &eto add your death location, &6/track stop &eto remove it.");
            }
            return true;
        }

        // No args = open menu
        if (args.length == 0) {
            LocatorMenu.openMenu(player);
            return true;
        }

        switch (subCommand) {
            case "death" -> handleDeathTracking(player);
            case "accept" -> {
                if (args.length < 2) {
                    ColorUtil.sendMessage(sender, "&cUsage: /track accept <player>");
                    return true;
                }
                TrackingRequestManager.getInstance().acceptRequest(player, args[1]);
            }
            case "deny" -> {
                if (args.length < 2) {
                    ColorUtil.sendMessage(sender, "&cUsage: /track deny <player>");
                    return true;
                }
                TrackingRequestManager.getInstance().denyRequest(player, args[1]);
            }
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

    private void handleVanillaDeathTracking(Player player) {
        PlayerCache cache = PlayerCache.from(player);
        Location deathLocation = cache.getDeathLocation();

        if (deathLocation == null || deathLocation.getWorld() == null) {
            Messenger.info(player, "No death location was found.");
            return;
        }
        if (cache.isTrackingDeath()) {
            Messenger.info(player, "Your death location is already on your locator bar.");
            return;
        }
        if (!WaypointPacketSender.isAvailable()) {
            Messenger.info(player, "You died at " + deathLocation.getBlockX() + ", " + deathLocation.getBlockY() + ", "
                    + deathLocation.getBlockZ() + " in " + deathLocation.getWorld().getName() + ".");
            return;
        }

        cache.startTrackingDeath();
        String where = deathLocation.getBlockX() + ", " + deathLocation.getBlockY() + ", " + deathLocation.getBlockZ();
        if (deathLocation.getWorld().equals(player.getWorld())) {
            Messenger.success(player, "Your death location (" + where + ") is now on your locator bar.");
        } else {
            Messenger.success(player, "Your death location (" + where + ") will show on your locator bar once you are in "
                    + deathLocation.getWorld().getName() + ".");
        }
    }

    private void handleVanillaStop(Player player) {
        PlayerCache cache = PlayerCache.from(player);
        if (!cache.isTrackingDeath()) {
            Messenger.info(player, "You are not tracking anything.");
            return;
        }
        cache.stopTrackingDeath();
        VanillaLocator.getInstance().refreshDeathWaypoint(player);
        Messenger.success(player, "Removed your death location from the locator bar.");
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
        WaypointPacketSender.removeWaypoint(player,
                WaypointPacketSender.generateWaypointId(player.getUniqueId(), target.getUniqueId()));

        if (!cache.isTracking()) {
            TrackingRegistry.stopTracking(player.getUniqueId());
            LocatorBarManager.disableReceive(player);
            LocatorBarManager.clearTarget(player);
            LocatorTask.cleanupPlayer(player.getUniqueId());
            WaypointPacketSender.clearWaypoint(player);
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
        LocatorTask.cleanupPlayer(player.getUniqueId());
        WaypointPacketSender.clearWaypoint(player);

        Messenger.success(player, "Stopped all tracking.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (Settings.LocatorSection.ENABLE_LOCATOR_BAR)
                return Arrays.asList("death", "stop");
            return Arrays.asList("death", "accept", "deny", "stop");
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny") || args[0].equalsIgnoreCase("stop")) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    players.add(p.getName());
                }
                return players;
            }
        }
        return new ArrayList<>();
    }
}
