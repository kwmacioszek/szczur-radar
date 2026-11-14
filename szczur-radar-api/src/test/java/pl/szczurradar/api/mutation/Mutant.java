package pl.szczurradar.api.mutation;

/**
 * One mutant of PIT's {@code mutations.xml}.
 *
 * @param key its identity, the same across runs
 * @param lineNumber the line it mutates, only for the message: it is not part of the identity
 * @param status what PIT found
 */
record Mutant(MutantKey key, int lineNumber, MutantStatus status) {

	boolean surviving() {
		return status.surviving();
	}

}
