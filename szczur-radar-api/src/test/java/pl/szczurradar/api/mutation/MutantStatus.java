package pl.szczurradar.api.mutation;

/**
 * The status of a mutant in PIT's {@code mutations.xml}. {@link #SURVIVED} and {@link #NO_COVERAGE} are the
 * surviving ones the registry {@link AcceptedMutants} answers for; every other status is detected, as PIT counts
 * it. A status not listed here (PIT's {@code STARTED}, {@code NOT_STARTED}: a run that did not finish) fails the
 * reading of the report.
 */
enum MutantStatus {

	KILLED(false),
	TIMED_OUT(false),
	MEMORY_ERROR(false),
	RUN_ERROR(false),
	NON_VIABLE(false),
	SURVIVED(true),
	NO_COVERAGE(true);

	private final boolean surviving;

	MutantStatus(boolean surviving) {
		this.surviving = surviving;
	}

	/** True when no test detected the mutant: it needs a test of behavior or an entry in the registry. */
	boolean surviving() {
		return surviving;
	}

}
