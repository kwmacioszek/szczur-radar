package pl.szczurradar.api.cli;

/**
 * How an operator command ends. {@code main} exits the process with its {@link #code()}: 0 done,
 * 1 not done (refused or failed), 2 usage or configuration error. Railway's pre-deploy stops the
 * deploy on any non-zero code.
 */
public enum ExitCode {

	/** Done: migrations applied, the admin created, or the link issued and printed. */
	OK(0),

	/** Refused, with nothing written: e.g. an admin or the e-mail exists, no such account, passwords differ. */
	REFUSED(1),

	/** Failed while working, e.g. the database is unreachable or a migration fails. */
	FAILED(1),

	/**
	 * A usage or configuration error, found before any database work: a missing or unknown command or
	 * option, an invalid e-mail, missing settings, no interactive console, or an invalid setting that
	 * fails the start of the command's Spring context (an {@code InvalidSettingsException}, e.g.
	 * {@code LLM_TIMEOUT=120}).
	 */
	USAGE(2);

	private final int code;

	ExitCode(int code) {
		this.code = code;
	}

	/** The process exit code. */
	public int code() {
		return code;
	}

}
