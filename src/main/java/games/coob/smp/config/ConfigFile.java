package games.coob.smp.config;

import games.coob.smp.SMPPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Level;

/**
 * Base class for managing YAML configuration files
 */
public abstract class ConfigFile {

	protected final File file;
	protected FileConfiguration config;

	protected ConfigFile(String fileName) {
		this.file = new File(SMPPlugin.getInstance().getDataFolder(), fileName);
		load();
	}

	/**
	 * Load the configuration file
	 */
	public void load() {
		if (!file.exists()) {
			file.getParentFile().mkdirs();
			saveDefaultConfig();
		}

		config = YamlConfiguration.loadConfiguration(file);
		onLoad();
	}

	/**
	 * Save the default configuration from resources if it doesn't exist
	 */
	protected void saveDefaultConfig() {
		InputStream defaultStream = SMPPlugin.getInstance().getResource(file.getName());
		if (defaultStream != null) {
			try {
				Files.copy(defaultStream, file.toPath());
			} catch (IOException e) {
				SMPPlugin.getInstance().getLogger().log(Level.SEVERE, "Could not save default config: " + file.getName(), e);
			}
		}
	}

	/**
	 * Save the configuration file
	 */
	public void save() {
		try {
			onSave();
			config.save(file);
		} catch (IOException e) {
			SMPPlugin.getInstance().getLogger().log(Level.SEVERE, "Could not save config: " + file.getName(), e);
		}
	}

	/**
	 * Reload the configuration file
	 */
	public void reload() {
		load();
	}

	/**
	 * Get the FileConfiguration
	 *
	 * @return The configuration
	 */
	public FileConfiguration getConfig() {
		return config;
	}

	/**
	 * Called when the config is loaded
	 */
	protected void onLoad() {
	}

	/**
	 * Called before the config is saved
	 */
	protected void onSave() {
	}
}
