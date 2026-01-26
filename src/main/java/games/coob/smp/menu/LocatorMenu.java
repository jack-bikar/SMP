package games.coob.smp.menu;

import games.coob.smp.PlayerCache;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.tracking.PortalFinder;
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
            
            // Set compass target to point to death location or nearest portal to death dimension
            org.bukkit.Location deathLoc = cache.getDeathLocation();
            if (deathLoc.getWorld() == player.getWorld()) {
                player.setCompassTarget(deathLoc);
            } else {
                org.bukkit.Location portalLoc = resolvePortalForDeathCrossDimension(cache, player, deathLoc.getWorld().getEnvironment());
                player.setCompassTarget(portalLoc != null ? portalLoc : player.getLocation());
            }
            
            cache.setTrackingLocation("Death");
            player.closeInventory();
            Messenger.success(player, "You are now tracking your death location.");
        } else {
            Messenger.info(player, "No death location was found.");
        }
    }

    private static org.bukkit.Location resolvePortalForDeathCrossDimension(PlayerCache cache, org.bukkit.entity.Player player, org.bukkit.World.Environment targetEnv) {
        org.bukkit.Location portal = cache.getPortalLocation();
        if (portal != null && portal.getWorld() == player.getWorld()) {
            if (targetEnv == org.bukkit.World.Environment.NORMAL && (player.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER || player.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END)) {
                return portal;
            }
        }
        portal = cache.getOverworldNetherPortalLocation();
        if (portal != null && portal.getWorld() == player.getWorld() && targetEnv == org.bukkit.World.Environment.NETHER) return portal;
        portal = cache.getOverworldEndPortalLocation();
        if (portal != null && portal.getWorld() == player.getWorld() && targetEnv == org.bukkit.World.Environment.THE_END) return portal;
        return PortalFinder.findNearestPortalToDimension(player.getWorld(), player.getLocation(), targetEnv);
    }

    /**
     * Open the locator menu to the given player
     */
    public static void openMenu(final Player player) {
        new LocatorMenu(player).displayTo(player);
    }
}
