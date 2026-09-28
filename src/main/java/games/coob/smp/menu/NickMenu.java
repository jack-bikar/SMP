package games.coob.smp.menu;

import games.coob.smp.nickname.NickStyles;
import games.coob.smp.nickname.Nickname;
import games.coob.smp.nickname.NicknameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /nick: pick a colour, gradient and style; the preview and your name update
 * as you click.
 */
public final class NickMenu extends SimpleMenu {

	private static final int SLOT_PREVIEW = 4;
	private static final int FIRST_COLOR_SLOT = 9;
	private static final int FIRST_GRADIENT_SLOT = 27;
	private static final int SLOT_CHANGE_NAME = 45;
	private static final int SLOT_BOLD = 47;
	private static final int SLOT_ITALIC = 48;
	private static final int SLOT_UNDERLINED = 49;
	private static final int SLOT_CUSTOM = 51;
	private static final int SLOT_RESET = 53;

	public NickMenu(Player viewer) {
		super(viewer, 54, "&8Nickname");
		NicknameManager.getInstance().cancelPrompt(viewer);
		render();
	}

	private void render() {
		inventory.clear();
		NicknameManager manager = NicknameManager.getInstance();
		Nickname nickname = manager.peek(viewer);
		Component preview = manager.preview(viewer);

		inventory.setItem(SLOT_PREVIEW, item(Material.NAME_TAG, preview,
				gray("This is how your name looks"),
				gray("in chat, the tab list and messages.")));

		for (int i = 0; i < NickStyles.COLORS.size(); i++) {
			NickStyles.SolidColor color = NickStyles.COLORS.get(i);
			boolean selected = !nickname.isRainbow() && nickname.getColors().size() == 1
					&& nickname.getColors().getFirst().value() == color.color().value();
			ItemStack stack = item(color.icon(), Component.text(color.name(), color.color()),
					gray("Click to colour your name"));
			inventory.setItem(FIRST_COLOR_SLOT + i, glow(stack, selected));
		}

		for (int i = 0; i < NickStyles.GRADIENTS.size(); i++) {
			NickStyles.Gradient gradient = NickStyles.GRADIENTS.get(i);
			Nickname sample = new Nickname();
			sample.setText(gradient.name());
			sample.setRainbow(gradient.isRainbow());
			sample.setColors(new ArrayList<>(gradient.colors()));
			boolean selected = gradient.isRainbow() ? nickname.isRainbow()
					: !nickname.isRainbow() && sameColors(nickname.getColors(), gradient.colors());
			ItemStack stack = item(gradient.icon(), sample.render(gradient.name()), gray("Click to use this gradient"));
			inventory.setItem(FIRST_GRADIENT_SLOT + i, glow(stack, selected));
		}

		inventory.setItem(SLOT_CHANGE_NAME, item(Material.WRITABLE_BOOK, Component.text("Change name", NamedTextColor.GREEN),
				gray("Type a new nickname in chat."),
				gray("Or use /nick <name>")));
		inventory.setItem(SLOT_BOLD, toggle(Material.IRON_INGOT, "Bold", nickname.isBold()));
		inventory.setItem(SLOT_ITALIC, toggle(Material.FEATHER, "Italic", nickname.isItalic()));
		inventory.setItem(SLOT_UNDERLINED, toggle(Material.STRING, "Underline", nickname.isUnderlined()));
		inventory.setItem(SLOT_CUSTOM, item(Material.MAP, Component.text("Your own colours", NamedTextColor.AQUA),
				gray("/nick color <#hex or name>"),
				gray("/nick gradient <colour> <colour> [more]"),
				gray("e.g. /nick gradient #ff0000 #0000ff")));
		inventory.setItem(SLOT_RESET, item(Material.BARRIER, Component.text("Reset", NamedTextColor.RED),
				gray("Back to your normal name.")));
	}

	@Override
	protected void onMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
		NicknameManager manager = NicknameManager.getInstance();
		Nickname nickname = manager.get(player);

		int colorIndex = slot - FIRST_COLOR_SLOT;
		int gradientIndex = slot - FIRST_GRADIENT_SLOT;

		if (colorIndex >= 0 && colorIndex < NickStyles.COLORS.size()) {
			nickname.setRainbow(false);
			nickname.setColors(new ArrayList<>(List.of(NickStyles.COLORS.get(colorIndex).color())));
		} else if (gradientIndex >= 0 && gradientIndex < NickStyles.GRADIENTS.size()) {
			NickStyles.Gradient gradient = NickStyles.GRADIENTS.get(gradientIndex);
			nickname.setRainbow(gradient.isRainbow());
			nickname.setColors(new ArrayList<>(gradient.colors()));
		} else {
			switch (slot) {
				case SLOT_CHANGE_NAME -> {
					manager.promptForText(player);
					return;
				}
				case SLOT_BOLD -> nickname.setBold(!nickname.isBold());
				case SLOT_ITALIC -> nickname.setItalic(!nickname.isItalic());
				case SLOT_UNDERLINED -> nickname.setUnderlined(!nickname.isUnderlined());
				case SLOT_RESET -> {
					manager.reset(player.getUniqueId());
					render();
					return;
				}
				default -> {
					return;
				}
			}
		}

		manager.update(player);
		render();
	}

	private static ItemStack toggle(Material icon, String name, boolean on) {
		Component title = Component.text(name + ": ", NamedTextColor.YELLOW)
				.append(on ? Component.text("ON", NamedTextColor.GREEN) : Component.text("OFF", NamedTextColor.RED));
		return glow(item(icon, title, gray("Click to turn " + (on ? "off" : "on"))), on);
	}

	private static ItemStack item(Material material, Component name, Component... lore) {
		ItemStack stack = new ItemStack(material);
		ItemMeta meta = stack.getItemMeta();
		// Item names are italic by default; keep them upright like chat
		meta.displayName(name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
		List<Component> lines = new ArrayList<>();
		for (Component line : lore)
			lines.add(line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
		meta.lore(lines);
		stack.setItemMeta(meta);
		return stack;
	}

	private static ItemStack glow(ItemStack stack, boolean on) {
		if (on) {
			ItemMeta meta = stack.getItemMeta();
			meta.setEnchantmentGlintOverride(true);
			stack.setItemMeta(meta);
		}
		return stack;
	}

	private static boolean sameColors(List<TextColor> first, List<TextColor> second) {
		if (first.size() != second.size())
			return false;
		for (int i = 0; i < first.size(); i++) {
			if (first.get(i).value() != second.get(i).value())
				return false;
		}
		return true;
	}

	private static Component gray(String text) {
		return Component.text(text, NamedTextColor.GRAY);
	}
}
