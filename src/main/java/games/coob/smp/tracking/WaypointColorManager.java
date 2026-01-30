package games.coob.smp.tracking;

import games.coob.smp.PlayerCache;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applies waypoint colors on the locator bar to match the colors chosen in the
 * Tracking Info menu. Uses scoreboard teams: the locator bar displays a player's
 * waypoint in their team color.
 */
public final class WaypointColorManager {

    private static final String TEAM_PREFIX = "smp_wp_";

    private static Scoreboard scoreboard;
    private static final Map<MarkerColor, Team> teamsByColor = new HashMap<>();
    private static final Map<UUID, MarkerColor> playerColor = new HashMap<>();

    static {
        scoreboard = Bukkit.getScoreboardManager() != null
                ? Bukkit.getScoreboardManager().getMainScoreboard()
                : null;
    }

    private WaypointColorManager() {
    }

    private static Team getOrCreateTeam(MarkerColor color) {
        if (scoreboard == null) {
            scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        }
        if (scoreboard == null) return null;

        return teamsByColor.computeIfAbsent(color, c -> {
            String name = TEAM_PREFIX + c.name();
            Team team = scoreboard.getTeam(name);
            if (team == null) {
                team = scoreboard.registerNewTeam(name);
            }
            ChatColor chatColor = c.getChatColor();
            if (chatColor != null) {
                team.setColor(chatColor);
            }
            return team;
        });
    }

    /**
     * Set the waypoint color for a tracked player so it matches the menu.
     * Call when the player is being tracked (same dimension) with the color
     * chosen in Tracking Info.
     */
    public static void setPlayerWaypointColor(Player target, MarkerColor color) {
        if (target == null || color == null) return;
        if (scoreboard == null) {
            scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        }
        if (scoreboard == null) return;

        // Remove from previous team if any
        MarkerColor previous = playerColor.get(target.getUniqueId());
        if (previous != null && previous != color) {
            Team oldTeam = teamsByColor.get(previous);
            if (oldTeam != null) {
                oldTeam.removeEntry(target.getName());
            }
        }

        Team team = getOrCreateTeam(color);
        if (team != null) {
            if (!team.hasEntry(target.getName())) {
                team.addEntry(target.getName());
            }
            playerColor.put(target.getUniqueId(), color);
        }
    }

    /**
     * Clear the waypoint color for a player when they are no longer tracked by anyone.
     */
    public static void clearPlayerWaypointColor(Player target) {
        if (target == null) return;

        MarkerColor current = playerColor.remove(target.getUniqueId());
        if (current != null) {
            Team team = teamsByColor.get(current);
            if (team != null) {
                team.removeEntry(target.getName());
            }
        }
    }

    /**
     * Check if any tracker has this player in their tracked targets (for clearing color when last tracker stops).
     */
    public static boolean isAnyoneTracking(UUID targetUUID) {
        return Bukkit.getOnlinePlayers().stream()
                .anyMatch(tracker -> {
                    PlayerCache cache = PlayerCache.from(tracker);
                    return cache.getTrackedTarget(targetUUID) != null;
                });
    }
}
