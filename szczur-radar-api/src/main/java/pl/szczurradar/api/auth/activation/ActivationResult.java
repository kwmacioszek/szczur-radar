package pl.szczurradar.api.auth.activation;

/** What setting a password through a link ({@link ActivationLinks#activate}) did. */
public enum ActivationResult {

	/** The password is set and the link is used up. */
	ACTIVATED,

	/** Unknown, used, expired or replaced by a newer link; nothing changed. */
	LINK_INVALID,

	/** The password breaks the policy ({@code auth/Passwords}); the link still works. */
	PASSWORD_POLICY

}
