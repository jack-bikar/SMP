package games.coob.smp.menu;

import games.coob.smp.util.ItemCreator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
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
        inventory.setItem(PLAYER_BUTTON_SLOT, ItemCreator.of(
                Material.PLAYER_HEAD,
                "&r&b&lTrack Player",
                "",
                "&r&7Click to select a player",
                "&r&7to track their location.",
                "",
                "&r&eClick to open menu").make());

        inventory.setItem(INFO_BUTTON_SLOT, ItemCreator.of(
                Material.CLOCK,
                "&r&e&lWho's tracking me",
                "",
                "&r&7See who is tracking you.",
                "&r&7Click to revoke.",
                "",
                "&r&eClick to open").make());
    }

    @Override
    protected void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
        if (slot == PLAYER_BUTTON_SLOT) {
            LocatorPlayersMenu.openMenu(player);
        } else if (slot == INFO_BUTTON_SLOT) {
            WhoIsTrackingMeMenu.openMenu(player);
        }
    }

    public static void openMenu(Player player) {
        new LocatorMenu(player).displayTo(player);
    }
}
