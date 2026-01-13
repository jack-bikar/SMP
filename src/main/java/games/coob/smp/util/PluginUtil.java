package games.coob.smp.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Utility class for plugin-related operations
 */
public final class PluginUtil {

	private PluginUtil() {
	}

	/**
	 * Check if a plugin is installed and enabled
	 *
	 * @param pluginName The name of the plugin
	 * @return True if the plugin exists and is enabled
	 */
	public static boolean isPluginEnabled(String pluginName) {
		Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
		return plugin != null && plugin.isEnabled();
	}
}
