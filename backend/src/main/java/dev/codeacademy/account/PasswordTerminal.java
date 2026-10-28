package dev.codeacademy.account;

import java.io.Console;
import java.util.Optional;

/** Where the console reset reads the new password without echoing it. */
public interface PasswordTerminal {

	/** Returns the typed characters, or null at end of input. The caller wipes the array. */
	char[] readPassword(String prompt);

	/**
	 * The process terminal, only when there is a real one. Since Java 22 a Console may exist without a
	 * terminal, and reading from it could echo the password, so both conditions are required.
	 */
	static Optional<PasswordTerminal> system() {
		Console console = System.console();
		if (console == null || !console.isTerminal()) return Optional.empty();
		return Optional.of(prompt -> console.readPassword("%s", prompt));
	}
}
