package games.coob.smp.menu;

import games.coob.smp.PlayerCache;
import games.coob.smp.util.ItemCreator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Main menu for the player tracking system.
 */
public final class LocatorMenu extends SimpleMenu {

    private static final int PLAYER_BUTTON_SLOT = 11;
    private static final int INFO_BUTTON_SLOT = 15;

    public LocatorMenu(Player player) {
        super(player, 9 * 3, "&0&lPlayer Tracker");
        setupItems(player);
    }

    private void setupItems(Player player) {
        PlayerCache cache = PlayerCache.from(player);
        int trackingCount = cache.getTrackedTargets().size();

        inventory.setItem(PLAYER_BUTTON_SLOT, ItemCreator.of(
                Material.PLAYER_HEAD,
                "&r&b&lTrack Player",
                "",
                "&r&7Click to select a player",
                "&r&7to track their location.",
                "",
                "&r&eClick to open menu").make());

        inventory.setItem(INFO_BUTTON_SLOT, ItemCreator.of(
                Material.BOOK,
                "&r&e&lTracking Info",
                "",
                "&r&7View and manage your",
                "&r&7currently tracked targets.",
                "",
                "&r&7Currently tracking: &f" + trackingCount,
                "",
                "&r&eClick to view").make());
    }

    @Override
    protected void onMenuClick(Player player, int slot, ItemStack clicked) {
        if (slot == PLAYER_BUTTON_SLOT) {
            LocatorPlayersMenu.openMenu(player);
        } else if (slot == INFO_BUTTON_SLOT) {
            TrackingInfoMenu.openMenu(player);
        }
    }

    public static void openMenu(Player player) {
        new LocatorMenu(player).displayTo(player);
    }
}
