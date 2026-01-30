package games.coob.smp.menu;

import games.coob.smp.PlayerCache;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.tracking.MarkerColor;
import games.coob.smp.tracking.TrackedTarget;
import games.coob.smp.tracking.TrackingRegistry;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.ItemCreator;
import games.coob.smp.util.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Menu showing all currently tracked targets with their marker colors.
 * Players are represented by wool colors, clicking opens color picker.
 * Death location shows as dark red wool (non-editable).
 */
public class TrackingInfoMenu extends SimpleMenu {

    private static final int BACK_BUTTON_SLOT = 27;

    public TrackingInfoMenu(Player viewer) {
        super(viewer, 9 * 4, "&0&lTracking Info");
        setupItems(viewer);
    }

    private void setupItems(Player viewer) {
        PlayerCache cache = PlayerCache.from(viewer);
        int slot = 0;

        // Add tracked players as wool
        for (TrackedTarget target : cache.getTrackedTargets()) {
            if (slot >= 27) break; // Leave bottom row for controls

            if (target.isPlayer()) {
                Player targetPlayer = Bukkit.getPlayer(target.getTargetUUID());
                String playerName = targetPlayer != null ? targetPlayer.getName() : "Unknown";
                MarkerColor color = target.getColor();

                inventory.setItem(slot, ItemCreator.of(
                        color.getWoolMaterial(),
                        color.getDisplayName() + " &7- &f" + playerName,
                        "",
                        "&7This player appears as",
                        "&7" + color.getDisplayName() + " &7on your locator bar.",
                        "",
                        "&eLeft-click to change color",
                        "&cRight-click to stop tracking").make());
            } else if (target.isDeath()) {
                inventory.setItem(slot, ItemCreator.of(
                        Material.RED_WOOL,
                        "&4&lDeath Location",
                        "",
                        "&7Your death location marker.",
                        "&7Always appears as &4dark red&7.",
                        "",
                        "&cRight-click to stop tracking").make());
            }
            slot++;
        }

        // Empty state
        if (cache.getTrackedTargets().isEmpty()) {
            inventory.setItem(13, ItemCreator.of(
                    Material.BARRIER,
                    "&c&lNo Active Tracking",
                    "",
                    "&7You are not tracking anything.",
                    "",
                    "&7Use &e/track &7to open the menu",
                    "&7or &e/track death &7to track your",
                    "&7death location.").make());
        }

        // Back button
        inventory.setItem(BACK_BUTTON_SLOT, ItemCreator.of(
                Material.ARROW,
                "&c&lBack",
                "",
                "&7Return to tracker menu.").make());
    }

    @Override
    protected void onMenuClick(Player viewer, int slot, ItemStack clicked) {
        if (clicked == null) return;

        // Back button
        if (slot == BACK_BUTTON_SLOT) {
            LocatorMenu.openMenu(viewer);
            return;
        }

        PlayerCache cache = PlayerCache.from(viewer);
        if (slot < 0 || slot >= cache.getTrackedTargets().size()) return;

        TrackedTarget target = cache.getTrackedTargets().get(slot);

        // Check click type (we can't easily detect right-click in InventoryClickEvent without more code)
        // For simplicity: clicking on wool opens color picker for players, or stops tracking for death
        if (target.isPlayer()) {
            // Open color picker
            ColorPickerMenu.openMenu(viewer, target.getTargetUUID());
        } else if (target.isDeath()) {
            // Stop tracking death
            cache.stopTrackingDeath();
            if (!cache.isTracking()) {
                TrackingRegistry.stopTracking(viewer.getUniqueId());
                LocatorBarManager.disableReceive(viewer);
            }
            Messenger.success(viewer, "Stopped tracking death location.");
            // Refresh menu
            new TrackingInfoMenu(viewer).displayTo(viewer);
        }
    }

    public static void openMenu(Player player) {
        new TrackingInfoMenu(player).displayTo(player);
    }
}
