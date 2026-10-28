package dev.codeacademy;

import java.net.HttpCookie;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static dev.codeacademy.ApiClient.assertError;
import static dev.codeacademy.ApiClient.credentials;
import static dev.codeacademy.ApiClient.json;
import static dev.codeacademy.RegistrationApiTest.PASSWORD;
import static dev.codeacademy.RegistrationApiTest.unique;
import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionApiTest {

	@Autowired JdbcTemplate jdbc;
	@Autowired Environment environment;
	@LocalServerPort int port;

	ApiClient browser;
	String username;

	@BeforeEach
	void registeredAccount() throws Exception {
		browser = new ApiClient(port);
		username = unique("Mixed_");
		assertThat(new ApiClient(port).send("POST", "/api/auth/register", credentials(username, PASSWORD))
				.statusCode()).isEqualTo(201);
	}

	@Test
	void loginIgnoresCaseReturnsDisplayNameAndAuthenticatesMe() throws Exception {
		var login = browser.send("POST", "/api/auth/login", credentials(username.toLowerCase(), PASSWORD));

		assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
		var user = json(login);
		assertThat(user.propertyNames()).containsExactlyInAnyOrder("id", "username");
		assertThat(user.path("username").asString()).isEqualTo(username);
		String setCookie = login.headers().allValues("Set-Cookie").stream()
				.filter(value -> value.startsWith("JSESSIONID=")).findFirst().orElseThrow();
		assertThat(setCookie).contains("HttpOnly").containsIgnoringCase("SameSite=Lax")
				.doesNotContainIgnoringCase("Domain=");
		assertThat(login.headers().firstValue("Cache-Control").orElse("")).contains("no-store");

		var me = browser.get("/api/me");
		assertThat(me.statusCode()).isEqualTo(200);
		assertThat(json(me)).isEqualTo(user);
		assertThat(environment.getProperty("server.servlet.session.timeout")).isEqualTo("2h");
	}

	@Test
	void loginRotatesSessionIdAndRenewsCsrfToken() throws Exception {
		String tokenBefore = csrfAfterSafeRequest();
		assertThat(browser.send("POST", "/api/auth/login", credentials(username, PASSWORD)).statusCode())
				.isEqualTo(200);
		String firstSession = browser.cookie("JSESSIONID").orElseThrow().getValue();
		assertThat(csrfAfterSafeRequest()).isNotEqualTo(tokenBefore);

		assertThat(browser.send("POST", "/api/auth/login", credentials(username, PASSWORD)).statusCode())
				.isEqualTo(200);
		String secondSession = browser.cookie("JSESSIONID").orElseThrow().getValue();
		assertThat(secondSession).isNotEqualTo(firstSession);

		var replay = new ApiClient(port);
		replay.useSession(firstSession);
		assertError(replay.get("/api/me"), 401, "AUTHENTICATION_REQUIRED");
		assertThat(browser.get("/api/me").statusCode()).isEqualTo(200);
	}

	@Test
	void everyInvalidCredentialGetsTheSameGeneric401WithoutSession() throws Exception {
		var before = TestDatabase.snapshot(jdbc);
		String body = null;
		for (String[] attempt : List.of(
				new String[] { unique("nobody_"), PASSWORD },
				new String[] { username, PASSWORD + "x" },
				new String[] { username.toUpperCase(), " " + PASSWORD },
				new String[] { username, PASSWORD.stripTrailing() + " " },
				new String[] { "no valid name", PASSWORD },
				new String[] { "ab", PASSWORD },
				new String[] { username, "" },
				new String[] { username, "corta" },
				new String[] { username, "🚀".repeat(129) })) {
			var response = browser.send("POST", "/api/auth/login", credentials(attempt[0], attempt[1]));
			assertError(response, 401, "AUTHENTICATION_FAILED");
			if (body == null) body = response.body();
			assertThat(response.body()).isEqualTo(body);
		}
		assertError(browser.get("/api/me"), 401, "AUTHENTICATION_REQUIRED");
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void loginSchemaErrorsAre400AndStillRequireCsrf() throws Exception {
		for (String body : List.of("{", "{}", "{\"username\":\"" + username + "\"}",
				"{\"username\":\"" + username + "\",\"password\":1}",
				"{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"remember\":true}")) {
			assertError(browser.send("POST", "/api/auth/login", body), 400, "INVALID_REQUEST");
		}
		assertError(browser.send("POST", "/api/auth/login", credentials(username, PASSWORD), null),
				403, "CSRF_REJECTED");
		assertError(browser.get("/api/me"), 401, "AUTHENTICATION_REQUIRED");
	}

	@Test
	void logoutInvalidatesSessionClearsCookieAndRenewsCsrf() throws Exception {
		assertThat(browser.send("POST", "/api/auth/login", credentials(username, PASSWORD)).statusCode())
				.isEqualTo(200);
		String session = browser.cookie("JSESSIONID").orElseThrow().getValue();

		assertError(browser.send("POST", "/api/auth/logout", null, null), 403, "CSRF_REJECTED");
		assertThat(browser.get("/api/me").statusCode()).as("Rejected logout keeps the session").isEqualTo(200);

		String tokenBefore = csrfAfterSafeRequest();
		var logout = browser.send("POST", "/api/auth/logout", null);
		assertThat(logout.statusCode()).isEqualTo(204);
		assertThat(logout.body()).isEmpty();
		assertThat(logout.headers().allValues("Set-Cookie")).anySatisfy(value ->
				assertThat(value).startsWith("JSESSIONID=").containsIgnoringCase("Max-Age=0"));
		assertError(browser.get("/api/me"), 401, "AUTHENTICATION_REQUIRED");
		assertThat(csrfAfterSafeRequest()).isNotEqualTo(tokenBefore);

		var replay = new ApiClient(port);
		replay.useSession(session);
		assertError(replay.get("/api/me"), 401, "AUTHENTICATION_REQUIRED");
	}

	@Test
	void changedCredentialsVersionRejectsExistingSessionOnNextRequest() throws Exception {
		assertThat(browser.send("POST", "/api/auth/login", credentials(username, PASSWORD)).statusCode())
				.isEqualTo(200);
		var other = new ApiClient(port);
		assertThat(other.send("POST", "/api/auth/login", credentials(username, PASSWORD)).statusCode())
				.isEqualTo(200);

		// What the console reset does to the version; the console itself is covered separately.
		jdbc.update("update code_academy.users set credentials_version = credentials_version + 1 "
				+ "where username_canonical = ?", username.toLowerCase());

		assertError(browser.get("/api/me"), 401, "AUTHENTICATION_REQUIRED");
		assertError(other.get("/api/me"), 401, "AUTHENTICATION_REQUIRED");
		assertError(browser.get("/api/me"), 401, "AUTHENTICATION_REQUIRED");
		assertThat(browser.send("POST", "/api/auth/login", credentials(username, PASSWORD)).statusCode())
				.isEqualTo(200);
		assertThat(browser.get("/api/me").statusCode()).isEqualTo(200);
	}

	@Test
	void nonCsrfDenialIsAccessDeniedWhileCsrfFailureStaysCsrfRejected() throws Exception {
		assertThat(browser.send("POST", "/api/auth/login", credentials(username, PASSWORD)).statusCode())
				.isEqualTo(200);
		var before = TestDatabase.snapshot(jdbc);

		assertError(browser.get("/api/future-route"), 403, "ACCESS_DENIED");
		assertError(browser.send("PUT", "/api/future-route", null), 403, "ACCESS_DENIED");
		assertError(browser.send("PUT", "/api/future-route", null, null), 403, "CSRF_REJECTED");
		assertError(browser.send("PUT", "/api/future-route", null, "forged"), 403, "CSRF_REJECTED");
		assertError(new ApiClient(port).get("/api/future-route"), 401, "AUTHENTICATION_REQUIRED");
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	private String csrfAfterSafeRequest() throws Exception {
		browser.get("/actuator/health");
		return browser.cookie("XSRF-TOKEN").map(HttpCookie::getValue).orElseThrow();
	}
}
