package games.coob.smp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Item names for chat and menu lore, e.g. "3x Diamond".
 */
public final class ItemText {

	private ItemText() {
	}

	/** "3x Diamond", or the item's custom name. */
	public static Component describe(ItemStack item) {
		return Component.text(item.getAmount() + "x ", NamedTextColor.GRAY)
				.append(item.effectiveName().colorIfAbsent(NamedTextColor.WHITE));
	}

	/** "3x Diamond, 1x Elytra", or "nothing". */
	public static Component list(List<ItemStack> items) {
		if (items.isEmpty())
			return Component.text("nothing", NamedTextColor.GRAY);
		List<Component> names = new ArrayList<>(items.size());
		for (ItemStack item : items)
			names.add(describe(item));
		return Component.join(JoinConfiguration.separator(Component.text(", ", NamedTextColor.GRAY)), names);
	}

	/** One lore line per stack, at most {@code maxLines}; the last one says how many more there are. */
	public static List<Component> lines(List<ItemStack> items, int maxLines) {
		List<Component> lines = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			if (i == maxLines - 1 && items.size() > maxLines) {
				lines.add(ColorUtil.toComponent("&8...and " + (items.size() - i) + " more"));
				break;
			}
			lines.add(Component.text("- ", NamedTextColor.DARK_GRAY).append(describe(items.get(i))));
		}
		return lines;
	}

	/** "64x diamond, 1x diamond_sword (Excalibur)" for the server log. */
	public static String plain(List<ItemStack> items) {
		if (items.isEmpty())
			return "nothing";
		StringJoiner joiner = new StringJoiner(", ");
		for (ItemStack item : items) {
			ItemMeta meta = item.getItemMeta();
			String name = meta != null && meta.hasCustomName()
					? " (" + PlainTextComponentSerializer.plainText().serialize(meta.customName()) + ")"
					: "";
			joiner.add(item.getAmount() + "x " + item.getType().getKey().getKey() + name);
		}
		return joiner.toString();
	}

	/** A copy of the item with lines added under its own lore, for menu icons. */
	public static ItemStack withLore(ItemStack item, List<Component> extra) {
		ItemStack copy = item.clone();
		ItemMeta meta = copy.getItemMeta();
		if (meta == null)
			return copy;
		List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
		lore.addAll(extra);
		meta.lore(lore);
		copy.setItemMeta(meta);
		return copy;
	}
}
