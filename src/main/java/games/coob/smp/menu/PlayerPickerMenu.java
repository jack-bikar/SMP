package games.coob.smp.menu;

import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.ItemCreator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Paginated list of online players to pick from (duel challenges, team invites).
 */
public final class PlayerPickerMenu extends SimpleMenu {

	private static final int PER_PAGE = 45;
	private static final int SLOT_BACK = 45;
	private static final int SLOT_PREVIOUS = 48;
	private static final int SLOT_NEXT = 50;

	/** Stored by id and looked up on click, so relogged players are handled correctly. */
	private final List<UUID> players = new ArrayList<>();
	private final List<String> names = new ArrayList<>();
	private final String action;
	private final Consumer<Player> onPick;
	private final Runnable onBack;
	private int page;

	/**
	 * @param filter which online players to list (the viewer is always left out)
	 * @param action what clicking does, shown on each head (e.g. "Click to invite")
	 * @param onPick called with the chosen player (still online)
	 * @param onBack called by the back button, or null for no back button
	 */
	public PlayerPickerMenu(Player viewer, String title, Predicate<Player> filter, String action,
			Consumer<Player> onPick, Runnable onBack) {
		super(viewer, 54, title);
		this.action = action;
		this.onPick = onPick;
		this.onBack = onBack;
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (!player.equals(viewer) && filter.test(player)) {
				players.add(player.getUniqueId());
				names.add(player.getName());
			}
		}
		render();
	}

	private void render() {
		inventory.clear();
		int start = page * PER_PAGE;
		for (int i = start; i < Math.min(start + PER_PAGE, players.size()); i++) {
			inventory.setItem(i - start, ItemCreator.of(Material.PLAYER_HEAD, "&b&l" + names.get(i), "",
					"&e" + action).skullOwner(Bukkit.getOfflinePlayer(players.get(i))).make());
		}

		if (players.isEmpty())
			inventory.setItem(22, ItemCreator.of(Material.BARRIER, "&cNobody available",
					"", "&7No other online players can be picked right now.").make());

		if (onBack != null)
			inventory.setItem(SLOT_BACK, ItemCreator.of(Material.ARROW, "&c&lBack").make());
		if (page > 0)
			inventory.setItem(SLOT_PREVIOUS, ItemCreator.of(Material.ARROW, "&a&lPrevious page").make());
		if (start + PER_PAGE < players.size())
			inventory.setItem(SLOT_NEXT, ItemCreator.of(Material.ARROW, "&a&lNext page").make());
	}

	@Override
	protected void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
		if (slot == SLOT_BACK && onBack != null) {
			onBack.run();
		} else if (slot == SLOT_PREVIOUS && page > 0) {
			page--;
			render();
		} else if (slot == SLOT_NEXT && (page + 1) * PER_PAGE < players.size()) {
			page++;
			render();
		} else if (slot < PER_PAGE) {
			int index = page * PER_PAGE + slot;
			if (index >= players.size())
				return;
			Player picked = Bukkit.getPlayer(players.get(index));
			if (picked == null) {
				ColorUtil.sendMessage(player, "&c" + names.get(index) + " is no longer online.");
				return;
			}
			onPick.accept(picked);
		}
	}
}
