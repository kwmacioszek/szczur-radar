package pl.szczurradar.api.auth.activation;

/**
 * What issuing a link returns ({@link ActivationLinks#createUser}, {@link ActivationLinks#resetPassword}):
 * the {@link IssuedLink}, or why nothing was written.
 */
public sealed interface IssueOutcome permits IssuedLink, IssueOutcome.Refused {

	/** No link was issued and nothing was written. */
	record Refused(Reason reason) implements IssueOutcome {
	}

	/** Why a link was not issued. */
	enum Reason {

		/** {@code createUser}: an account with this e-mail exists (a reset issues it a link). */
		EMAIL_TAKEN,

		/** {@code resetPassword}: there is no account with this e-mail. */
		NO_ACCOUNT

	}

}
