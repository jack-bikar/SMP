package games.coob.smp.duel.model;

import lombok.Getter;

import java.util.UUID;

/**
 * Represents a pending duel request from one player to another.
 */
@Getter
public class DuelRequest {

	private final UUID challengerId;
	private final UUID targetId;
	private final String challengerName;
	private final String targetName;
	private final long timestamp;
	private final long expiryTime;

	public DuelRequest(UUID challengerId, String challengerName, UUID targetId, String targetName, int timeoutSeconds) {
		this.challengerId = challengerId;
		this.challengerName = challengerName;
		this.targetId = targetId;
		this.targetName = targetName;
		this.timestamp = System.currentTimeMillis();
		this.expiryTime = this.timestamp + (timeoutSeconds * 1000L);
	}

	/**
	 * Checks if this request has expired.
	 */
	public boolean isExpired() {
		return System.currentTimeMillis() > expiryTime;
	}

	/**
	 * Gets remaining time in seconds before expiry.
	 */
	public int getRemainingSeconds() {
		long remaining = expiryTime - System.currentTimeMillis();
		return remaining > 0 ? (int) (remaining / 1000) : 0;
	}
}
