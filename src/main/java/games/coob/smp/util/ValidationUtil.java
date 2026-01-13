package games.coob.smp.util;

/**
 * Utility class for validation
 */
public final class ValidationUtil {

	private ValidationUtil() {
	}

	/**
	 * Check if a condition is true, throw exception if not
	 *
	 * @param condition The condition to check
	 * @param message The error message
	 * @throws IllegalArgumentException if condition is false
	 */
	public static void checkBoolean(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

	/**
	 * Check if an object is not null
	 *
	 * @param object The object to check
	 * @param message The error message
	 * @throws IllegalArgumentException if object is null
	 */
	public static void checkNotNull(Object object, String message) {
		if (object == null) {
			throw new IllegalArgumentException(message);
		}
	}
}
