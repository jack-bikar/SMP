package games.coob.smp.duel.kit;

import games.coob.smp.SMPPlugin;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A set of gear players can pick for a duel (see duel-kits.yml). Every item a
 * kit gives is tagged, so it can be recognised and removed anywhere outside the
 * duel it belongs to.
 */
@Getter
public final class DuelKit {

	/** Marks items given by a kit. */
	private static final NamespacedKey KIT_ITEM = new NamespacedKey(SMPPlugin.getInstance(), "duel_kit_item");

	private final String id;
	private final String displayName;
	private final Material icon;
	private final List<String> description;
	private final ItemStack helmet;
	private final ItemStack chestplate;
	private final ItemStack leggings;
	private final ItemStack boots;
	private final ItemStack offhand;
	private final List<ItemStack> items;
	private final List<PotionEffect> effects;

	DuelKit(String id, String displayName, Material icon, List<String> description, ItemStack helmet,
			ItemStack chestplate, ItemStack leggings, ItemStack boots, ItemStack offhand, List<ItemStack> items,
			List<PotionEffect> effects) {
		this.id = id;
		this.displayName = displayName;
		this.icon = icon;
		this.description = List.copyOf(description);
		this.helmet = tag(helmet);
		this.chestplate = tag(chestplate);
		this.leggings = tag(leggings);
		this.boots = tag(boots);
		this.offhand = tag(offhand);
		List<ItemStack> tagged = new ArrayList<>(items.size());
		for (ItemStack item : items)
			tagged.add(tag(item));
		this.items = Collections.unmodifiableList(tagged);
		this.effects = List.copyOf(effects);
	}

	/**
	 * Replaces everything the player has with this kit. Their own gear must have
	 * been stored first (see {@link KitStash}).
	 */
	public void apply(Player player) {
		PlayerInventory inventory = player.getInventory();
		inventory.clear();
		inventory.setHelmet(copy(helmet));
		inventory.setChestplate(copy(chestplate));
		inventory.setLeggings(copy(leggings));
		inventory.setBoots(copy(boots));
		inventory.setItemInOffHand(copy(offhand));
		for (int i = 0; i < items.size() && i < 36; i++)
			inventory.setItem(i, items.get(i).clone());

		// Everyone starts clean; the kit's own effects come when the fight starts (giveEffects)
		for (PotionEffect effect : player.getActivePotionEffects())
			player.removePotionEffect(effect.getType());
		player.setLevel(0);
		player.setExp(0);
	}

	/** The kit's effects, given when the fight starts so the countdown doesn't use them up. */
	public void giveEffects(Player player) {
		for (PotionEffect effect : effects)
			player.addPotionEffect(effect);
	}

	/** One line per piece of gear, for the kit menu. */
	public List<String> describeContents() {
		List<String> lines = new ArrayList<>();
		List<String> armour = new ArrayList<>();
		for (ItemStack piece : new ItemStack[] { helmet, chestplate, leggings, boots }) {
			if (piece != null)
				armour.add(nameOf(piece));
		}
		if (!armour.isEmpty())
			lines.add("&7Armour: &f" + String.join(", ", armour));
		if (offhand != null)
			lines.add("&7Off hand: &f" + nameOf(offhand));
		for (ItemStack item : items)
			lines.add("&7- &f" + (item.getAmount() > 1 ? item.getAmount() + "x " : "") + nameOf(item));
		for (PotionEffect effect : effects)
			lines.add("&7- &f" + pretty(effect.getType().getKey().getKey()) + " " + roman(effect.getAmplifier() + 1)
					+ " (" + effect.getDuration() / 20 + "s)");
		return lines;
	}

	// -------------------------------------------------------------------------
	// Kit items
	// -------------------------------------------------------------------------

	/** Whether the item came from a kit. */
	public static boolean isKitItem(ItemStack item) {
		return item != null && !item.isEmpty() && item.hasItemMeta()
				&& item.getItemMeta().getPersistentDataContainer().has(KIT_ITEM, PersistentDataType.BYTE);
	}

	private static ItemStack tag(ItemStack item) {
		if (item == null || item.isEmpty())
			return null;
		ItemStack tagged = item.clone();
		ItemMeta meta = tagged.getItemMeta();
		if (meta != null) {
			meta.getPersistentDataContainer().set(KIT_ITEM, PersistentDataType.BYTE, (byte) 1);
			tagged.setItemMeta(meta);
		}
		return tagged;
	}

	private static ItemStack copy(ItemStack item) {
		return item != null ? item.clone() : null;
	}

	/** e.g. "Iron Sword (Sharpness I)" or "Splash Potion of Strong Healing". */
	private static String nameOf(ItemStack item) {
		String name = pretty(item.getType().getKey().getKey());
		if (item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta potion && potion.getBasePotionType() != null)
			name += " of " + pretty(potion.getBasePotionType().getKey().getKey());
		List<String> enchantments = new ArrayList<>();
		for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet())
			enchantments.add(pretty(entry.getKey().getKey().getKey()) + " " + roman(entry.getValue()));
		return enchantments.isEmpty() ? name : name + " (" + String.join(", ", enchantments) + ")";
	}

	static String pretty(String key) {
		StringBuilder name = new StringBuilder();
		for (String word : key.toLowerCase(Locale.ROOT).split("_")) {
			if (word.isEmpty())
				continue;
			if (!name.isEmpty())
				name.append(' ');
			name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return name.toString();
	}

	private static String roman(int number) {
		return switch (number) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> String.valueOf(number);
		};
	}
}
