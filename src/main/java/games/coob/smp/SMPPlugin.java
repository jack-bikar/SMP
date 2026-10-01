package games.coob.smp;

import games.coob.smp.combat.CombatListener;
import games.coob.smp.combat.CombatNPC;
import games.coob.smp.combat.CombatPunishmentManager;
import games.coob.smp.combat.CombatTracker;
import games.coob.smp.combat.GhostLootStore;
import games.coob.smp.command.InvEditCommand;
import games.coob.smp.command.SMPCommand;
import games.coob.smp.command.SpawnCommand;
import games.coob.smp.command.TpCommand;
import games.coob.smp.command.TrackCommand;
import games.coob.smp.config.ConfigFile;
import games.coob.smp.duel.ArenaCommand;
import games.coob.smp.duel.DuelArenaListener;
import games.coob.smp.duel.DuelCommand;
import games.coob.smp.duel.DuelListener;
import games.coob.smp.duel.DuelManager;
import games.coob.smp.duel.DuelMobListener;
import games.coob.smp.duel.DuelQueueManager;
import games.coob.smp.duel.TeamDuelManager;
import games.coob.smp.duel.kit.DuelKitListener;
import games.coob.smp.duel.kit.DuelKits;
import games.coob.smp.duel.model.ArenaRegistry;
import games.coob.smp.duel.model.DuelStatistics;
import games.coob.smp.listener.DeathChestListener;
import games.coob.smp.listener.LocatorListener;
import games.coob.smp.listener.SMPListener;
import games.coob.smp.menu.MenuListener;
import games.coob.smp.menu.SimpleMenu;
import games.coob.smp.model.DeathChestRegistry;
import games.coob.smp.model.DeathMessages;
import games.coob.smp.nickname.NickCommand;
import games.coob.smp.nickname.NicknameManager;
import games.coob.smp.model.Effects;
import games.coob.smp.settings.Settings;
import games.coob.smp.task.HologramTask;
import games.coob.smp.task.LocatorTask;
import games.coob.smp.tracking.PortalCache;
import games.coob.smp.tracking.TrackingRegistry;
import games.coob.smp.tracking.VanillaLocator;
import games.coob.smp.tracking.WaypointColorManager;
import games.coob.smp.tracking.WaypointPacketSender;
import games.coob.smp.util.SchedulerUtil;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class SMPPlugin extends JavaPlugin {

    private static SMPPlugin instance;

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        instance = this;

        Settings.loadSettings();

        // Initialize waypoint packet sender (uses reflection)
        WaypointPacketSender.initialize();

        // Reset any leftover name tag colors from previous runs
        WaypointColorManager.resetAllNameTagColors();

        // Load data files
        DeathChestRegistry.getInstance();
        DeathMessages.getInstance();
        GhostLootStore.getInstance();
        NicknameManager.getInstance();
        NicknameManager.removeAllNameTags();
        ArenaRegistry.getInstance();
        DuelStatistics.getInstance();
        DuelKits.getInstance();

        registerCommand("smp", new SMPCommand());
        InvEditCommand invEditCommand = new InvEditCommand();
        registerCommand("inv", invEditCommand);
        registerCommand("spawn", new SpawnCommand());
        registerCommand("track", new TrackCommand());
        registerCommand("tp", new TpCommand());
        registerCommand("duel", new DuelCommand());
        registerCommand("arena", new ArenaCommand());
        registerCommand("nick", new NickCommand());

        registerEvents(
                SMPListener.getInstance(),
                LocatorListener.getInstance(),
                DeathChestListener.getInstance(),
                CombatListener.getInstance(),
                DuelListener.getInstance(),
                DuelMobListener.getInstance(),
                DuelKitListener.getInstance(),
                DuelArenaListener.getInstance(),
                MenuListener.getInstance(),
                VanillaLocator.getInstance(),
                NicknameManager.getInstance(),
                invEditCommand);

        // Locator updates every 2 seconds, death chest holograms every 2 seconds
        SchedulerUtil.runTimer(40, new LocatorTask());
        SchedulerUtil.runTimer(20, 40, new HologramTask());
        // Vanilla locator bar: name of the player you are facing
        SchedulerUtil.runTimer(VanillaLocator.PERIOD_TICKS, VanillaLocator.PERIOD_TICKS, VanillaLocator.getInstance());
    }

    @Override
    public void onDisable() {
        // Each step is guarded, so one failure can't skip the saves after it

        // Close plugin menus first, so admin edits of offline inventories are saved
        safely("closing menus", () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof SimpleMenu)
                    player.closeInventory();
            }
        });

        // Duels next: players are sent back and their data is updated
        safely("ending duels", () -> DuelManager.getInstance().cleanup());
        safely("clearing duel queues", () -> {
            DuelQueueManager.getInstance().clear();
            TeamDuelManager.getInstance().clear();
        });
        safely("removing name tags", NicknameManager::removeAllNameTags);

        safely("cleaning up tracking", () -> {
            TrackingRegistry.clear();
            PortalCache.clear();
            WaypointPacketSender.clearAll();
            LocatorTask.cleanupAll();
            WaypointColorManager.resetAllNameTagColors();
        });

        safely("dropping ghost body loot", CombatNPC::cleanupAll);
        safely("cleaning up combat", () -> {
            CombatPunishmentManager.cleanup();
            CombatTracker.clearAll();
        });
        safely("stopping effects", Effects::disable);

        // Save data: finish queued background writes first, so they can't land after the final saves
        safely("closing death chests", () -> DeathChestRegistry.getInstance().shutdown());
        safely("finishing file writes", ConfigFile::flushPendingWrites);
        safely("saving death chests", () -> DeathChestRegistry.getInstance().saveNow());
        safely("saving arenas", () -> ArenaRegistry.getInstance().saveNow());
        safely("saving duel stats", () -> DuelStatistics.getInstance().saveNow());
        safely("saving nicknames", () -> NicknameManager.getInstance().saveNow());
        safely("saving ghost loot", () -> GhostLootStore.getInstance().saveNow());
        safely("saving player data", PlayerCache::saveAllNow);
    }

    private void safely(String step, Runnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            getLogger().log(java.util.logging.Level.SEVERE, "Error while " + step + " on shutdown", throwable);
        }
    }

    private <T extends CommandExecutor & TabCompleter> void registerCommand(String name, T handler) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command /" + name + " is missing from plugin.yml");
            return;
        }
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    private void registerEvents(Listener... listeners) {
        for (Listener listener : listeners)
            getServer().getPluginManager().registerEvents(listener, this);
    }

    public static SMPPlugin getInstance() {
        return instance;
    }
}
