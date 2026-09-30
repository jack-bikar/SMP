package games.coob.smp.menu;

import games.coob.smp.duel.DuelMobs;
import games.coob.smp.duel.DuelSide;
import games.coob.smp.duel.TeamDuelManager;
import games.coob.smp.duel.TeamLobby;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ItemCreator;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Leader picks the mobs that fight for one team: every allowed mob as a spawn
 * egg, left-click adds one, right-click removes one.
 */
public final class DuelMobsMenu extends SimpleMenu {

	/** Mob slots are the top five rows. */
	private static final int MOB_SLOTS = 45;
	private static final int SLOT_BACK = 45;
	private static final int SLOT_SUMMARY = 49;
	private static final int SLOT_CLEAR = 53;

	private final TeamLobby lobby;
	private final DuelSide side;
	private final Map<Integer, EntityType> mobSlots = new HashMap<>();

	public DuelMobsMenu(Player viewer, TeamLobby lobby, DuelSide side) {
		super(viewer, 54, "&8" + side.getDisplayName() + " team mobs");
		this.lobby = lobby;
		this.side = side;
		render();
	}

	private void render() {
		inventory.clear();
		mobSlots.clear();

		Map<EntityType, Integer> chosen = lobby.getMobs(side);
		List<EntityType> allowed = Settings.DuelSection.ALLOWED_MOBS;
		for (int i = 0; i < allowed.size() && i < MOB_SLOTS; i++) {
			EntityType type = allowed.get(i);
			int count = chosen.getOrDefault(type, 0);
			ItemStack item = ItemCreator.of(DuelMobs.icon(type),
					(count > 0 ? side.getColorCode() : "&f") + "&l" + DuelMobs.displayName(type),
					"", count > 0 ? "&7On this team: &f" + count : "&7Not on this team",
					"", "&eLeft-click: &7add one", "&eRight-click: &7remove one", "&eShift-click: &7remove all").make();
			item.setAmount(Math.max(1, Math.min(count, item.getMaxStackSize())));
			inventory.setItem(i, item);
			mobSlots.put(i, type);
		}

		inventory.setItem(SLOT_BACK, ItemCreator.of(Material.ARROW, "&7Back").make());

		List<String> summary = new ArrayList<>();
		summary.add("");
		for (Map.Entry<EntityType, Integer> entry : chosen.entrySet())
			summary.add("&7- &f" + entry.getValue() + "x " + DuelMobs.displayName(entry.getKey()));
		if (chosen.isEmpty())
			summary.add("&7No mobs yet.");
		summary.add("");
		summary.add("&7They attack the other team, players and mobs.");
		summary.add("&7The duel ends when all players of a team are out.");
		inventory.setItem(SLOT_SUMMARY, ItemCreator.of(side.getBanner(), side.getColorCode() + "&l" + side.getDisplayName()
				+ " mobs &7(" + lobby.getMobCount(side) + "/" + Settings.DuelSection.MAX_MOBS_PER_TEAM + ")",
				summary.toArray(new String[0])).make());

		inventory.setItem(SLOT_CLEAR, ItemCreator.of(Material.BARRIER, "&c&lRemove all mobs").make());
	}

	@Override
	protected void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
		TeamDuelManager manager = TeamDuelManager.getInstance();
		// They may have left, been removed or started meanwhile
		if (manager.getLobby(player) != lobby || !lobby.isLeader(player.getUniqueId())) {
			player.closeInventory();
			return;
		}

		EntityType type = mobSlots.get(slot);
		if (type != null) {
			int count = lobby.getMobs(side).getOrDefault(type, 0);
			int wanted = clickType.isShiftClick() ? 0 : clickType.isRightClick() ? count - 1 : count + 1;
			manager.setMobCount(player, side, type, wanted);
			render();
			return;
		}

		switch (slot) {
			case SLOT_BACK -> new TeamLobbyMenu(player, lobby).displayTo(player);
			case SLOT_CLEAR -> {
				manager.clearMobs(player, side);
				render();
			}
			default -> {
			}
		}
	}
}
