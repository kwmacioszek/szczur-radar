package pl.szczurradar.api.mutation;

import java.util.Arrays;
import java.util.Optional;

/** The category a reason of an {@link AcceptedMutant} starts with. */
enum ReasonCategory {

	/** The behavior does not change: the reason says why. */
	EQUIVALENT("równoważny:"),

	/** A branch the input contract makes unreachable: the reason names the place that rules it out. */
	DEFENSIVE("obronny:"),

	/** The mutant changes only the text of a message that is no contract. */
	MESSAGE_TEXT("tekst:");

	private final String prefix;

	ReasonCategory(String prefix) {
		this.prefix = prefix;
	}

	String prefix() {
		return prefix;
	}

	/** The category {@code reason} starts with, if any. */
	static Optional<ReasonCategory> of(String reason) {
		return Arrays.stream(values()).filter(category -> reason.startsWith(category.prefix)).findFirst();
	}

}
