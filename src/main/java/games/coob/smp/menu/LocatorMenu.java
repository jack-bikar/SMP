package games.coob.smp.menu;

import games.coob.smp.PlayerCache;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.tracking.PortalCache;
import games.coob.smp.tracking.TrackingRegistry;
import games.coob.smp.util.ItemCreator;
import games.coob.smp.util.Messenger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Main menu for the player tracking system.
 */
public final class LocatorMenu extends SimpleMenu {

    private static final int PLAYER_BUTTON_SLOT = 11;
    private static final int DEATH_BUTTON_SLOT = 15;

    public LocatorMenu(Player player) {
        super(player, 9 * 3, "&0&lPlayer Tracker");
        setupItems();
    }

    private void setupItems() {
        inventory.setItem(PLAYER_BUTTON_SLOT, ItemCreator.of(
                Material.PLAYER_HEAD,
                "&r&b&lTrack Player",
                "",
                "&r&7Click to select a player",
                "&r&7to track their location.",
                "",
                "&r&eClick to open menu").make());

        inventory.setItem(DEATH_BUTTON_SLOT, ItemCreator.of(
                Material.SKELETON_SKULL,
                "&r&c&lTrack Death Location",
                "",
                "&r&7Click to track your",
                "&r&7previous death location.",
                "",
                "&r&eClick to track").make());
    }

    @Override
    protected void onMenuClick(Player player, int slot, ItemStack clicked) {
        if (slot == PLAYER_BUTTON_SLOT) {
            LocatorPlayersMenu.openMenu(player);
        } else if (slot == DEATH_BUTTON_SLOT) {
            handleDeathTracking(player);
        }
    }

    private void handleDeathTracking(Player player) {
        PlayerCache cache = PlayerCache.from(player);
        Location deathLocation = cache.getDeathLocation();

        if (deathLocation == null || deathLocation.getWorld() == null) {
            Messenger.info(player, "No death location was found.");
            return;
        }

        // Set up tracking
        cache.setTrackingLocation("Death");
        TrackingRegistry.startTracking(player.getUniqueId());

        // Enable locator bar
        LocatorBarManager.enableReceive(player);

        // Set target based on dimension
        if (deathLocation.getWorld().equals(player.getWorld())) {
            LocatorBarManager.setTarget(player, deathLocation);
            cache.setCachedPortalTarget(null);
        } else {
            // Different dimension - find portal
            Location portalLoc = findPortalToDimension(player, cache, deathLocation.getWorld().getEnvironment());
            cache.setCachedPortalTarget(portalLoc);
            if (portalLoc != null) {
                LocatorBarManager.setTarget(player, portalLoc);
            } else {
                LocatorBarManager.setTarget(player, player.getLocation());
            }
        }

        player.closeInventory();
        Messenger.success(player, "You are now tracking your death location.");
    }

    private Location findPortalToDimension(Player player, PlayerCache cache, World.Environment targetEnv) {
        World currentWorld = player.getWorld();
        Location playerLoc = player.getLocation();
        World.Environment currentEnv = currentWorld.getEnvironment();

        // Check player's stored portals first
        if (currentEnv == World.Environment.NORMAL) {
            if (targetEnv == World.Environment.NETHER) {
                Location portal = cache.getOverworldNetherPortalLocation();
                if (portal != null && portal.getWorld() != null && portal.getWorld().equals(currentWorld)) {
                    return portal;
                }
            } else if (targetEnv == World.Environment.THE_END) {
                Location portal = cache.getOverworldEndPortalLocation();
                if (portal != null && portal.getWorld() != null && portal.getWorld().equals(currentWorld)) {
                    return portal;
                }
            }
        } else if (targetEnv == World.Environment.NORMAL) {
            Location portal = cache.getPortalLocation();
            if (portal != null && portal.getWorld() != null && portal.getWorld().equals(currentWorld)) {
                return portal;
            }
        }

        // Fallback to PortalCache
        return PortalCache.findNearestToDimension(currentWorld, playerLoc, targetEnv);
    }

    public static void openMenu(Player player) {
        new LocatorMenu(player).displayTo(player);
    }
}
