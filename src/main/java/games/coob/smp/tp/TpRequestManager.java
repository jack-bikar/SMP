package games.coob.smp.tp;

import games.coob.smp.PlayerCache;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages teleport requests. Requester asks to TP to target; target sees
 * [ACCEPT]/[DENY] in chat. Combat timer blocks the requester from TPing while
 * in combat.
 */
public final class TpRequestManager {

    private static final TpRequestManager instance = new TpRequestManager();
    private static final int EXPIRATION_SECONDS = 60;

    /** Requester UUID -> target UUID (requester wants to TP to target) */
    private final Map<UUID, UUID> pendingRequests = new ConcurrentHashMap<>();

    private TpRequestManager() {
    }

    public static TpRequestManager getInstance() {
        return instance;
    }

    /**
     * Send a TP request: requester wants to teleport to target.
     * Target receives clickable [ACCEPT] [DENY] in chat.
     */
    public void sendRequest(Player requester, Player target) {
        if (!Settings.TpSection.ENABLE_TP) {
            ColorUtil.sendMessage(requester, "&cTP requests are disabled.");
            return;
        }
        if (requester.equals(target)) {
            ColorUtil.sendMessage(requester, "&cYou cannot teleport to yourself.");
            return;
        }

        pendingRequests.put(requester.getUniqueId(), target.getUniqueId());

        Component acceptButton = Component.text("[ACCEPT]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/tp accept " + requester.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Click to accept teleport request")));

        Component denyButton = Component.text("[DENY]", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/tp deny " + requester.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Click to deny teleport request")));

        Component message = Component.text()
                .append(Component.text(requester.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" wants to teleport to you. ", NamedTextColor.WHITE))
                .append(acceptButton)
                .append(Component.text(" "))
                .append(denyButton)
                .append(Component.text(" (Expires in " + EXPIRATION_SECONDS + " seconds)", NamedTextColor.GRAY))
                .build();

        target.sendMessage(message);
        ColorUtil.sendMessage(requester, "&eTP request sent to &3" + target.getName() + "&e. Waiting for response...");

        SchedulerUtil.runLater(EXPIRATION_SECONDS * 20L, () -> {
            UUID removed = pendingRequests.remove(requester.getUniqueId());
            if (removed != null) {
                Player req = Bukkit.getPlayer(requester.getUniqueId());
                if (req != null && req.isOnline()) {
                    ColorUtil.sendMessage(req, "&cTP request to &3" + target.getName() + " &cexpired.");
                }
            }
        });
    }

    /**
     * Target accepts the TP request from requester. Teleports requester to target
     * if requester is not in combat.
     */
    public boolean acceptRequest(Player target, String requesterName) {
        if (!Settings.TpSection.ENABLE_TP) {
            ColorUtil.sendMessage(target, "&cTP requests are disabled.");
            return false;
        }
        Player requester = Bukkit.getPlayer(requesterName);
        if (requester == null || !requester.isOnline()) {
            ColorUtil.sendMessage(target, "&cPlayer not found or offline.");
            return false;
        }

        UUID targetUUID = target.getUniqueId();
        UUID requesterUUID = requester.getUniqueId();
        if (!targetUUID.equals(pendingRequests.get(requesterUUID))) {
            ColorUtil.sendMessage(target, "&cNo pending TP request from that player.");
            return false;
        }

        pendingRequests.remove(requesterUUID);

        PlayerCache requesterCache = PlayerCache.from(requester);
        if (requesterCache.isInCombat()) {
            ColorUtil.sendMessage(target, "&c" + requester.getName() + " is in combat and cannot teleport yet.");
            ColorUtil.sendMessage(requester,
                    "&cYou cannot teleport while in combat. Wait for the combat timer to end.");
            return false;
        }

        requester.teleport(target.getLocation());
        ColorUtil.sendMessage(target,
                "&aYou accepted the TP request. &3" + requester.getName() + " &ateleported to you.");
        ColorUtil.sendMessage(requester, "&aYou teleported to &3" + target.getName() + "&a.");
        return true;
    }

    /**
     * Target denies the TP request from requester.
     */
    public boolean denyRequest(Player target, String requesterName) {
        if (!Settings.TpSection.ENABLE_TP) {
            ColorUtil.sendMessage(target, "&cTP requests are disabled.");
            return false;
        }
        Player requester = Bukkit.getPlayer(requesterName);
        if (requester == null) {
            ColorUtil.sendMessage(target, "&cPlayer not found.");
            return false;
        }

        UUID targetUUID = target.getUniqueId();
        if (!targetUUID.equals(pendingRequests.get(requester.getUniqueId()))) {
            ColorUtil.sendMessage(target, "&cNo pending TP request from that player.");
            return false;
        }

        pendingRequests.remove(requester.getUniqueId());
        ColorUtil.sendMessage(target, "&cYou denied the TP request from &3" + requester.getName() + "&c.");
        ColorUtil.sendMessage(requester, "&c" + target.getName() + " &cdenied your TP request.");
        return true;
    }
}
