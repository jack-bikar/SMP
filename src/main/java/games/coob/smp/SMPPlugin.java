package games.coob.smp;

import games.coob.smp.command.InvEditCommand;
import games.coob.smp.command.SpawnCommand;
import games.coob.smp.hologram.HologramRegistry;
import games.coob.smp.listener.AxeListener;
import games.coob.smp.listener.CompassListener;
import games.coob.smp.listener.DeathChestListener;
import games.coob.smp.listener.SMPListener;
import games.coob.smp.model.DeathChestRegistry;
import games.coob.smp.model.Effects;
import games.coob.smp.task.CompassTask;
import games.coob.smp.task.HologramTask;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.plugin.SimplePlugin;

public final class SMPPlugin extends SimplePlugin { // TODO auto register anotation

	@Override
	protected void onPluginStart() {
		Common.runLater(DeathChestRegistry::getInstance);
		HologramRegistry.getInstance().spawnFromDisk();
	}

	@Override
	protected void onReloadablesStart() {
		if (Common.doesPluginExist("EffectLib"))
			Effects.load();

		// Register commands
		registerCommand(new InvEditCommand());
		registerCommand(new SpawnCommand());

		// Register events
		registerEvents(SMPListener.getInstance());
		registerEvents(CompassListener.getInstance());
		registerEvents(DeathChestListener.getInstance());
		registerEvents(AxeListener.getInstance());

		Common.runTimer(20, new CompassTask());
		Common.runTimer(20, new HologramTask());
	}

	@Override
	protected void onPluginStop() {
		DeathChestRegistry.getInstance().save();
	}

	@Override
	protected void onPluginReload() {
		DeathChestRegistry.getInstance().save();

		if (Common.doesPluginExist("EffectLib"))
			Effects.disable();
	}

	/* ------------------------------------------------------------------------------- */
	/* Static */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Return the instance of this plugin, which simply refers to a static
	 * field already created for you in SimplePlugin but casts it to your
	 * specific plugin instance for your convenience.
	 *
	 * @return
	 */
	public static SMPPlugin getInstance() {
		return (SMPPlugin) SimplePlugin.getInstance();
	}
}
