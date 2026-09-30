package games.coob.smp.config;

import games.coob.smp.SMPPlugin;
import games.coob.smp.util.SchedulerUtil;
import org.bukkit.configuration.ConfigurationSection;
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
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Base class for managing YAML configuration files.
 * <p>
 * Saving serializes the YAML on the calling (main) thread and writes the file
 * on a single background thread, so disk IO never blocks the server tick and
 * writes to the same file always happen in order. Big files without comments
 * can also be serialized in the background ({@link #isSerializedInBackground()}),
 * and a queued write is skipped when a newer save of the same file follows it.
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
	private static final Map<String, PendingWrite> PENDING = new ConcurrentHashMap<>();

	protected final File file;
	protected FileConfiguration config;
	/** A {@link #saveLater} is already scheduled. */
	private boolean saveScheduled;

	/** Content waiting to be written. The YAML is built once, by whichever thread needs it first. */
	private static final class PendingWrite {
		private final Supplier<String> source;
		private String data;

		PendingWrite(Supplier<String> source) {
			this.source = source;
		}

		synchronized String data() {
			if (data == null)
				data = source.get();
			return data;
		}
	}

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
		PendingWrite pending = PENDING.get(file.getAbsolutePath());
		try {
			if (pending != null) {
				yaml.loadFromString(pending.data());
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
		saveScheduled = false;
		onSave();
		final Supplier<String> source;
		if (isSerializedInBackground()) {
			// A copy of the values is cheap; turning them into YAML text is the slow part
			final YamlConfiguration snapshot = copyOf(config);
			source = snapshot::saveToString;
		} else {
			final String data = config.saveToString();
			source = () -> data;
		}

		final PendingWrite pending = new PendingWrite(source);
		if (WRITER.isShutdown()) {
			write(pending.data());
			return;
		}
		final String key = file.getAbsolutePath();
		PENDING.put(key, pending);
		WRITER.execute(() -> {
			// A newer save of this file is queued after this one and will write instead
			if (PENDING.get(key) != pending)
				return;
			write(pending.data());
			PENDING.remove(key, pending);
		});
	}

	/**
	 * Saves in a moment instead of right away, so many changes in a short time
	 * (a duel ending for 8 players, clicks in a menu) cost one save.
	 */
	public void saveLater(long delayTicks) {
		if (saveScheduled)
			return;
		saveScheduled = true;
		SchedulerUtil.runLater(delayTicks, () -> {
			if (saveScheduled)
				save();
		});
	}

	/**
	 * Whether the YAML text may be built on the writer thread. Only for files
	 * whose comments don't matter (a copy of the values loses them), and whose
	 * values are plain strings, numbers and lists.
	 */
	protected boolean isSerializedInBackground() {
		return false;
	}

	private static YamlConfiguration copyOf(FileConfiguration source) {
		YamlConfiguration copy = new YamlConfiguration();
		for (Map.Entry<String, Object> entry : source.getValues(true).entrySet()) {
			if (!(entry.getValue() instanceof ConfigurationSection))
				copy.set(entry.getKey(), entry.getValue());
		}
		return copy;
	}

	/**
	 * Save the configuration file immediately on the calling thread.
	 * Use on plugin disable.
	 */
	public void saveNow() {
		saveScheduled = false;
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
