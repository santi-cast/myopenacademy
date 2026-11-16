package dev.codeacademy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import dev.codeacademy.content.ExampleContent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import dev.codeacademy.PasswordResetCommandTest.ScriptedTerminal;
import dev.codeacademy.account.CreateAdminCommand;
import dev.codeacademy.account.PasswordTerminal;

import static dev.codeacademy.ApiClient.assertError;
import static dev.codeacademy.ApiClient.credentials;
import static dev.codeacademy.RegistrationApiTest.PASSWORD;
import static dev.codeacademy.RegistrationApiTest.unique;
import static org.assertj.core.api.Assertions.assertThat;

/** H9, T1: the administrator from the console (--create-admin), roles in the session and deactivated accounts. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CreateAdminCommandTest {

	@DynamicPropertySource
	static void content(DynamicPropertyRegistry registry) {
		registry.add("code-academy.content-dir", () -> ExampleContent.root().toString());
	}

	static final String NL = System.lineSeparator();

	@Autowired CreateAdminCommand command;
	@Autowired JdbcTemplate jdbc;
	@LocalServerPort int port;

	@Test
	void aNewUsernameBecomesAnAdministratorWithThePasswordTypedTwice() throws Exception {
		String username = unique("Admin_");
		var terminal = new ScriptedTerminal(PASSWORD, PASSWORD);
		var result = run(username, Optional.of(terminal));
		assertThat(result.exit()).isZero();
		assertThat(result.out()).isEqualTo("Administrador creado. Ya puedes entrar en la academia con ese usuario." + NL);
		assertThat(terminal.prompts).containsExactly("Contraseña del administrador: ", "Repite la contraseña: ");
		terminal.assertWiped();
		assertThat(jdbc.queryForObject("select role from code_academy.users where username = ?", String.class, username))
			.isEqualTo("admin");
		var browser = new ApiClient(port);
		assertThat(browser.send("POST", "/api/auth/login", credentials(username, PASSWORD)).statusCode()).isEqualTo(200);
		assertThat(ApiClient.JSON.readTree(browser.get("/api/me").body()).path("role").asString()).isEqualTo("admin");
	}

	@Test
	void anExistingUserBecomesAnAdministratorKeepsItsPasswordAndItsSessionsClose() throws Exception {
		String username = unique("Alumno_");
		var browser = new ApiClient(port);
		assertThat(browser.send("POST", "/api/auth/register", credentials(username, PASSWORD)).statusCode()).isEqualTo(201);
		assertThat(browser.send("POST", "/api/auth/login", credentials(username, PASSWORD)).statusCode()).isEqualTo(200);
		String hash = jdbc.queryForObject("select password_hash from code_academy.users where username = ?", String.class, username);

		var terminal = new ScriptedTerminal();
		var result = run(username.toUpperCase(), Optional.of(terminal));
		assertThat(result.exit()).isZero();
		assertThat(result.out()).startsWith("El usuario ya existía: ahora es administrador y conserva su contraseña.");
		assertThat(terminal.prompts).isEmpty();
		assertThat(jdbc.queryForObject("select password_hash from code_academy.users where username = ?", String.class, username))
			.isEqualTo(hash);
		// The open session was granted as a learner: it closes, and the next login is an administrator's.
		assertThat(browser.get("/api/me").statusCode()).isEqualTo(401);
		assertThat(browser.send("POST", "/api/auth/login", credentials(username, PASSWORD)).statusCode()).isEqualTo(200);
		assertThat(ApiClient.JSON.readTree(browser.get("/api/me").body()).path("role").asString()).isEqualTo("admin");

		var again = run(username, Optional.of(new ScriptedTerminal()));
		assertThat(again.exit()).isEqualTo(1);
		assertThat(again.err()).isEqualTo("El usuario ya era administrador. No se cambió nada." + NL);
	}

	@Test
	void nothingIsCreatedWithoutATerminalAValidUsernameOrTwoEqualPasswords() {
		long users = jdbc.queryForObject("select count(*) from code_academy.users", Long.class);
		assertThat(run(unique("Admin_"), Optional.empty()).exit()).isEqualTo(1);
		var invalid = run("no vale", Optional.of(new ScriptedTerminal(PASSWORD, PASSWORD)));
		assertThat(invalid.exit()).isEqualTo(1);
		assertThat(invalid.err()).startsWith("El usuario debe tener entre 3 y 32");
		var mismatch = run(unique("Admin_"), Optional.of(new ScriptedTerminal(PASSWORD, PASSWORD + "x")));
		assertThat(mismatch.err()).isEqualTo("Las contraseñas no coinciden. No se cambió nada." + NL);
		var shortPassword = run(unique("Admin_"), Optional.of(new ScriptedTerminal("corta", "corta")));
		assertThat(shortPassword.exit()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from code_academy.users", Long.class)).isEqualTo(users);
	}

	@Test
	void aDeactivatedAccountCannotSignInAndSaysSoOnlyAfterTheRightPassword() throws Exception {
		String username = unique("Baja_");
		var browser = new ApiClient(port);
		assertThat(browser.send("POST", "/api/auth/register", credentials(username, PASSWORD)).statusCode()).isEqualTo(201);
		jdbc.update("update code_academy.users set active = false where username = ?", username);
		assertError(browser.send("POST", "/api/auth/login", credentials(username, PASSWORD + "x")), 401, "AUTHENTICATION_FAILED");
		assertError(browser.send("POST", "/api/auth/login", credentials(username, PASSWORD)), 403, "ACCOUNT_DISABLED");
	}

	private Result run(String target, Optional<PasswordTerminal> terminal) {
		var out = new StringWriter();
		var err = new StringWriter();
		int exit = command.run(target, terminal, new PrintWriter(out, true), new PrintWriter(err, true));
		return new Result(exit, out.toString(), err.toString());
	}

	record Result(int exit, String out, String err) {
	}
}
