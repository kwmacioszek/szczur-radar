package pl.szczurradar.api.agents.assessor;

import java.time.OffsetDateTime;
import java.util.List;

import pl.szczurradar.api.agents.assessor.contract.Anchor;
import pl.szczurradar.api.agents.assessor.contract.Assertiveness;
import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Attribution;
import pl.szczurradar.api.agents.assessor.contract.Claim;
import pl.szczurradar.api.agents.assessor.contract.ClaimKind;
import pl.szczurradar.api.agents.assessor.contract.Context;
import pl.szczurradar.api.agents.assessor.contract.Direction;
import pl.szczurradar.api.agents.assessor.contract.Document;
import pl.szczurradar.api.agents.assessor.contract.Evidence;
import pl.szczurradar.api.agents.assessor.contract.Field;
import pl.szczurradar.api.agents.assessor.contract.Item;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.agents.assessor.contract.Reference;
import pl.szczurradar.api.agents.assessor.contract.Relevance;
import pl.szczurradar.api.agents.assessor.contract.Stance;
import pl.szczurradar.api.agents.assessor.contract.Target;
import pl.szczurradar.api.agents.assessor.contract.TargetKind;

/**
 * Builders for contract inputs and outputs in tests. {@link #BODY} and {@link #validOutput()} form a
 * valid pair; tests break one rule at a time from there.
 */
public final class ContractFixtures {

	/** Two targets: XTB with the author's bullish stance, CDR with a reported opinion only. */
	public static final String BODY = "XTB po wynikach poleci w górę, dokupuję.\nAnalityk pisze, że CDR spadnie.\n#gielda ";

	public static final OffsetDateTime PUBLISHED_AT = OffsetDateTime.parse("2026-09-29T21:00:59+02:00");

	private ContractFixtures() {
	}

	public static AssessmentInputV1 post(String body) {
		return post(null, body);
	}

	public static AssessmentInputV1 post(String title, String body) {
		return AssessmentInputV1.of(new Item(ItemType.POST, PUBLISHED_AT, List.of("gielda"), title, body), Context.NONE);
	}

	public static AssessmentInputV1 validInput() {
		return post(BODY);
	}

	public static AssessmentOutputV2 validOutput() {
		return investment(
				List.of(target("t1", "XTB", Direction.BULLISH, Assertiveness.UNHEDGED, "c1"),
						target("t2", "CDR", Direction.NOT_EXPRESSED, null)),
				List.of(claim("c1", Attribution.AUTHOR, "XTB po wynikach poleci w górę", "t1"),
						claim("c2", Attribution.REPORTED, "Analityk pisze, że CDR spadnie", "t2")));
	}

	public static AssessmentOutputV2 investment(List<Target> targets, List<Claim> claims) {
		return new AssessmentOutputV2(Relevance.INVESTMENT_RELATED, List.of(), targets, claims, List.of());
	}

	/** An explicit company target anchored in {@code item.body}. */
	public static Target target(String id, String anchor, Direction direction, Assertiveness assertiveness,
			String... claimIds) {
		return target(id, new Anchor(Document.ITEM, Field.BODY, anchor), Reference.EXPLICIT, direction, assertiveness,
				claimIds);
	}

	public static Target target(String id, Anchor anchor, Reference reference, Direction direction,
			Assertiveness assertiveness, String... claimIds) {
		return new Target(id, TargetKind.COMPANY, anchor, new Stance(direction, assertiveness, List.of(claimIds)),
				reference);
	}

	/** An investment thesis quoting {@code item.body}. */
	public static Claim claim(String id, Attribution attribution, String quote, String... targetIds) {
		return claim(id, attribution, new Evidence(Document.ITEM, Field.BODY, quote), targetIds);
	}

	public static Claim claim(String id, Attribution attribution, Evidence evidence, String... targetIds) {
		return new Claim(id, ClaimKind.INVESTMENT_THESIS, attribution, List.of(targetIds), evidence,
				"Teza testowa.");
	}

}
