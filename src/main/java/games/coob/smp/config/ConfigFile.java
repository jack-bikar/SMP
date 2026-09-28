package games.coob.smp.config;

import games.coob.smp.SMPPlugin;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Base class for managing YAML configuration files.
 * <p>
 * Saving serializes the YAML on the calling (main) thread and writes the file
 * on a single background thread, so disk IO never blocks the server tick and
 * writes to the same file always happen in order.
 */
public abstract class ConfigFile {

	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "SMP-FileWriter");
		thread.setDaemon(true);
		return thread;
	});

	/**
	 * Latest content of files whose background write hasn't finished yet, so a
	 * file that is re-opened in the meantime (e.g. a player rejoining right away)
	 * reads the newest data instead of the old file.
	 */
	private static final Map<String, String> PENDING = new ConcurrentHashMap<>();

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

		config = read();
		onLoad();
	}

	private YamlConfiguration read() {
		YamlConfiguration yaml = new YamlConfiguration();
		String pending = PENDING.get(file.getAbsolutePath());
		try {
			if (pending != null) {
				yaml.loadFromString(pending);
			} else if (file.exists()) {
				yaml.load(file);
			}
		} catch (IOException | InvalidConfigurationException e) {
			// Keep the unreadable file instead of overwriting it with empty data on the next save
			File backup = new File(file.getParentFile(), file.getName() + ".broken-" + System.currentTimeMillis());
			file.renameTo(backup);
			SMPPlugin.getInstance().getLogger().log(Level.SEVERE,
					"Could not read " + file.getName() + ", it was moved to " + backup.getName(), e);
		}
		return yaml;
	}

	/**
	 * Save the default configuration from resources if it doesn't exist
	 */
	protected void saveDefaultConfig() {
		InputStream defaultStream = SMPPlugin.getInstance().getResource(file.getName());
		if (defaultStream != null) {
			try (defaultStream) {
				Files.copy(defaultStream, file.toPath());
			} catch (IOException e) {
				SMPPlugin.getInstance().getLogger().log(Level.SEVERE, "Could not save default config: " + file.getName(), e);
			}
		}
	}

	/**
	 * Adds keys that exist in the bundled default file but are missing from the
	 * file on disk (e.g. new settings after a plugin update). Existing values are
	 * never changed.
	 *
	 * @return true if any key was added
	 */
	protected boolean addMissingDefaults() {
		InputStream defaultStream = SMPPlugin.getInstance().getResource(file.getName());
		if (defaultStream == null)
			return false;

		YamlConfiguration defaults;
		try (InputStreamReader reader = new InputStreamReader(defaultStream, StandardCharsets.UTF_8)) {
			defaults = YamlConfiguration.loadConfiguration(reader);
		} catch (IOException e) {
			return false;
		}

		boolean changed = false;
		for (String key : defaults.getKeys(true)) {
			if (!defaults.isConfigurationSection(key) && !config.contains(key, true)) {
				config.set(key, defaults.get(key));
				config.setComments(key, defaults.getComments(key));
				changed = true;
			}
		}
		return changed;
	}

	/**
	 * Save the configuration file (file IO happens off the main thread)
	 */
	public void save() {
		onSave();
		final String data = config.saveToString();
		if (WRITER.isShutdown()) {
			write(data);
			return;
		}
		final String key = file.getAbsolutePath();
		PENDING.put(key, data);
		WRITER.execute(() -> {
			write(data);
			PENDING.remove(key, data);
		});
	}

	/**
	 * Save the configuration file immediately on the calling thread.
	 * Use on plugin disable.
	 */
	public void saveNow() {
		onSave();
		write(config.saveToString());
	}

	/**
	 * Writes to a temporary file and swaps it in, so a crash mid-write never
	 * leaves a half-written (truncated) file behind.
	 */
	private synchronized void write(String data) {
		try {
			file.getParentFile().mkdirs();
			Path target = file.toPath();
			Path temp = target.resolveSibling(file.getName() + ".tmp");
			Files.writeString(temp, data, StandardCharsets.UTF_8);
			try {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			SMPPlugin.getInstance().getLogger().log(Level.SEVERE, "Could not save config: " + file.getName(), e);
		}
	}

	/**
	 * Wait for all pending background writes to finish. Call on plugin disable.
	 */
	public static void flushPendingWrites() {
		WRITER.shutdown();
		try {
			WRITER.awaitTermination(10, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
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
