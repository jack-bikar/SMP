package games.coob.smp.util;

import java.text.DecimalFormat;

/**
 * Utility class for math operations
 */
public final class MathUtil {

	private static final DecimalFormat TWO_DECIMALS = new DecimalFormat("#.##");

	private MathUtil() {
	}

	/**
	 * Format a number to two decimal places
	 *
	 * @param number The number to format
	 * @return Formatted string
	 */
	public static String formatTwoDigits(double number) {
		return TWO_DECIMALS.format(number);
	}
}
