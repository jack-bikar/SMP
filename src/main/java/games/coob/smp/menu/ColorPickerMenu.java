package games.coob.smp.menu;

import games.coob.smp.PlayerCache;
import games.coob.smp.tracking.MarkerColor;
import games.coob.smp.tracking.TrackedTarget;
import games.coob.smp.tracking.WaypointColorManager;
import games.coob.smp.util.ItemCreator;
import games.coob.smp.util.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Menu for selecting a marker color for a tracked player.
 */
public class ColorPickerMenu extends SimpleMenu {

    private static final int BACK_BUTTON_SLOT = 27;

    private final UUID targetPlayerUUID;

    // Colors to show (excluding DARK_RED which is reserved for death)
    private static final MarkerColor[] SELECTABLE_COLORS = {
            MarkerColor.WHITE, MarkerColor.ORANGE, MarkerColor.MAGENTA, MarkerColor.LIGHT_BLUE,
            MarkerColor.YELLOW, MarkerColor.LIME, MarkerColor.PINK, MarkerColor.GRAY,
            MarkerColor.LIGHT_GRAY, MarkerColor.CYAN, MarkerColor.PURPLE, MarkerColor.BLUE,
            MarkerColor.BROWN, MarkerColor.GREEN, MarkerColor.RED, MarkerColor.BLACK
    };

    public ColorPickerMenu(Player viewer, UUID targetPlayerUUID) {
        super(viewer, 9 * 4, "&0&lSelect Marker Color");
        this.targetPlayerUUID = targetPlayerUUID;
        setupItems(viewer);
    }

    private void setupItems(Player viewer) {
        PlayerCache cache = PlayerCache.from(viewer);
        TrackedTarget target = cache.getTrackedTarget(targetPlayerUUID);
        MarkerColor currentColor = target != null ? target.getColor() : MarkerColor.WHITE;

        Player targetPlayer = Bukkit.getPlayer(targetPlayerUUID);
        String targetName = targetPlayer != null ? targetPlayer.getName() : "Unknown";

        // Add header info
        inventory.setItem(4, ItemCreator.of(
                Material.PLAYER_HEAD,
                "&b&l" + targetName,
                "",
                "&7Current color: " + currentColor.getDisplayName(),
                "",
                "&7Select a new color below.").skullOwner(targetName).make());

        // Add color options
        int slot = 9; // Start on second row
        for (MarkerColor color : SELECTABLE_COLORS) {
            if (slot >= 27) break;

            boolean isCurrentColor = color == currentColor;
            String selectedIndicator = isCurrentColor ? " &a(Selected)" : "";

            inventory.setItem(slot, ItemCreator.of(
                    color.getWoolMaterial(),
                    color.getDisplayName() + selectedIndicator,
                    "",
                    "&7Click to select this color.").make());
            slot++;
        }

        // Back button
        inventory.setItem(BACK_BUTTON_SLOT, ItemCreator.of(
                Material.ARROW,
                "&c&lBack",
                "",
                "&7Return to tracking info.").make());

        // Stop tracking button
        inventory.setItem(35, ItemCreator.of(
                Material.BARRIER,
                "&c&lStop Tracking",
                "",
                "&7Stop tracking this player.").make());
    }

    @Override
    protected void onMenuClick(Player viewer, int slot, ItemStack clicked) {
        if (clicked == null) return;

        // Back button
        if (slot == BACK_BUTTON_SLOT) {
            TrackingInfoMenu.openMenu(viewer);
            return;
        }

        // Stop tracking button
        if (slot == 35) {
            PlayerCache cache = PlayerCache.from(viewer);
            cache.removeTrackedPlayer(targetPlayerUUID);

            if (!WaypointColorManager.isAnyoneTracking(targetPlayerUUID)) {
                Player targetPlayer = Bukkit.getPlayer(targetPlayerUUID);
                if (targetPlayer != null) WaypointColorManager.clearPlayerWaypointColor(targetPlayer);
            }

            Player targetPlayer = Bukkit.getPlayer(targetPlayerUUID);
            String targetName = targetPlayer != null ? targetPlayer.getName() : "Unknown";
            Messenger.success(viewer, "Stopped tracking " + targetName + ".");

            TrackingInfoMenu.openMenu(viewer);
            return;
        }

        // Color selection (slots 9-26)
        if (slot >= 9 && slot < 27) {
            int colorIndex = slot - 9;
            if (colorIndex < SELECTABLE_COLORS.length) {
                MarkerColor selectedColor = SELECTABLE_COLORS[colorIndex];
                PlayerCache cache = PlayerCache.from(viewer);
                cache.setTrackedPlayerColor(targetPlayerUUID, selectedColor);

                // Apply waypoint color immediately so locator bar matches menu
                Player targetPlayer = Bukkit.getPlayer(targetPlayerUUID);
                if (targetPlayer != null) {
                    WaypointColorManager.setPlayerWaypointColor(targetPlayer, selectedColor);
                }

                String targetName = targetPlayer != null ? targetPlayer.getName() : "Unknown";
                Messenger.success(viewer, "Changed " + targetName + "'s marker to " + selectedColor.getDisplayName() + "&a.");

                // Refresh menu to show updated selection
                new ColorPickerMenu(viewer, targetPlayerUUID).displayTo(viewer);
            }
        }
    }

    public static void openMenu(Player viewer, UUID targetPlayerUUID) {
        new ColorPickerMenu(viewer, targetPlayerUUID).displayTo(viewer);
    }
}
