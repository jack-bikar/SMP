package games.coob.smp.menu;

import games.coob.smp.PlayerCache;
import games.coob.smp.tracking.TrackingRequestManager;
import games.coob.smp.util.ItemCreator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu showing who is currently tracking the viewer. Click a tracker head to revoke.
 */
public final class WhoIsTrackingMeMenu extends SimpleMenu {

    private static final int BACK_BUTTON_SLOT = 27;
    private static final int EMPTY_STATE_SLOT = 13;
    private static final int MAX_TRACKERS = 27;

    private final List<Player> trackers = new ArrayList<>();

    public WhoIsTrackingMeMenu(Player viewer) {
        super(viewer, 9 * 4, "&0&lWho's tracking you");
        refreshTrackers();
        setupItems();
    }

    private void refreshTrackers() {
        trackers.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(viewer)) continue;
            PlayerCache cache = PlayerCache.from(p);
            if (cache.getTrackedTarget(viewer.getUniqueId()) != null) {
                trackers.add(p);
            }
        }
    }

    private void setupItems() {
        inventory.clear();
        refreshTrackers();

        if (trackers.isEmpty()) {
            inventory.setItem(EMPTY_STATE_SLOT, ItemCreator.of(
                    Material.BARRIER,
                    "&aNo one is tracking you.",
                    "",
                    "&7No players are currently",
                    "&7tracking your location.").make());
        } else {
            int count = Math.min(trackers.size(), MAX_TRACKERS);
            for (int i = 0; i < count; i++) {
                Player tracker = trackers.get(i);
                inventory.setItem(i, ItemCreator.of(
                        Material.PLAYER_HEAD,
                        "&c" + tracker.getName() + " &7is tracking you",
                        "",
                        "&eClick to revoke").skullOwner(tracker.getName()).make());
            }
        }

        inventory.setItem(BACK_BUTTON_SLOT, ItemCreator.of(
                Material.ARROW,
                "&c&lBack",
                "",
                "&7Return to player tracker menu.").make());
    }

    @Override
    protected void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
        if (clicked == null) return;

        if (slot == BACK_BUTTON_SLOT) {
            LocatorMenu.openMenu(viewer);
            return;
        }

        if (trackers.isEmpty() || slot >= trackers.size()) return;

        Player tracker = trackers.get(slot);
        TrackingRequestManager.getInstance().revokeTracker(viewer, tracker);
        setupItems();
    }

    public static void openMenu(Player player) {
        new WhoIsTrackingMeMenu(player).displayTo(player);
    }
}
