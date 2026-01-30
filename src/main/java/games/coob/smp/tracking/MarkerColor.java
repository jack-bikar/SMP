package games.coob.smp.tracking;

import org.bukkit.Material;

/**
 * Marker colors for the locator bar, mapped to wool colors.
 */
public enum MarkerColor {
    WHITE(Material.WHITE_WOOL, "&fWhite", 0xFFFFFF),
    ORANGE(Material.ORANGE_WOOL, "&6Orange", 0xD87F33),
    MAGENTA(Material.MAGENTA_WOOL, "&dMagenta", 0xB24CD8),
    LIGHT_BLUE(Material.LIGHT_BLUE_WOOL, "&bLight Blue", 0x6699D8),
    YELLOW(Material.YELLOW_WOOL, "&eYellow", 0xE5E533),
    LIME(Material.LIME_WOOL, "&aLime", 0x7FCC19),
    PINK(Material.PINK_WOOL, "&dPink", 0xF27FA5),
    GRAY(Material.GRAY_WOOL, "&8Gray", 0x4C4C4C),
    LIGHT_GRAY(Material.LIGHT_GRAY_WOOL, "&7Light Gray", 0x999999),
    CYAN(Material.CYAN_WOOL, "&3Cyan", 0x4C7F99),
    PURPLE(Material.PURPLE_WOOL, "&5Purple", 0x7F3FB2),
    BLUE(Material.BLUE_WOOL, "&9Blue", 0x334CB2),
    BROWN(Material.BROWN_WOOL, "&6Brown", 0x664C33),
    GREEN(Material.GREEN_WOOL, "&2Green", 0x667F33),
    RED(Material.RED_WOOL, "&cRed", 0x993333),
    BLACK(Material.BLACK_WOOL, "&0Black", 0x191919),
    // Special color for death tracking (always dark red)
    DARK_RED(Material.RED_WOOL, "&4Dark Red", 0x660000);

    private final Material woolMaterial;
    private final String displayName;
    private final int rgb;

    MarkerColor(Material woolMaterial, String displayName, int rgb) {
        this.woolMaterial = woolMaterial;
        this.displayName = displayName;
        this.rgb = rgb;
    }

    public Material getWoolMaterial() {
        return woolMaterial;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getRgb() {
        return rgb;
    }

    public int getRed() {
        return (rgb >> 16) & 0xFF;
    }

    public int getGreen() {
        return (rgb >> 8) & 0xFF;
    }

    public int getBlue() {
        return rgb & 0xFF;
    }

    /**
     * Get a MarkerColor from a wool material.
     */
    public static MarkerColor fromWool(Material wool) {
        for (MarkerColor color : values()) {
            if (color.woolMaterial == wool && color != DARK_RED) {
                return color;
            }
        }
        return WHITE;
    }

    /**
     * Get the default color for a new tracked player (cycles through colors).
     */
    public static MarkerColor getDefault(int index) {
        MarkerColor[] playerColors = {
            WHITE, ORANGE, MAGENTA, LIGHT_BLUE, YELLOW, LIME, PINK,
            CYAN, PURPLE, BLUE, GREEN, RED
        };
        return playerColors[index % playerColors.length];
    }
}
