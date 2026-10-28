package dev.codeacademy;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** One simulated browser: its own cookie jar, the SPA CSRF header and JSON helpers. */
final class ApiClient {

	static final ObjectMapper JSON = new ObjectMapper();

	private final int port;
	private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
	private final HttpClient client;

	ApiClient(int port) {
		this.port = port;
		this.client = HttpClient.newBuilder().cookieHandler(cookies)
				.followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
	}

	/** Sends with the current XSRF-TOKEN, fetching one through a safe request first when missing. */
	HttpResponse<String> send(String method, String path, String jsonBody) throws Exception {
		if (cookie("XSRF-TOKEN").isEmpty()) send("GET", "/actuator/health", null, null);
		return send(method, path, jsonBody, cookie("XSRF-TOKEN").map(HttpCookie::getValue).orElseThrow());
	}

	HttpResponse<String> send(String method, String path, String jsonBody, String csrf) throws Exception {
		return send(method, path, jsonBody, csrf, "application/json");
	}

	/** Same as {@link #send(String, String, String)} with a chosen Accept header; null omits it. */
	HttpResponse<String> sendAccepting(String method, String path, String jsonBody, String accept) throws Exception {
		if (cookie("XSRF-TOKEN").isEmpty()) send("GET", "/actuator/health", null, null);
		return send(method, path, jsonBody, cookie("XSRF-TOKEN").map(HttpCookie::getValue).orElseThrow(), accept);
	}

	private HttpResponse<String> send(String method, String path, String jsonBody, String csrf, String accept)
			throws Exception {
		var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
				.timeout(Duration.ofSeconds(10));
		if (accept != null) builder.header("Accept", accept);
		if (csrf != null) builder.header("X-XSRF-TOKEN", csrf);
		if (jsonBody != null) builder.header("Content-Type", "application/json");
		var body = jsonBody == null ? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(jsonBody);
		return client.send(builder.method(method, body).build(), HttpResponse.BodyHandlers.ofString());
	}

	HttpResponse<String> get(String path) throws Exception {
		return send("GET", path, null, null);
	}

	Optional<HttpCookie> cookie(String name) {
		return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals(name)).findFirst();
	}

	/** Copies a session cookie into this browser, as an attacker replaying a stolen or stale ID would. */
	void useSession(String sessionId) {
		var cookie = new HttpCookie("JSESSIONID", sessionId);
		cookie.setPath("/");
		cookie.setVersion(0);
		cookies.getCookieStore().add(URI.create("http://127.0.0.1:" + port + "/"), cookie);
	}

	static JsonNode json(HttpResponse<String> response) {
		return JSON.readTree(response.body());
	}

	static String credentials(String username, String password) {
		var node = JSON.createObjectNode();
		node.put("username", username);
		node.put("password", password);
		return node.toString();
	}

	static void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).as(response.uri() + " " + response.body()).isEqualTo(status);
		assertThat(response.headers().firstValue("Location")).isEmpty();
		assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
		assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
		JsonNode body = json(response);
		assertThat(body.isObject()).isTrue();
		assertThat(body.size()).isEqualTo(2);
		assertThat(body.path("code").asString()).isEqualTo(code);
		assertThat(body.path("message").asString()).isNotBlank();
	}

}
