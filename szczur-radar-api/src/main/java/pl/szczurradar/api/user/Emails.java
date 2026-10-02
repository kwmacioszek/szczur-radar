package pl.szczurradar.api.user;

import java.util.Locale;

/**
 * The single e-mail normalization, used by login and the CLI. Matches the
 * {@code ck_users_email_lower} constraint on {@code users.email}.
 */
public final class Emails {

	/** Longest accepted address (RFC 5321 path limit), enforced before any lookup. */
	public static final int MAX_EMAIL_LENGTH = 320;

	private Emails() {
	}

	/** Canonical form of an address: trimmed and lower-cased. */
	public static String normalize(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
