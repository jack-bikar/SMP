package games.coob.smp.duel.kit;

import games.coob.smp.SMPPlugin;
import games.coob.smp.config.ConfigFile;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * The kits in duel-kits.yml. Server owners can change them or add their own;
 * anything that can't be read is skipped with a warning in the console.
 */
public final class DuelKits extends ConfigFile {

	@Getter
	private static final DuelKits instance = new DuelKits();

	/**
	 * Kits in the order they are written in the file. No initializer: the file is
	 * read in the super constructor, which runs before field initializers would.
	 */
	private Map<String, DuelKit> kits;

	private DuelKits() {
		super("duel-kits.yml");
	}

	@Override
	protected void onLoad() {
		if (getConfig().getInt("Version", 1) < 2 && addPyromancer())
			save();

		Map<String, DuelKit> loaded = new LinkedHashMap<>();
		ConfigurationSection section = getConfig().getConfigurationSection("Kits");
		if (section != null) {
			for (String id : section.getKeys(false)) {
				ConfigurationSection data = section.getConfigurationSection(id);
				if (data == null)
					continue;
				try {
					loaded.put(id.toLowerCase(Locale.ROOT), read(id, data));
				} catch (IllegalArgumentException e) {
					logger().warning("duel-kits.yml: skipping kit '" + id + "': " + e.getMessage());
				}
			}
		}
		if (loaded.isEmpty())
			logger().warning("duel-kits.yml has no usable kits: kit duels are unavailable.");
		kits = Collections.unmodifiableMap(loaded);
	}

	/**
	 * Files made before the Pyromancer existed get it (unless the server owner
	 * already has a kit by that name), and the other kits' "Strong vs / Weak vs"
	 * lines mention it where they are still the old defaults. Nothing else that
	 * may have been changed is touched.
	 *
	 * @return whether the file changed
	 */
	private boolean addPyromancer() {
		YamlConfiguration defaults = bundledDefaults();
		if (defaults == null)
			return false;

		if (!getConfig().isConfigurationSection("Kits.pyromancer")) {
			ConfigurationSection kit = defaults.getConfigurationSection("Kits.pyromancer");
			if (kit != null) {
				for (String key : kit.getKeys(true)) {
					if (!kit.isConfigurationSection(key))
						getConfig().set("Kits.pyromancer." + key, kit.get(key));
				}
				getConfig().setComments("Kits.pyromancer", defaults.getComments("Kits.pyromancer"));
			}
		}

		String[][] matchups = {
				{ "knight", "&cWeak vs: &7Berserker, Alchemist", "&cWeak vs: &7Berserker, Alchemist, Pyromancer" },
				{ "berserker", "&cWeak vs: &7Archer, Alchemist", "&cWeak vs: &7Archer, Alchemist, Pyromancer" },
				{ "archer", "&aStrong vs: &7Berserker, Alchemist", "&aStrong vs: &7Berserker, Alchemist, Pyromancer" },
				{ "scout", "&aStrong vs: &7Archer, Alchemist", "&aStrong vs: &7Archer, Alchemist, Pyromancer" } };
		for (String[] matchup : matchups) {
			String path = "Kits." + matchup[0] + ".Description";
			List<String> lines = new ArrayList<>(getConfig().getStringList(path));
			int index = lines.indexOf(matchup[1]);
			if (index >= 0) {
				lines.set(index, matchup[2]);
				getConfig().set(path, lines);
			}
		}

		getConfig().set("Version", 2);
		logger().info("duel-kits.yml: added the Pyromancer kit.");
		return true;
	}

	private static YamlConfiguration bundledDefaults() {
		InputStream stream = SMPPlugin.getInstance().getResource("duel-kits.yml");
		if (stream == null)
			return null;
		try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			return YamlConfiguration.loadConfiguration(reader);
		} catch (IOException e) {
			return null;
		}
	}

	public List<DuelKit> getKits() {
		return kits == null ? List.of() : List.copyOf(kits.values());
	}

	public DuelKit get(String id) {
		return id == null || kits == null ? null : kits.get(id.toLowerCase(Locale.ROOT));
	}

	public boolean isEmpty() {
		return kits == null || kits.isEmpty();
	}

	public DuelKit random() {
		List<DuelKit> all = getKits();
		return all.isEmpty() ? null : all.get(ThreadLocalRandom.current().nextInt(all.size()));
	}

	// -------------------------------------------------------------------------
	// Reading
	// -------------------------------------------------------------------------

	private static DuelKit read(String id, ConfigurationSection data) {
		List<ItemStack> items = new ArrayList<>();
		for (String spec : data.getStringList("Items"))
			items.add(item(spec));
		List<PotionEffect> effects = new ArrayList<>();
		for (String spec : data.getStringList("Effects"))
			effects.add(effect(spec));

		Material icon = Material.matchMaterial(data.getString("Icon", "iron_sword"));
		return new DuelKit(id, data.getString("Name", "&f" + DuelKit.pretty(id)), icon != null && icon.isItem() ? icon : Material.IRON_SWORD,
				data.getStringList("Description"), optionalItem(data, "Helmet"), optionalItem(data, "Chestplate"),
				optionalItem(data, "Leggings"), optionalItem(data, "Boots"), optionalItem(data, "Offhand"), items, effects);
	}

	private static ItemStack optionalItem(ConfigurationSection data, String path) {
		String spec = data.getString(path);
		return spec == null || spec.isBlank() ? null : item(spec);
	}

	/**
	 * "iron_sword 1 sharpness:1", "arrow 32", "splash_potion 2 potion:strong_healing".
	 */
	static ItemStack item(String spec) {
		String[] parts = spec.trim().split("\\s+");
		Material material = Material.matchMaterial(parts[0]);
		if (material == null || !material.isItem() || material.isAir())
			throw new IllegalArgumentException("unknown item '" + parts[0] + "'");

		int amount = 1;
		int next = 1;
		if (parts.length > 1 && parts[1].matches("\\d+")) {
			amount = Math.clamp(Integer.parseInt(parts[1]), 1, material.getMaxStackSize());
			next = 2;
		}
		ItemStack item = new ItemStack(material, amount);

		for (int i = next; i < parts.length; i++) {
			String[] option = parts[i].split(":", 2);
			if (option.length != 2)
				throw new IllegalArgumentException("'" + parts[i] + "' should be name:value");
			String key = option[0].toLowerCase(Locale.ROOT);
			String value = option[1].toLowerCase(Locale.ROOT);

			if (key.equals("potion")) {
				PotionType type = Registry.POTION.get(NamespacedKey.minecraft(value));
				if (type == null || !(item.getItemMeta() instanceof PotionMeta meta))
					throw new IllegalArgumentException("unknown potion '" + value + "' (or " + parts[0] + " isn't a potion)");
				meta.setBasePotionType(type);
				item.setItemMeta(meta);
				continue;
			}
			Enchantment enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
					.get(NamespacedKey.minecraft(key));
			if (enchantment == null)
				throw new IllegalArgumentException("unknown enchantment '" + key + "'");
			item.addUnsafeEnchantment(enchantment, Integer.parseInt(value));
		}
		return item;
	}

	/** "speed:1:30": Speed I for 30 seconds. */
	static PotionEffect effect(String spec) {
		String[] parts = spec.trim().toLowerCase(Locale.ROOT).split(":");
		PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(parts[0]));
		if (type == null || parts.length != 3)
			throw new IllegalArgumentException("effect '" + spec + "' should be effect:level:seconds");
		int level = Math.max(1, Integer.parseInt(parts[1]));
		int seconds = Math.max(1, Integer.parseInt(parts[2]));
		return new PotionEffect(type, seconds * 20, level - 1);
	}

	private static Logger logger() {
		return SMPPlugin.getInstance().getLogger();
	}
}
