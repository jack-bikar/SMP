package games.coob.smp.nickname;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * The colours and gradients offered in the nickname menu.
 */
public final class NickStyles {

	private NickStyles() {
	}

	public record SolidColor(String name, NamedTextColor color, Material icon) {
	}

	/** An empty colour list means rainbow. */
	public record Gradient(String name, List<TextColor> colors, Material icon) {
		public boolean isRainbow() {
			return colors.isEmpty();
		}
	}

	public static final List<SolidColor> COLORS = List.of(
			new SolidColor("White", NamedTextColor.WHITE, Material.WHITE_DYE),
			new SolidColor("Light Grey", NamedTextColor.GRAY, Material.LIGHT_GRAY_DYE),
			new SolidColor("Dark Grey", NamedTextColor.DARK_GRAY, Material.GRAY_DYE),
			new SolidColor("Black", NamedTextColor.BLACK, Material.BLACK_DYE),
			new SolidColor("Red", NamedTextColor.RED, Material.RED_DYE),
			new SolidColor("Dark Red", NamedTextColor.DARK_RED, Material.REDSTONE),
			new SolidColor("Gold", NamedTextColor.GOLD, Material.ORANGE_DYE),
			new SolidColor("Yellow", NamedTextColor.YELLOW, Material.YELLOW_DYE),
			new SolidColor("Green", NamedTextColor.GREEN, Material.LIME_DYE),
			new SolidColor("Dark Green", NamedTextColor.DARK_GREEN, Material.GREEN_DYE),
			new SolidColor("Aqua", NamedTextColor.AQUA, Material.LIGHT_BLUE_DYE),
			new SolidColor("Dark Aqua", NamedTextColor.DARK_AQUA, Material.CYAN_DYE),
			new SolidColor("Blue", NamedTextColor.BLUE, Material.BLUE_DYE),
			new SolidColor("Dark Blue", NamedTextColor.DARK_BLUE, Material.LAPIS_LAZULI),
			new SolidColor("Pink", NamedTextColor.LIGHT_PURPLE, Material.PINK_DYE),
			new SolidColor("Purple", NamedTextColor.DARK_PURPLE, Material.PURPLE_DYE));

	public static final List<Gradient> GRADIENTS = List.of(
			gradient("Sunset", Material.ORANGE_TULIP, "#FF7E5F", "#FEB47B"),
			gradient("Fire", Material.BLAZE_POWDER, "#FFE259", "#FF7A00", "#FF1E00"),
			gradient("Ocean", Material.HEART_OF_THE_SEA, "#00C6FF", "#0072FF"),
			gradient("Toxic", Material.SLIME_BALL, "#A8FF78", "#1D976C"),
			gradient("Candy", Material.PINK_TULIP, "#FF6FD8", "#FFC3A0"),
			gradient("Galaxy", Material.AMETHYST_SHARD, "#8E2DE2", "#4A00E0", "#00D2FF"),
			gradient("Ice", Material.PACKED_ICE, "#E0FFFF", "#7FDBFF"),
			gradient("Royal", Material.GOLDEN_HELMET, "#FFD700", "#8A2BE2"),
			gradient("Blood", Material.RED_CANDLE, "#FF0000", "#5B0000"),
			gradient("Mint", Material.SEA_PICKLE, "#00F260", "#0575E6"),
			gradient("Peach", Material.PEONY, "#FFB88C", "#DE6262"),
			gradient("Gold", Material.GOLD_INGOT, "#FFF200", "#FF9D00"),
			gradient("Neon", Material.GLOW_INK_SAC, "#00FFA3", "#DC1FFF"),
			gradient("Mono", Material.QUARTZ, "#FFFFFF", "#6B6B6B"),
			new Gradient("Rainbow", List.of(), Material.PRISMARINE_CRYSTALS));

	private static Gradient gradient(String name, Material icon, String... hex) {
		return new Gradient(name, java.util.Arrays.stream(hex).map(TextColor::fromHexString).toList(), icon);
	}

	/**
	 * Reads a colour typed in a command: a name ("red", "dark_aqua", "pink") or
	 * a hex code ("#ff8800"). Null if not recognised.
	 */
	public static TextColor parseColor(String input) {
		String value = input.toLowerCase(Locale.ROOT).replace(' ', '_');
		if (value.startsWith("#"))
			return value.length() == 7 ? TextColor.fromHexString(value) : null;
		for (SolidColor color : COLORS) {
			if (color.name().toLowerCase(Locale.ROOT).replace(' ', '_').equals(value))
				return color.color();
		}
		return NamedTextColor.NAMES.value(value);
	}

	public static Gradient findGradient(String name) {
		for (Gradient gradient : GRADIENTS) {
			if (gradient.name().equalsIgnoreCase(name))
				return gradient;
		}
		return null;
	}
}
