package pl.szczurradar.api.agents.assessor.validation;

import pl.szczurradar.api.agents.assessor.contract.Assertiveness;
import pl.szczurradar.api.agents.assessor.contract.Direction;

/**
 * Table §7.2 of contract-v2.md as data: for each {@code direction}, whether {@code assertiveness}
 * is required or must be {@code null}, whether {@code claim_ids} are required, allowed or forbidden,
 * and whether an author's claim is required. Rules D12–D14 read only from here.
 */
public enum StanceTable {

	BULLISH(Direction.BULLISH, AssertivenessPresence.REQUIRED, ClaimIdsPresence.REQUIRED, true),
	BEARISH(Direction.BEARISH, AssertivenessPresence.REQUIRED, ClaimIdsPresence.REQUIRED, true),
	NEUTRAL(Direction.NEUTRAL, AssertivenessPresence.REQUIRED, ClaimIdsPresence.REQUIRED, true),
	MIXED(Direction.MIXED, AssertivenessPresence.NULL, ClaimIdsPresence.REQUIRED, true),
	UNCLEAR(Direction.UNCLEAR, AssertivenessPresence.NULL, ClaimIdsPresence.ALLOWED, false),
	NOT_EXPRESSED(Direction.NOT_EXPRESSED, AssertivenessPresence.NULL, ClaimIdsPresence.FORBIDDEN, false);

	/** Upper bound of {@code stance.claim_ids} (§9). */
	public static final int MAX_CLAIM_IDS = 4;

	private final Direction direction;

	private final AssertivenessPresence assertiveness;

	private final ClaimIdsPresence claimIds;

	private final boolean authorClaimRequired;

	StanceTable(Direction direction, AssertivenessPresence assertiveness, ClaimIdsPresence claimIds,
			boolean authorClaimRequired) {
		this.direction = direction;
		this.assertiveness = assertiveness;
		this.claimIds = claimIds;
		this.authorClaimRequired = authorClaimRequired;
	}

	/** The row of {@code direction}. */
	public static StanceTable of(Direction direction) {
		for (StanceTable row : values()) {
			if (row.direction == direction) {
				return row;
			}
		}
		throw new IllegalArgumentException("no table §7.2 row for " + direction);
	}

	public AssertivenessPresence assertiveness() {
		return assertiveness;
	}

	public ClaimIdsPresence claimIds() {
		return claimIds;
	}

	public boolean authorClaimRequired() {
		return authorClaimRequired;
	}

	/** Whether {@code value} is a permitted {@code assertiveness} for this direction (D12). */
	public boolean allowsAssertiveness(Assertiveness value) {
		return switch (assertiveness) {
			case REQUIRED -> value != null;
			case NULL -> value == null;
		};
	}

	/** Whether {@code count} claim IDs are permitted for this direction (D13). */
	public boolean allowsClaimIdCount(int count) {
		return switch (claimIds) {
			case REQUIRED -> count >= 1 && count <= MAX_CLAIM_IDS;
			case ALLOWED -> count >= 0 && count <= MAX_CLAIM_IDS;
			case FORBIDDEN -> count == 0;
		};
	}

	/** {@code assertiveness} column: {@code hedged | unhedged} required, or {@code null}. */
	public enum AssertivenessPresence {
		REQUIRED, NULL
	}

	/** {@code claim_ids} column: required (1–4), allowed (0–4) or forbidden ({@code []}). */
	public enum ClaimIdsPresence {
		REQUIRED, ALLOWED, FORBIDDEN
	}

}
