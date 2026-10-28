package dev.codeacademy.account;

import java.io.PrintWriter;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Local console recovery: the password is typed twice in a real terminal, never passed as an argument.
 * Messages are fixed strings and never include the password.
 */
@Component
public class PasswordResetCommand {

	static final String NO_TERMINAL = "Esta herramienta necesita una terminal interactiva para pedir la contraseña "
			+ "sin mostrarla. Ejecutala con java -jar desde una terminal. No se cambió nada.";
	static final String UNKNOWN_USER = "No existe el usuario indicado. No se cambió nada.";
	static final String NO_INPUT = "No se recibió la contraseña. No se cambió nada.";
	static final String MISMATCH = "Las contraseñas no coinciden. No se cambió nada.";
	static final String INVALID_PASSWORD = "La contraseña debe tener entre 15 y 128 caracteres "
			+ "(puntos de código Unicode). No se cambió nada.";
	static final String SUCCESS = "Contraseña actualizada. Las sesiones abiertas de este usuario se cerrarán "
			+ "en su próxima solicitud.";

	private static final String PROMPT = "Nueva contraseña: ";
	private static final String CONFIRMATION_PROMPT = "Repetí la nueva contraseña: ";

	private final AccountRepository accounts;
	private final PasswordEncoder passwordEncoder;

	PasswordResetCommand(AccountRepository accounts, PasswordEncoder passwordEncoder) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
	}

	/** Exit code 1 without asking anything; usable before the application context exists. */
	public static int refuseWithoutTerminal(PrintWriter err) {
		return fail(err, NO_TERMINAL);
	}

	/** Returns the process exit code: 0 after a reset, 1 when nothing changed. */
	public int run(String username, Optional<PasswordTerminal> terminal, PrintWriter out, PrintWriter err) {
		if (terminal.isEmpty()) return refuseWithoutTerminal(err);
		String usernameCanonical = Account.canonical(username);
		if (!accounts.existsByUsernameCanonical(usernameCanonical)) return fail(err, UNKNOWN_USER);
		char[] password = null;
		char[] confirmation = null;
		try {
			password = terminal.get().readPassword(PROMPT);
			if (password == null) return fail(err, NO_INPUT);
			confirmation = terminal.get().readPassword(CONFIRMATION_PROMPT);
			if (confirmation == null) return fail(err, NO_INPUT);
			if (!Arrays.equals(password, confirmation)) return fail(err, MISMATCH);
			if (!CredentialsPolicy.validNewPassword(CharBuffer.wrap(password))) return fail(err, INVALID_PASSWORD);
			String hash = passwordEncoder.encode(CharBuffer.wrap(password));
			// One statement writes the hash and the new version together; the row may vanish meanwhile.
			if (accounts.replacePassword(usernameCanonical, hash) != 1) return fail(err, UNKNOWN_USER);
			out.println(SUCCESS);
			return 0;
		}
		finally {
			if (password != null) Arrays.fill(password, '\0');
			if (confirmation != null) Arrays.fill(confirmation, '\0');
		}
	}

	private static int fail(PrintWriter err, String message) {
		err.println(message);
		return 1;
	}
}
