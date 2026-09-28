package games.coob.smp.duel;

import lombok.Getter;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/**
 * The two sides of a duel. In a 1v1 each side has one player.
 */
@Getter
public enum DuelSide {

	RED("Red", "&c", NamedTextColor.RED, Material.RED_BANNER, Material.RED_STAINED_GLASS_PANE),
	BLUE("Blue", "&9", NamedTextColor.BLUE, Material.BLUE_BANNER, Material.BLUE_STAINED_GLASS_PANE);

	private final String displayName;
	private final String colorCode;
	private final NamedTextColor color;
	private final Material banner;
	private final Material pane;

	DuelSide(String displayName, String colorCode, NamedTextColor color, Material banner, Material pane) {
		this.displayName = displayName;
		this.colorCode = colorCode;
		this.color = color;
		this.banner = banner;
		this.pane = pane;
	}

	public DuelSide other() {
		return this == RED ? BLUE : RED;
	}

	/** e.g. "&cRed" */
	public String coloredName() {
		return colorCode + displayName;
	}
}
