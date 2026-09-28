package games.coob.smp.model;

import games.coob.smp.config.ConfigFile;
import games.coob.smp.nickname.NicknameManager;
import games.coob.smp.util.ColorUtil;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Custom death messages from death-messages.yml. The lists are read once when
 * the file loads; picking a message is a map lookup and a random index.
 */
public final class DeathMessages extends ConfigFile {

	@Getter
	private static final DeathMessages instance = new DeathMessages();

	private Map<String, List<String>> messages;

	private DeathMessages() {
		super("death-messages.yml");
	}

	@Override
	protected void onLoad() {
		// Lists aren't re-added when missing, so deleting one really falls back to MOB / DEFAULT
		messages = new HashMap<>();
		for (String key : getConfig().getKeys(false)) {
			List<String> list = getConfig().getStringList(key);
			if (!list.isEmpty())
				messages.put(key.toUpperCase(Locale.ROOT), List.copyOf(list));
		}
	}

	/**
	 * Picks a message for this death, or null to keep the vanilla one.
	 */
	public Component build(Player player, Player killer, Entity mob, EntityDamageEvent.DamageCause cause) {
		String template;
		if (killer != null && !killer.equals(player)) {
			template = pick("PLAYER");
		} else if (mob != null && !(mob instanceof Player)) {
			template = pick(mobKey(mob.getType()));
			if (template == null)
				template = pick("MOB");
		} else {
			template = cause != null ? pick(causeKey(cause)) : null;
		}
		if (template == null)
			template = pick("DEFAULT");
		if (template == null)
			return null;

		// Names go in as components: nicknames keep their colours, and item/mob names can't inject formatting
		String text = "&7" + template;
		Component message = ColorUtil.toComponent(text);
		message = replaceName(message, "{player}", nameOf(player, NamedTextColor.YELLOW));
		if (text.contains("{killer}"))
			message = replaceName(message, "{killer}",
					killer != null ? nameOf(killer, NamedTextColor.RED) : Component.text("someone"));
		if (text.contains("{weapon}"))
			message = replaceName(message, "{weapon}", Component.text(weaponName(killer), NamedTextColor.GOLD));
		if (text.contains("{mob}"))
			message = replaceName(message, "{mob}", Component.text(mobName(mob), NamedTextColor.RED));
		return message;
	}

	private static Component nameOf(Player player, NamedTextColor fallbackColor) {
		try {
			return NicknameManager.getInstance().nameOf(player, fallbackColor);
		} catch (RuntimeException e) {
			return Component.text(player.getName(), fallbackColor);
		}
	}

	private static Component replaceName(Component message, String placeholder, Component name) {
		return message.replaceText(TextReplacementConfig.builder().matchLiteral(placeholder).replacement(name).build());
	}

	private String pick(String key) {
		List<String> list = messages.get(key);
		return list == null ? null : list.get(ThreadLocalRandom.current().nextInt(list.size()));
	}

	/** Mob type to message list; related mobs share a list. */
	private static String mobKey(EntityType type) {
		return switch (type.name()) {
			case "ZOMBIE", "HUSK", "ZOMBIE_VILLAGER" -> "ZOMBIE";
			case "SKELETON", "STRAY", "BOGGED" -> "SKELETON";
			case "SPIDER", "CAVE_SPIDER" -> "SPIDER";
			case "PIGLIN", "PIGLIN_BRUTE" -> "PIGLIN";
			case "SLIME", "MAGMA_CUBE" -> "SLIME";
			default -> type.name();
		};
	}

	/** Damage cause to message list; similar causes share a list. */
	private static String causeKey(EntityDamageEvent.DamageCause cause) {
		return switch (cause) {
			case FIRE, FIRE_TICK, CAMPFIRE -> "FIRE";
			case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "EXPLOSION";
			case MAGIC, POISON -> "MAGIC";
			case SONIC_BOOM -> "WARDEN";
			default -> cause.name();
		};
	}

	private static String mobName(Entity mob) {
		if (mob == null)
			return "mob";
		if (mob.customName() != null)
			return PlainTextComponentSerializer.plainText().serialize(mob.customName());
		return pretty(mob.getType().name());
	}

	/** e.g. "a Diamond Sword", "an Iron Axe", "their bare hands", or a renamed item's name. */
	private static String weaponName(Player killer) {
		if (killer == null)
			return "their bare hands";
		ItemStack item = killer.getInventory().getItemInMainHand();
		if (item.isEmpty())
			return "their bare hands";
		if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName())
			return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
		String name = pretty(item.getType().name());
		return ("AEIOU".indexOf(name.charAt(0)) >= 0 ? "an " : "a ") + name;
	}

	/** DIAMOND_SWORD -> Diamond Sword */
	private static String pretty(String enumName) {
		StringBuilder builder = new StringBuilder();
		for (String word : enumName.toLowerCase(Locale.ROOT).split("_")) {
			if (word.isEmpty())
				continue;
			if (!builder.isEmpty())
				builder.append(' ');
			builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return builder.toString();
	}
}
