package pl.szczurradar.api.cli;

import java.io.Console;

import org.jspecify.annotations.Nullable;

/** Where operator commands read a password from: a hidden terminal prompt, or a script in tests. */
@FunctionalInterface
public interface PasswordPrompt {

	/** Reads one password without echoing it. */
	String read(String prompt);

	/** The terminal's hidden prompt, or {@code null} without an interactive console. */
	static @Nullable PasswordPrompt console() {
		Console console = System.console();
		if (console == null) {
			return null;
		}
		return prompt -> {
			char[] password = console.readPassword("%s", prompt);
			return password == null ? "" : new String(password);
		};
	}

}
