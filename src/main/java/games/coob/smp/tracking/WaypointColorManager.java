package games.coob.smp.tracking;

import games.coob.smp.PlayerCache;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applies waypoint colors on the locator bar to match the colors chosen in the
 * Tracking Info menu. Uses the main scoreboard; team color affects both the
 * waypoint on the locator bar and the player's name tag/tab list while they
 * are being tracked.
 */
public final class WaypointColorManager {

    private static final String TEAM_PREFIX = "smp_wp_";

    private static Scoreboard scoreboard;
    private static final Map<MarkerColor, Team> teamsByColor = new HashMap<>();
    private static final Map<UUID, MarkerColor> playerColor = new HashMap<>();

    static {
        if (Bukkit.getScoreboardManager() != null) {
            scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        }
    }

    private WaypointColorManager() {
    }

    private static Scoreboard getScoreboard() {
        if (scoreboard == null && Bukkit.getScoreboardManager() != null) {
            scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        }
        return scoreboard;
    }

    private static Team getOrCreateTeam(MarkerColor color) {
        Scoreboard board = getScoreboard();
        if (board == null)
            return null;

        return teamsByColor.computeIfAbsent(color, c -> {
            String name = TEAM_PREFIX + c.name();
            Team team = board.getTeam(name);
            if (team == null) {
                team = board.registerNewTeam(name);
            }
            NamedTextColor textColor = c.getTextColor();
            if (textColor != null) {
                team.color(textColor);
            }
            return team;
        });
    }

    /**
     * Set the waypoint color for a tracked player so the locator bar matches the
     * menu.
     */
    public static void setPlayerWaypointColor(Player target, MarkerColor color) {
        if (target == null || color == null)
            return;
        Scoreboard board = getScoreboard();
        if (board == null)
            return;

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
     * Clear the waypoint/name tag color for a player when they are no longer
     * tracked by anyone.
     */
    public static void clearPlayerWaypointColor(Player target) {
        if (target == null)
            return;

        MarkerColor current = playerColor.remove(target.getUniqueId());
        if (current != null) {
            Team team = teamsByColor.get(current);
            if (team != null) {
                team.removeEntry(target.getName());
            }
        }
    }

    /**
     * Remove all players from this plugin's tracking teams on the main scoreboard.
     * Use this to reset name tag colors if they were left coloured after the plugin
     * previously added players to teams.
     */
    public static void resetAllNameTagColors() {
        Scoreboard board = getScoreboard();
        if (board == null)
            return;

        for (Team team : board.getTeams()) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                for (String entry : new java.util.HashSet<>(team.getEntries())) {
                    team.removeEntry(entry);
                }
            }
        }
        playerColor.clear();
    }

    public static boolean isAnyoneTracking(UUID targetUUID) {
        return Bukkit.getOnlinePlayers().stream()
                .anyMatch(tracker -> {
                    PlayerCache cache = PlayerCache.from(tracker);
                    return cache.getTrackedTarget(targetUUID) != null;
                });
    }
}
