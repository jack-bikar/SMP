package games.coob.smp;

import games.coob.smp.command.InvEditCommand;
import games.coob.smp.command.SMPCommand;
import games.coob.smp.command.SpawnCommand;
import games.coob.smp.hologram.HologramRegistry;
import games.coob.smp.listener.CompassListener;
import games.coob.smp.listener.DeathChestListener;
import games.coob.smp.listener.SMPListener;
import games.coob.smp.model.DeathChestRegistry;
import games.coob.smp.model.Effects;
import games.coob.smp.settings.Settings;
import games.coob.smp.task.CompassTask;
import games.coob.smp.task.HologramTask;
import games.coob.smp.util.PluginUtil;
import games.coob.smp.util.SchedulerUtil;
import org.bukkit.plugin.java.JavaPlugin;

public final class SMPPlugin extends JavaPlugin {

	private static SMPPlugin instance;

	@Override
	public void onEnable() {
		instance = this;

		// Load settings
		Settings.loadSettings();

		// Initialize registries
		SchedulerUtil.runLater(1, () -> {
			DeathChestRegistry.getInstance();
			HologramRegistry.getInstance().spawnFromDisk();
		});

		// Load EffectLib if available
		if (PluginUtil.isPluginEnabled("EffectLib")) {
			Effects.load();
		}

		// Register commands
		getCommand("smp").setExecutor(new SMPCommand());
		getCommand("inv").setExecutor(new InvEditCommand());
		getCommand("inventory").setExecutor(new InvEditCommand());
		getCommand("spawn").setExecutor(new SpawnCommand());

		// Register events
		getServer().getPluginManager().registerEvents(SMPListener.getInstance(), this);
		getServer().getPluginManager().registerEvents(CompassListener.getInstance(), this);
		getServer().getPluginManager().registerEvents(DeathChestListener.getInstance(), this);

		// Start tasks
		SchedulerUtil.runTimer(20, new CompassTask());
		SchedulerUtil.runTimer(20, new HologramTask());
	}

	@Override
	public void onDisable() {
		DeathChestRegistry.getInstance().save();

		if (PluginUtil.isPluginEnabled("EffectLib")) {
			Effects.disable();
		}
	}

	@Override
	public void onLoad() {
		instance = this;
	}

	public static SMPPlugin getInstance() {
		return instance;
	}
}
