package games.coob.smp.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility for creating items using Adventure API
 */
public final class ItemCreator {

	private final Material material;
	private Component name;
	private List<Component> lore = new ArrayList<>();
	private String skullOwner;

	private ItemCreator(Material material) {
		this.material = material;
	}

	public static ItemCreator of(Material material, String name, String... lore) {
		ItemCreator creator = new ItemCreator(material);
		if (name != null && !name.isEmpty()) {
			creator.name = ColorUtil.toComponent(name);
		}
		if (lore != null && lore.length > 0) {
			for (String line : lore) {
				if (line != null && !line.isEmpty()) {
					creator.lore.add(ColorUtil.toComponent(line));
				}
			}
		}
		return creator;
	}

	public ItemCreator skullOwner(String owner) {
		this.skullOwner = owner;
		return this;
	}

	public ItemStack make() {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			if (name != null) {
				meta.displayName(name);
			}
			if (!lore.isEmpty()) {
				meta.lore(lore);
			}
			item.setItemMeta(meta);

			// Handle skull owner using modern API
			if (material == Material.PLAYER_HEAD && skullOwner != null && meta instanceof SkullMeta skullMeta) {
				// Use the modern API - get player profile
				@SuppressWarnings("deprecation")
				org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(skullOwner);
				skullMeta.setOwningPlayer(offlinePlayer);
				item.setItemMeta(skullMeta);
			}
		}
		return item;
	}
}
