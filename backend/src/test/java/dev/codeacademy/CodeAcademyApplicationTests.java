package dev.codeacademy;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

// A single real HTTP server and disposable database; no test security overrides.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class CodeAcademyApplicationTest {

	@Autowired ApplicationContext context;
	@Autowired JdbcTemplate jdbc;
	@LocalServerPort int port;

	private final ObjectMapper json = new ObjectMapper();
	private CookieManager cookies;
	private HttpClient client;

	@BeforeEach
	void newAnonymousBrowser() {
		cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		client = HttpClient.newBuilder().cookieHandler(cookies)
				.followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
	}

	@Test
	void healthIsPublicAndExactlyMinimal() throws Exception {
		var before = databaseSnapshot();
		var response = request("GET", "/actuator/health", null);
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
		assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
		assertThat(databaseSnapshot()).isEqualTo(before);
	}

	@Test
	void denyApi401WithoutBasicChallengeOrLoginForm() throws Exception {
		var before = databaseSnapshot();
		String genericBody = null;
		for (String path : List.of("/api/me", "/api/courses", "/api/courses/java",
				"/api/lessons/unknown", "/api/resources/unknown", "/api/me/courses",
				"/api/me/courses/java/progress", "/api/future-route", "/login",
				"/actuator", "/actuator/env", "/actuator/health/db", "/outside-api")) {
			var response = request("GET", path, null);
			assertError(response, 401, "AUTHENTICATION_REQUIRED");
			if (genericBody == null) genericBody = response.body();
			assertThat(response.body()).as(path).isEqualTo(genericBody);
		}
		assertThat(databaseSnapshot()).isEqualTo(before);
	}

	@Test
	void csrf403WithoutTokenAnd401WithCorrectSpaCookieHeader() throws Exception {
		var before = databaseSnapshot();
		for (String path : List.of("/api/auth/register", "/api/auth/login", "/api/auth/logout")) {
			assertError(request("POST", path, null), 403, "CSRF_REJECTED");
		}
		for (String method : List.of("PUT", "PATCH", "DELETE")) {
			assertError(request(method, "/api/me/courses/java", null), 403, "CSRF_REJECTED");
		}

		// The official SPA integration must materialize a readable token on a safe request.
		request("GET", "/actuator/health", null);
		var token = cookies.getCookieStore().getCookies().stream()
				.filter(cookie -> cookie.getName().equals("XSRF-TOKEN")).findFirst();
		assertThat(token).as("SPA XSRF-TOKEN cookie available before login").isPresent();
		assertThat(token.orElseThrow().isHttpOnly()).isFalse();
		assertThat(token.orElseThrow().getValue()).isNotBlank();
		assertError(request("POST", "/api/auth/login", "invalid-token"), 403, "CSRF_REJECTED");
		// A valid token passes CSRF; the public routes then handle the request themselves.
		for (String path : List.of("/api/auth/register", "/api/auth/login")) {
			assertError(request("POST", path, token.orElseThrow().getValue()), 400, "INVALID_REQUEST");
		}
		for (String method : List.of("PUT", "PATCH", "DELETE")) {
			assertError(request(method, "/api/me/courses/java", token.orElseThrow().getValue()),
					401, "AUTHENTICATION_REQUIRED");
		}
		assertThat(request("POST", "/api/auth/logout", token.orElseThrow().getValue()).statusCode()).isEqualTo(204);
		assertThat(databaseSnapshot()).isEqualTo(before);
	}

	@Test
	void flywayHasOnlySuccessfulSqlMigrationsWithoutProgressTables() {
		var history = jdbc.queryForList("select version, type, success from flyway_schema_history where version is not null");
		assertThat(history).as("At least one versioned technical SQL migration").isNotEmpty();
		assertThat(history).allSatisfy(row -> {
			assertThat(row.get("type")).isEqualTo("SQL");
			assertThat(row.get("success")).isEqualTo(true);
		});
		assertThat(jdbc.queryForObject("show server_version", String.class)).startsWith("18.6");
		assertThat(jdbc.queryForList("""
				select table_name from information_schema.tables
				where table_schema not in ('pg_catalog', 'information_schema')
				""", String.class)).doesNotContain("enrollments", "lesson_states", "exercise_states",
				"inscripcion");
	}

	@Test
	void serverBindingDefaultsAreLoopback8080WhileTestUsesRandomPort() throws Exception {
		var defaults = new Properties();
		try (var input = context.getResource("classpath:application.properties").getInputStream()) {
			defaults.load(input);
		}
		assertThat(defaults.getProperty("server.address")).isEqualTo("127.0.0.1");
		assertThat(defaults.getProperty("server.port")).isEqualTo("8080");
		assertThat(context.getEnvironment().getProperty("server.address")).isEqualTo("127.0.0.1");
		assertThat(port).isPositive();
		assertThat(request("GET", "/actuator/health", null).statusCode()).isEqualTo(200);
	}

	@Test
	void argon2ConfigurationAnd128UnicodeRoundTripBenchmark() {
		// Provider lookup keeps missing production configuration an assertion failure, not failed wiring.
		var encoders = context.getBeanProvider(PasswordEncoder.class).stream().toList();
		assertThat(encoders).as("One explicit production Argon2 password encoder").hasSize(1);
		assertThat(encoders.getFirst()).isInstanceOf(Argon2PasswordEncoder.class);
		var encoder = encoders.getFirst();
		String password = "\uD83D\uDE80".repeat(127) + " ";
		assertThat(password.codePointCount(0, password.length())).isEqualTo(128);
		assertThat(password.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(72);
		long start = System.nanoTime();
		String encoded = encoder.encode(password);
		long encodeNanos = System.nanoTime() - start;
		assertThat(encoded).startsWith("$argon2id$v=19$");
		String[] parameters = encoded.split("\\$")[3].split(",");
		assertThat(Integer.parseInt(parameters[0].substring(2))).isGreaterThanOrEqualTo(19 * 1024);
		assertThat(Integer.parseInt(parameters[1].substring(2))).isGreaterThanOrEqualTo(2);
		assertThat(Integer.parseInt(parameters[2].substring(2))).isEqualTo(1);
		start = System.nanoTime();
		assertThat(encoder.matches(password, encoded)).isTrue();
		long verifyNanos = System.nanoTime() - start;
		assertThat(encoder.matches("\uD83D\uDE80".repeat(127) + "!", encoded)).isFalse();
		assertThat(encoder.matches(password.stripTrailing(), encoded)).isFalse();
		assertThat(encoder.matches("\uD83D\uDE80".repeat(18), encoded)).isFalse();
		assertThat(encoder.encode(password)).as("Random salt per hash").isNotEqualTo(encoded);
		// Record cost, not a flaky machine-dependent timing threshold or a lowered security floor.
		System.out.printf("Argon2id 128-codepoint benchmark: encode=%.3f ms verify=%.3f ms%n",
				encodeNanos / 1_000_000.0, verifyNanos / 1_000_000.0);
	}

	@Test
	void noAutoAdminOrGeneratedPassword(CapturedOutput output) throws Exception {
		assertThat(context.getBeansOfType(InMemoryUserDetailsManager.class)).isEmpty();
		assertThat(context.containsBean("inMemoryUserDetailsManager")).isFalse();
		assertThat(output.getAll()).doesNotContain("Using generated security password:");
		var response = client.send(HttpRequest.newBuilder(uri("/api/me"))
				.header("Authorization", "Basic " + java.util.Base64.getEncoder()
						.encodeToString("user:password".getBytes(StandardCharsets.UTF_8)))
				.GET().build(), HttpResponse.BodyHandlers.ofString());
		assertError(response, 401, "AUTHENTICATION_REQUIRED");
	}

	private URI uri(String path) {
		return URI.create("http://127.0.0.1:" + port + path);
	}

	private HttpResponse<String> request(String method, String path, String csrf) throws Exception {
		var builder = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
				.header("Accept", "application/json");
		if (csrf != null) builder.header("X-XSRF-TOKEN", csrf);
		return client.send(builder.method(method, HttpRequest.BodyPublishers.noBody()).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
		assertThat(response.statusCode()).as(response.uri().toString()).isEqualTo(status);
		assertThat(response.headers().firstValue("Location")).isEmpty();
		assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
		assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
		JsonNode body = json.readTree(response.body());
		assertThat(body.isObject()).isTrue();
		assertThat(body.size()).isEqualTo(2);
		assertThat(body.path("code").asString()).isEqualTo(code);
		assertThat(body.path("message").isString()).isTrue();
		assertThat(body.path("message").asString()).isNotBlank();
	}

	private List<Map<String, Object>> databaseSnapshot() {
		// Snapshot every existing application table, not just counters; no domain data is seeded.
		return jdbc.queryForList("""
				select n.nspname as schema_name, c.relname as table_name,
				       query_to_xml(format('select * from %I.%I order by 1', n.nspname, c.relname),
				                    true, false, '')::text as contents
				from pg_class c join pg_namespace n on c.relnamespace = n.oid
				where c.relkind = 'r' and n.nspname not in ('pg_catalog', 'information_schema')
				  and n.nspname not like 'pg_toast%'
				order by n.nspname, c.relname
				""");
	}
}
