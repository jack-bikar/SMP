package games.coob.smp.menu;

import games.coob.smp.duel.ActiveDuel;
import games.coob.smp.duel.DuelManager;
import games.coob.smp.duel.kit.DuelKit;
import games.coob.smp.duel.kit.DuelKits;
import games.coob.smp.util.ItemCreator;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Picking a kit before a kit duel. The pick can be changed until the fight's
 * countdown starts; anyone who hasn't picked by then gets a random kit.
 */
public final class DuelKitMenu extends SimpleMenu {

	private static final int SLOT_INFO = 4;

	@Getter
	private final ActiveDuel duel;
	private final List<DuelKit> kits;
	private final Map<Integer, DuelKit> kitSlots = new HashMap<>();
	private final int randomSlot;

	public DuelKitMenu(Player viewer, ActiveDuel duel) {
		super(viewer, DuelKits.getInstance().getKits().size() <= 7 ? 27 : 45, "&8Pick your kit");
		this.duel = duel;
		this.kits = DuelKits.getInstance().getKits();
		this.randomSlot = inventory.getSize() == 27 ? 22 : 40;
		render();
	}

	public void render() {
		inventory.clear();
		kitSlots.clear();
		DuelKit chosen = duel.getKitChoice(viewer);

		inventory.setItem(SLOT_INFO, ItemCreator.of(Material.CLOCK, "&e&lPick your kit",
				"", "&7Your own items are kept safe", "&7and given back after the duel.",
				"", "&7No pick in time: a random kit.", "&7Reopen with &e/duel kit&7.").make());

		// Centred in the middle row when they fit, otherwise two rows
		int start = kits.size() <= 7 ? 9 + (9 - kits.size()) / 2 : 9;
		for (int i = 0; i < kits.size() && start + i < randomSlot - 4; i++) {
			DuelKit kit = kits.get(i);
			boolean mine = kit == chosen;
			List<String> lore = new ArrayList<>();
			lore.add("");
			lore.addAll(kit.getDescription());
			lore.add("");
			lore.addAll(kit.describeContents());
			lore.add("");
			lore.add(mine ? "&aYour kit" : "&eClick to pick");

			ItemStack item = ItemCreator.of(kit.getIcon(), kit.getDisplayName(), lore.toArray(new String[0])).make();
			if (mine)
				glow(item);
			inventory.setItem(start + i, item);
			kitSlots.put(start + i, kit);
		}

		inventory.setItem(randomSlot, ItemCreator.of(Material.COMPARATOR, "&f&lRandom kit",
				"", "&7Let chance decide.", "", "&eClick to get a random kit").make());
	}

	private static void glow(ItemStack item) {
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			meta.setEnchantmentGlintOverride(true);
			item.setItemMeta(meta);
		}
	}

	@Override
	protected void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
		// The duel may have moved on (fight started, cancelled)
		if (DuelManager.getInstance().getActiveDuel(player) != duel || !duel.isChoosingKits()) {
			player.closeInventory();
			return;
		}

		DuelKit kit = slot == randomSlot ? DuelKits.getInstance().random() : kitSlots.get(slot);
		if (kit != null && duel.chooseKit(player, kit))
			render();
	}
}
