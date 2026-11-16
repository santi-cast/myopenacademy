package dev.codeacademy.account;

import java.io.PrintWriter;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The administrator is created from the console when installing (H9), never with a default user or password: in a real
 * terminal, like the password reset. A new username becomes an administrator account with the password typed twice; an
 * existing one becomes an administrator and keeps its password. Messages never include the password.
 */
@Component
public class CreateAdminCommand {

	static final String INVALID_USERNAME = "El usuario debe tener entre 3 y 32 letras sin tildes, cifras o guiones bajos. "
			+ "No se cambió nada.";
	static final String CREATED = "Administrador creado. Ya puede entrar en la academia con ese usuario.";
	static final String PROMOTED = "El usuario ya existía: ahora es administrador y conserva su contraseña. "
			+ "Sus sesiones abiertas se cerrarán en su próxima solicitud.";
	static final String ALREADY_ADMIN = "El usuario ya era administrador. No se cambió nada.";

	private static final String PROMPT = "Contraseña del administrador: ";
	private static final String CONFIRMATION_PROMPT = "Repite la contraseña: ";

	private final AccountRepository accounts;
	private final PasswordEncoder passwordEncoder;

	CreateAdminCommand(AccountRepository accounts, PasswordEncoder passwordEncoder) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
	}

	/** Returns the process exit code: 0 after creating or promoting, 1 when nothing changed. */
	public int run(String username, Optional<PasswordTerminal> terminal, PrintWriter out, PrintWriter err) {
		if (terminal.isEmpty()) return PasswordResetCommand.refuseWithoutTerminal(err);
		if (!CredentialsPolicy.validUsername(username)) return fail(err, INVALID_USERNAME);
		Optional<Account> existing = accounts.findByUsernameCanonical(Account.canonical(username));
		if (existing.isPresent()) {
			if (existing.get().admin()) return fail(err, ALREADY_ADMIN);
			if (accounts.promoteToAdmin(Account.canonical(username)) != 1) return fail(err, PasswordResetCommand.UNKNOWN_USER);
			out.println(PROMOTED);
			return 0;
		}
		char[] password = null;
		char[] confirmation = null;
		try {
			password = terminal.get().readPassword(PROMPT);
			if (password == null) return fail(err, PasswordResetCommand.NO_INPUT);
			confirmation = terminal.get().readPassword(CONFIRMATION_PROMPT);
			if (confirmation == null) return fail(err, PasswordResetCommand.NO_INPUT);
			if (!Arrays.equals(password, confirmation)) return fail(err, PasswordResetCommand.MISMATCH);
			if (!CredentialsPolicy.validNewPassword(CharBuffer.wrap(password))) {
				return fail(err, PasswordResetCommand.INVALID_PASSWORD);
			}
			accounts.saveAndFlush(new Account(username, passwordEncoder.encode(CharBuffer.wrap(password)), Account.ADMIN));
			out.println(CREATED);
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
