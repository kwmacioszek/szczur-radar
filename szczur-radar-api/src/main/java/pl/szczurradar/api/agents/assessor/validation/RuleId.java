package pl.szczurradar.api.agents.assessor.validation;

import static pl.szczurradar.api.agents.assessor.validation.FailureClass.DOMAIN_INVARIANT_VIOLATION;
import static pl.szczurradar.api.agents.assessor.validation.FailureClass.FABRICATED_EVIDENCE;
import static pl.szczurradar.api.agents.assessor.validation.FailureClass.INVALID_REFERENCE;

/**
 * The domain rules of contract-v2.md §12 and the failure class each one reports — the §12 table as
 * data. Each constant says what the rule requires; its strategy in {@code validation/rules/} says
 * only how it is checked.
 */
public enum RuleId {

	/** Target IDs unique among targets; claim IDs unique among claims. */
	D1(INVALID_REFERENCE),

	/** Every element of {@code claim.target_ids} names an existing target. */
	D2(INVALID_REFERENCE),

	/** Every element of {@code stance.claim_ids} names an existing claim. */
	D3(INVALID_REFERENCE),

	/** No repeats within {@code claim.target_ids} or within {@code stance.claim_ids}. */
	D4(INVALID_REFERENCE),

	/** A quote points to an existing document and a field that is not {@code null} (§10 pt 1). */
	D5(INVALID_REFERENCE),

	/** A quote after normalization is a contiguous fragment of its field after normalization (§10 pt 3). */
	D6(FABRICATED_EVIDENCE),

	/** A quote after normalization has at least one character (§10 pt 2); checked before D6. */
	D7(DOMAIN_INVARIANT_VIOLATION),

	/** {@code evidence.document = item}. */
	D8(DOMAIN_INVARIANT_VIOLATION),

	/** {@code reference = explicit | ambiguous | unresolved} ⇒ the anchor is from {@code item}. */
	D9(DOMAIN_INVARIANT_VIOLATION),

	/** {@code reference = contextual} ⇒ the anchor is from {@code parent_post} or {@code parent_comment}. */
	D10(DOMAIN_INVARIANT_VIOLATION),

	/** Every claim in {@code stance.claim_ids} has that target in its {@code target_ids}. */
	D11(DOMAIN_INVARIANT_VIOLATION),

	/** {@code assertiveness} as table §7.2 requires for the direction. */
	D12(DOMAIN_INVARIANT_VIOLATION),

	/** Presence and count of {@code claim_ids} as table §7.2 requires for the direction. */
	D13(DOMAIN_INVARIANT_VIOLATION),

	/** A directional stance ({@code bullish | bearish | neutral | mixed}) rests on a claim with {@code attribution = author}. */
	D14(DOMAIN_INVARIANT_VIOLATION),

	/** {@code relevance = not_investment_related} ⇒ {@code targets = []} and {@code claims = []}. */
	D15(DOMAIN_INVARIANT_VIOLATION);

	private final FailureClass failureClass;

	RuleId(FailureClass failureClass) {
		this.failureClass = failureClass;
	}

	public FailureClass failureClass() {
		return failureClass;
	}

	/** A violation of this rule at {@code path}. */
	public Violation violation(String path, String message) {
		return new Violation(failureClass, name(), path, message);
	}

}
