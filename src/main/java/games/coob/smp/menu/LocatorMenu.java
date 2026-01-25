package games.coob.smp.menu;

import games.coob.smp.PlayerCache;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.util.ItemCreator;
import games.coob.smp.util.Messenger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class LocatorMenu extends SimpleMenu {

    private static final int PLAYER_BUTTON_SLOT = 11;
    private static final int DEATH_BUTTON_SLOT = 15;

    public LocatorMenu(final Player player) {
        super(player, 9 * 3, "&0&lPlayer Tracker");
        setupItems();
    }

    private void setupItems() {
        // Player selection button (center-left)
        inventory.setItem(PLAYER_BUTTON_SLOT, ItemCreator.of(
                Material.PLAYER_HEAD,
                "&r&b&lTrack Player",
                "",
                "&r&7Click to select a player",
                "&r&7to track their location.",
                "",
                "&r&eClick to open menu").make());

        // Death location button (center-right)
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
    protected void onMenuClick(final Player player, final int slot, final ItemStack clicked) {
        if (slot == PLAYER_BUTTON_SLOT) {
            LocatorPlayersMenu.openMenu(player);
        } else if (slot == DEATH_BUTTON_SLOT) {
            handleDeathLocation(player);
        }
    }

    private void handleDeathLocation(final Player player) {
        final PlayerCache cache = PlayerCache.from(player);

        if (cache.getDeathLocation() != null) {
            // Use Player Locator Bar for death location tracking
            LocatorBarManager locatorBar = new LocatorBarManager(player);
            locatorBar.enableTemporarily();
            
            // Set compass target to point to death location or portal
            org.bukkit.Location deathLoc = cache.getDeathLocation();
            if (deathLoc.getWorld() == player.getWorld()) {
                // Same dimension - point to death location
                player.setCompassTarget(deathLoc);
            } else {
                // Different dimension - point to portal if available
                org.bukkit.Location portalLoc = cache.getPortalLocation();
                if (portalLoc != null && portalLoc.getWorld() == player.getWorld()) {
                    player.setCompassTarget(portalLoc);
                } else {
                    // No portal found, still enable but LocatorTask will handle it
                    player.setCompassTarget(deathLoc);
                }
            }
            
            cache.setTrackingLocation("Death");
            player.closeInventory();
            Messenger.success(player, "You are now tracking your death location.");
        } else {
            Messenger.info(player, "No death location was found.");
        }
    }

    /**
     * Open the locator menu to the given player
     */
    public static void openMenu(final Player player) {
        new LocatorMenu(player).displayTo(player);
    }
}
