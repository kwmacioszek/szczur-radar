package pl.szczurradar.api.agents.assessor;

/** The first step of an assessment: either a model call to make or a result decided without one. */
public sealed interface Preparation permits Preparation.Prepared, Preparation.Decided {

	/** The entry needs exactly one model call. */
	record Prepared(PreparedRequest request) implements Preparation {
	}

	/** The result is known without a model call (the no-content prefilter, contract-v2.md §11). */
	record Decided(AssessmentResult result) implements Preparation {
	}

}
