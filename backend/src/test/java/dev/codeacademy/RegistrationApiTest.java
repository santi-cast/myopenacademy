package dev.codeacademy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static dev.codeacademy.ApiClient.assertError;
import static dev.codeacademy.ApiClient.credentials;
import static dev.codeacademy.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationApiTest {

	static final String PASSWORD = "una frase extensa";

	@Autowired JdbcTemplate jdbc;
	@LocalServerPort int port;

	ApiClient browser;

	@BeforeEach
	void newBrowser() {
		browser = new ApiClient(port);
	}

	@Test
	void registersPreservingDisplayNameWithoutStartingSession() throws Exception {
		String username = unique("Alumno_");
		var response = browser.send("POST", "/api/auth/register", credentials(username, PASSWORD));

		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
		var body = json(response);
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("id", "username");
		assertThat(body.path("username").asString()).isEqualTo(username);
		assertThat(UUID.fromString(body.path("id").asString())).isNotNull();

		var row = jdbc.queryForMap("select * from code_academy.users where id = ?::uuid", body.path("id").asString());
		assertThat(row.get("username")).isEqualTo(username);
		assertThat(row.get("username_canonical")).isEqualTo(username.toLowerCase());
		assertThat((String) row.get("password_hash")).startsWith("$argon2id$").doesNotContain(PASSWORD);
		assertThat(row.get("credentials_version")).isEqualTo(1L);

		assertThat(browser.cookie("JSESSIONID")).as("Registration does not log in").isEmpty();
		assertError(browser.get("/api/me"), 401, "AUTHENTICATION_REQUIRED");
	}

	@Test
	void usernameTakenIgnoringCaseLeavesDatabaseUnchanged() throws Exception {
		String username = unique("Santiago_");
		assertThat(browser.send("POST", "/api/auth/register", credentials(username, PASSWORD)).statusCode())
				.isEqualTo(201);
		var before = TestDatabase.snapshot(jdbc);

		for (String variant : List.of(username, username.toLowerCase(), username.toUpperCase())) {
			assertError(browser.send("POST", "/api/auth/register", credentials(variant, "otra frase distinta")),
					409, "USERNAME_TAKEN");
		}
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void usernameRuleIsThreeToThirtyTwoAsciiWordCharactersWithoutTrimming() throws Exception {
		var before = TestDatabase.snapshot(jdbc);
		for (String invalid : List.of("ab", "a".repeat(33), "con espacio", "ñandu", " " + unique("pad"),
				unique("pad") + " ", "guion-medio", "", "punto.final")) {
			assertError(browser.send("POST", "/api/auth/register", credentials(invalid, PASSWORD)),
					400, "INVALID_USERNAME");
		}
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);

		// Exact boundaries; the three-character name is reserved for this test in the disposable database.
		for (String valid : List.of("Ab3", (unique("Z9_") + "_".repeat(32)).substring(0, 32))) {
			assertThat(browser.send("POST", "/api/auth/register", credentials(valid, PASSWORD)).statusCode())
					.as(valid).isEqualTo(201);
		}
	}

	@Test
	void passwordIsFifteenToOneHundredTwentyEightCodePointsKeptVerbatim() throws Exception {
		String rocket = "\uD83D\uDE80";
		var before = TestDatabase.snapshot(jdbc);
		for (String invalid : List.of("a".repeat(14), rocket.repeat(14), rocket.repeat(129), "a".repeat(129), "")) {
			assertError(browser.send("POST", "/api/auth/register", credentials(unique("p_"), invalid)),
					400, "INVALID_PASSWORD");
		}
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);

		// Boundaries counted in code points (an emoji is two UTF-16 units) and spaces never trimmed.
		for (String valid : List.of(rocket.repeat(15), rocket.repeat(128), "  " + "b".repeat(13), "c".repeat(128))) {
			var response = browser.send("POST", "/api/auth/register", credentials(unique("p_"), valid));
			assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
		}
	}

	@Test
	void strictJsonRejectsSchemaAndTypeErrorsWithoutWriting() throws Exception {
		var before = TestDatabase.snapshot(jdbc);
		String name = unique("j_");
		for (String body : List.of(
				"{",
				"[]",
				"\"text\"",
				"null",
				"{}",
				"{\"username\":\"" + name + "\"}",
				"{\"password\":\"" + PASSWORD + "\"}",
				"{\"username\":null,\"password\":\"" + PASSWORD + "\"}",
				"{\"username\":\"" + name + "\",\"password\":null}",
				"{\"username\":123,\"password\":\"" + PASSWORD + "\"}",
				"{\"username\":\"" + name + "\",\"password\":123456789012345678}",
				"{\"username\":[\"" + name + "\"],\"password\":\"" + PASSWORD + "\"}",
				"{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\",\"admin\":true}")) {
			assertError(browser.send("POST", "/api/auth/register", body), 400, "INVALID_REQUEST");
		}
		// Missing body and a non-JSON content type are schema errors too.
		assertError(browser.send("POST", "/api/auth/register", null), 400, "INVALID_REQUEST");
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void registrationStillRequiresCsrf() throws Exception {
		var before = TestDatabase.snapshot(jdbc);
		assertError(browser.send("POST", "/api/auth/register", credentials(unique("c_"), PASSWORD), null),
				403, "CSRF_REJECTED");
		assertError(browser.send("POST", "/api/auth/register", credentials(unique("c_"), PASSWORD), "forged"),
				403, "CSRF_REJECTED");
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	static String unique(String prefix) {
		return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}
}
