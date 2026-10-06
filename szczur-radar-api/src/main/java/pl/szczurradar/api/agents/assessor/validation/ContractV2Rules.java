package pl.szczurradar.api.agents.assessor.validation;

import java.util.List;

import pl.szczurradar.api.agents.assessor.validation.rules.AnchorFromItemRule;
import pl.szczurradar.api.agents.assessor.validation.rules.ClaimTargetsExistRule;
import pl.szczurradar.api.agents.assessor.validation.rules.ContextualAnchorFromParentRule;
import pl.szczurradar.api.agents.assessor.validation.rules.DirectionalStanceAuthorClaimRule;
import pl.szczurradar.api.agents.assessor.validation.rules.EvidenceFromItemRule;
import pl.szczurradar.api.agents.assessor.validation.rules.IrrelevantHasNoContentRule;
import pl.szczurradar.api.agents.assessor.validation.rules.NoRepeatedReferencesRule;
import pl.szczurradar.api.agents.assessor.validation.rules.QuoteIsFragmentRule;
import pl.szczurradar.api.agents.assessor.validation.rules.QuoteNotEmptyRule;
import pl.szczurradar.api.agents.assessor.validation.rules.QuoteReferenceRule;
import pl.szczurradar.api.agents.assessor.validation.rules.StanceAssertivenessRule;
import pl.szczurradar.api.agents.assessor.validation.rules.StanceClaimCountRule;
import pl.szczurradar.api.agents.assessor.validation.rules.StanceClaimsCoverTargetRule;
import pl.szczurradar.api.agents.assessor.validation.rules.StanceClaimsExistRule;
import pl.szczurradar.api.agents.assessor.validation.rules.UniqueIdsRule;

/**
 * The rule set of contract V2 (§12), in run order. The quote rules run D5 → D7 → D6 so that each
 * quote gets one verdict: a missing field, an empty quote, or a quote that is not a fragment.
 */
public final class ContractV2Rules {

	private ContractV2Rules() {
	}

	public static List<ValidationRule> rules() {
		return List.of(new UniqueIdsRule(), new ClaimTargetsExistRule(), new StanceClaimsExistRule(),
				new NoRepeatedReferencesRule(), new QuoteReferenceRule(), new QuoteNotEmptyRule(),
				new QuoteIsFragmentRule(), new EvidenceFromItemRule(), new AnchorFromItemRule(),
				new ContextualAnchorFromParentRule(), new StanceClaimsCoverTargetRule(), new StanceAssertivenessRule(),
				new StanceClaimCountRule(), new DirectionalStanceAuthorClaimRule(), new IrrelevantHasNoContentRule());
	}

}
