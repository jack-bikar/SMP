package games.coob.smp.menu;

import games.coob.smp.PlayerCache;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.tracking.TrackingRegistry;
import games.coob.smp.util.ItemCreator;
import games.coob.smp.util.Messenger;
import org.bukkit.Location;
import org.bukkit.Material;
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

        // Set up tracking state
        cache.setTrackingLocation("Death");
        cache.setCachedPortalTarget(null); // Will be calculated by LocatorTask if needed
        TrackingRegistry.startTracking(player.getUniqueId());

        // Enable locator bar
        LocatorBarManager.enableReceive(player);

        // Set initial target if same dimension
        if (deathLocation.getWorld().equals(player.getWorld())) {
            LocatorBarManager.setTarget(player, deathLocation);
        }
        // If different dimension, LocatorTask will calculate portal on next tick

        player.closeInventory();
        Messenger.success(player, "You are now tracking your death location.");
    }

    public static void openMenu(Player player) {
        new LocatorMenu(player).displayTo(player);
    }
}
