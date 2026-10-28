package dev.codeacademy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.codeacademy.account.PasswordResetCommand;
import dev.codeacademy.account.PasswordTerminal;

import static dev.codeacademy.ApiClient.assertError;
import static dev.codeacademy.ApiClient.credentials;
import static dev.codeacademy.RegistrationApiTest.PASSWORD;
import static dev.codeacademy.RegistrationApiTest.unique;
import static org.assertj.core.api.Assertions.assertThat;

/** The console reset through its command entry, with a scripted terminal and the real database. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PasswordResetCommandTest {

	static final String NL = System.lineSeparator();
	static final String NEW_PASSWORD = "otra frase bastante larga";

	@Autowired PasswordResetCommand command;
	@Autowired JdbcTemplate jdbc;
	@LocalServerPort int port;

	String username;

	@BeforeEach
	void registeredAccount() throws Exception {
		username = unique("Reset_");
		assertThat(new ApiClient(port).send("POST", "/api/auth/register", credentials(username, PASSWORD))
				.statusCode()).isEqualTo(201);
	}

	@Test
	void resetKeepsIdChangesHashBumpsVersionAndClosesOlderSessions() throws Exception {
		var browser = new ApiClient(port);
		assertThat(browser.send("POST", "/api/auth/login", credentials(username, PASSWORD)).statusCode())
				.isEqualTo(200);
		assertThat(browser.get("/api/me").statusCode()).isEqualTo(200);
		Map<String, Object> before = row();
		long users = jdbc.queryForObject("select count(*) from code_academy.users", Long.class);

		var terminal = new ScriptedTerminal(NEW_PASSWORD, NEW_PASSWORD);
		var result = run(username.toLowerCase(), Optional.of(terminal));

		assertThat(result.exit()).isZero();
		assertThat(result.out()).isEqualTo(
				"Contraseña actualizada. Las sesiones abiertas de este usuario se cerrarán en su próxima solicitud."
						+ NL);
		assertThat(result.err()).isEmpty();
		assertThat(terminal.prompts).containsExactly("Nueva contraseña: ", "Repetí la nueva contraseña: ");
		terminal.assertWiped();

		Map<String, Object> after = row();
		assertThat(after.get("id")).isEqualTo(before.get("id"));
		assertThat(after.get("username")).isEqualTo(username);
		assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
		assertThat((String) after.get("password_hash")).startsWith("$argon2id$")
				.isNotEqualTo(before.get("password_hash")).doesNotContain(NEW_PASSWORD);
		assertThat(after.get("credentials_version")).isEqualTo((Long) before.get("credentials_version") + 1);
		assertThat(jdbc.queryForObject("select count(*) from code_academy.users", Long.class)).isEqualTo(users);

		assertError(browser.get("/api/me"), 401, "AUTHENTICATION_REQUIRED");
		assertError(new ApiClient(port).send("POST", "/api/auth/login", credentials(username, PASSWORD)),
				401, "AUTHENTICATION_FAILED");
		var fresh = new ApiClient(port);
		assertThat(fresh.send("POST", "/api/auth/login", credentials(username, NEW_PASSWORD)).statusCode())
				.isEqualTo(200);
		assertThat(fresh.get("/api/me").statusCode()).isEqualTo(200);
	}

	@Test
	void rejectedInputChangesNothingAndExitsNonZero() throws Exception {
		String policy = "La contraseña debe tener entre 15 y 128 caracteres (puntos de código Unicode). "
				+ "No se cambió nada." + NL;
		record Case(String username, String[] answers, String err, int prompts) {
		}
		for (Case attempt : List.of(
				new Case(username, new String[] { NEW_PASSWORD, NEW_PASSWORD + " " },
						"Las contraseñas no coinciden. No se cambió nada." + NL, 2),
				new Case(username, new String[] { "🚀".repeat(14), "🚀".repeat(14) }, policy, 2),
				new Case(username, new String[] { "a".repeat(14), "a".repeat(14) }, policy, 2),
				new Case(username, new String[] { "🚀".repeat(129), "🚀".repeat(129) }, policy, 2),
				new Case(username, new String[] { "a".repeat(129), "a".repeat(129) }, policy, 2),
				new Case(username, new String[] { null }, "No se recibió la contraseña. No se cambió nada." + NL, 1),
				new Case(unique("nobody_"), new String[] { NEW_PASSWORD, NEW_PASSWORD },
						"No existe el usuario indicado. No se cambió nada." + NL, 0))) {
			var before = TestDatabase.snapshot(jdbc);
			var terminal = new ScriptedTerminal(attempt.answers());

			var result = run(attempt.username(), Optional.of(terminal));

			assertThat(result.exit()).as(attempt.err()).isEqualTo(1);
			assertThat(result.err()).isEqualTo(attempt.err());
			assertThat(result.out()).isEmpty();
			assertThat(terminal.prompts).hasSize(attempt.prompts());
			terminal.assertWiped();
			assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
		}
		assertThat(new ApiClient(port).send("POST", "/api/auth/login", credentials(username, PASSWORD))
				.statusCode()).isEqualTo(200);
	}

	@Test
	void boundariesAreAcceptedAndSpacesAreKept() throws Exception {
		// The spaced 15-code-point phrase goes last, so the stripped login below targets it.
		for (String password : List.of("🚀".repeat(128), " quince puntos ")) {
			var result = run(username, Optional.of(new ScriptedTerminal(password, password)));

			assertThat(result.exit()).as(password).isZero();
			assertThat(result.err()).isEmpty();
			assertThat(new ApiClient(port).send("POST", "/api/auth/login", credentials(username, password))
					.statusCode()).isEqualTo(200);
		}
		assertError(new ApiClient(port).send("POST", "/api/auth/login", credentials(username, "quince puntos")),
				401, "AUTHENTICATION_FAILED");
	}

	@Test
	void withoutRealTerminalItRefusesAsksNothingAndChangesNothing() throws Exception {
		Optional<PasswordTerminal> system = PasswordTerminal.system();
		assertThat(system).as("The surefire JVM has no terminal").isEmpty();
		var before = TestDatabase.snapshot(jdbc);

		var result = run(username, system);

		assertThat(result.exit()).isEqualTo(1);
		assertThat(result.out()).isEmpty();
		assertThat(result.err()).isEqualTo("Esta herramienta necesita una terminal interactiva para pedir la "
				+ "contraseña sin mostrarla. Ejecutala con java -jar desde una terminal. No se cambió nada." + NL);
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	private Map<String, Object> row() {
		return jdbc.queryForMap("select * from code_academy.users where username = ?", username);
	}

	private Result run(String target, Optional<PasswordTerminal> terminal) {
		var out = new StringWriter();
		var err = new StringWriter();
		int exit = command.run(target, terminal, new PrintWriter(out, true), new PrintWriter(err, true));
		return new Result(exit, out.toString(), err.toString());
	}

	record Result(int exit, String out, String err) {
	}

	/** Answers prompts in order; null simulates end of input. Keeps the buffers to prove they are wiped. */
	static final class ScriptedTerminal implements PasswordTerminal {

		final List<String> prompts = new ArrayList<>();
		final List<char[]> handedOut = new ArrayList<>();
		private final Deque<Optional<String>> answers = new ArrayDeque<>();

		ScriptedTerminal(String... answers) {
			for (String answer : answers) this.answers.add(Optional.ofNullable(answer));
		}

		@Override
		public char[] readPassword(String prompt) {
			prompts.add(prompt);
			char[] answer = answers.removeFirst().map(String::toCharArray).orElse(null);
			if (answer != null) handedOut.add(answer);
			return answer;
		}

		void assertWiped() {
			for (char[] buffer : handedOut) assertThat(buffer).containsOnly('\0');
		}
	}
}
