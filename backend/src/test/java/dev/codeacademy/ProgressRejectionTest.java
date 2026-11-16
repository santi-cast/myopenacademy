package dev.codeacademy;

import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static dev.codeacademy.ApiClient.assertError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every rejected progress request answers the contract error and leaves the whole database unchanged: in particular a
 * rejected mark never creates an enrollment.
 */
class ProgressRejectionTest extends ProgressTestSupport {

	/** Route templates that take an ID in the path; {id} is replaced by the raw, already encoded segment. */
	private static final Map<String, String> ID_ROUTES = new LinkedHashMap<>();
	static {
		ID_ROUTES.put("PUT /api/me/lessons/{id}/state", state("completed"));
		ID_ROUTES.put("PUT /api/me/exercises/{id}/state", state("solved"));
		ID_ROUTES.put("PUT /api/me/courses/{id}", null);
		ID_ROUTES.put("GET /api/me/courses/{id}/progress", null);
		ID_ROUTES.put("PUT /api/me/courses/{id}/continuation", "{\"lessonId\":\"" + EJEMPLO_LESSON + "\"}");
	}

	/** Look-alikes of real IDs that must never resolve to them. */
	private static final List<String> NOT_FOUND_SEGMENTS = List.of(
			"EJEMPLO.FICHEROS-JSON", "Ejemplo", "DEMO-01.01-E01",
			// Cyrillic «о» in place of the Latin o.
			"ejempl%D0%BE.ficheros-json", "ejempl%D0%BE", "dem%D0%BE-01.01-e01",
			"a".repeat(300),
			"ejemplo.ficheros-json%20", "ejemplo%20", "demo.01.01.e01%20",
			// A dot is part of the ID, never an extension to strip.
			"ejemplo.ficheros-json.json", "ejemplo.ficheros-json.un-pedido.json",
			"ejemplo.no-existe", "demo.01.01.e99", "python");

	@Test
	void unknownAndLookAlikeIdsAre404OnEveryRouteAndAddNothing() throws Exception {
		var browser = learner();
		var before = snapshot();

		for (var route : ID_ROUTES.entrySet()) {
			for (String segment : NOT_FOUND_SEGMENTS) {
				assertError(send(browser, route.getKey(), segment, route.getValue()), 404, "NOT_FOUND");
			}
		}

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void dotSegmentsNeverReachAProgressRoute() throws Exception {
		var browser = learner();
		var before = snapshot();

		for (var route : ID_ROUTES.entrySet()) {
			// The security firewall refuses a non-normalized path, plain or encoded, before any routing.
			for (String segment : List.of("..", "%2e%2e", "%2E%2E", ".%2e")) {
				assertError(send(browser, route.getKey(), segment, route.getValue()), 400, "INVALID_REQUEST");
			}
		}

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void nulInAPathIdNeverReachesTheDatabase() throws Exception {
		var browser = learner();
		var before = snapshot();

		for (var route : ID_ROUTES.entrySet()) {
			// Rejected by the server before routing (Tomcat answers 400), so no query ever sees U+0000.
			for (String segment : List.of("%00", "ejemplo.ficheros-json%00", "%00ejemplo")) {
				var response = send(browser, route.getKey(), segment, route.getValue());
				assertThat(response.statusCode()).as(route.getKey() + " " + segment).isEqualTo(400);
			}
		}

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void continuationBodyIdsThatDoNotResolveAre404() throws Exception {
		var browser = learner();
		ok(add(browser, EJEMPLO));
		var before = snapshot();

		for (String lessonId : List.of("EJEMPLO.FICHEROS-JSON", "ejempl\u043e.ficheros-json", "a".repeat(300),
				"ejemplo.ficheros-json ", " ejemplo.ficheros-json", "..", "../ejemplo.ficheros-json",
				"ejemplo/ficheros-json", "ejemplo.ficheros-json/state", "", "ejemplo.no-existe", "\u0000",
				"ejemplo.ficheros-json\u0000", "\u0000ejemplo.ficheros-json")) {
			assertError(continuation(browser, EJEMPLO, lessonId), 404, "NOT_FOUND");
		}

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void lessonStateBodyIsStrictJson() throws Exception {
		var browser = learner();
		String path = "/api/me/lessons/" + INTRO + "/state";
		var before = snapshot();

		for (String body : schemaViolations("state", "completed")) {
			assertError(browser.send("PUT", path, body), 400, "INVALID_REQUEST");
		}
		for (String unknown : List.of("solved", "pending", "in_progress", "Completed", "COMPLETED", "not completed",
				"not-completed", "done", "", " completed")) {
			assertError(lessonState(browser, INTRO, unknown), 400, "INVALID_STATE");
		}

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void exerciseStateBodyIsStrictJson() throws Exception {
		var browser = learner();
		String path = "/api/me/exercises/" + E01 + "/state";
		var before = snapshot();

		for (String body : schemaViolations("state", "solved")) {
			assertError(browser.send("PUT", path, body), 400, "INVALID_REQUEST");
		}
		for (String unknown : List.of("completed", "not_completed", "Solved", "SOLVED", "in progress", "in-progress",
				"done", "", "solved ")) {
			assertError(exerciseState(browser, E01, unknown), 400, "INVALID_STATE");
		}

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void continuationBodyIsStrictJson() throws Exception {
		var browser = learner();
		ok(add(browser, "demo"));
		var before = snapshot();

		for (String body : schemaViolations("lessonId", INTRO)) {
			assertError(browser.send("PUT", "/api/me/courses/demo/continuation", body), 400, "INVALID_REQUEST");
		}
		assertError(browser.send("PUT", "/api/me/courses/demo/continuation", "{\"lastOpenedLessonId\":\"" + INTRO
				+ "\"}"), 400, "INVALID_REQUEST");

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void stateBodiesCannotChooseTheOwnerOrTheCourse() throws Exception {
		var browser = learner();
		String user = userId(browser);
		var before = snapshot();

		assertError(browser.send("PUT", "/api/me/lessons/" + INTRO + "/state",
				"{\"state\":\"completed\",\"userId\":\"" + user + "\"}"), 400, "INVALID_REQUEST");
		assertError(browser.send("PUT", "/api/me/exercises/" + E01 + "/state",
				"{\"state\":\"solved\",\"courseId\":\"demo\"}"), 400, "INVALID_REQUEST");

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void explicitAddTakesNoBody() throws Exception {
		var browser = learner();
		String user = userId(browser);
		var before = snapshot();

		for (String body : List.of("{}", "{\"courseId\":\"demo\"}", "{\"userId\":\"" + user + "\"}", "[]", "null",
				"\"demo\"", "{")) {
			assertError(browser.send("PUT", "/api/me/courses/demo", body), 400, "INVALID_REQUEST");
		}

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void everyProgressRouteRequiresASession() throws Exception {
		var anonymous = new ApiClient(port);
		var before = snapshot();

		for (var request : allRoutes()) {
			assertError(anonymous.send(request.method(), request.path(), request.body()), 401,
					"AUTHENTICATION_REQUIRED");
		}

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void everyProgressWriteRequiresTheCsrfToken() throws Exception {
		var browser = learner();
		ok(add(browser, EJEMPLO));
		var before = snapshot();

		for (var request : allRoutes()) {
			if (request.method().equals("GET")) continue;
			assertError(browser.send(request.method(), request.path(), request.body(), null), 403, "CSRF_REJECTED");
			assertError(browser.send(request.method(), request.path(), request.body(), "not-the-token"), 403,
					"CSRF_REJECTED");
		}

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void aStaleSessionIsRejectedOnEveryProgressRouteWithoutWriting() throws Exception {
		var browser = learner();
		String user = userId(browser);
		ok(add(browser, EJEMPLO));
		// What the console reset does to the version; the console itself is covered separately.
		jdbc.update("update code_academy.users set credentials_version = credentials_version + 1 where id = ?::uuid",
				user);
		var before = snapshot();

		for (var request : allRoutes()) {
			assertError(browser.send(request.method(), request.path(), request.body()), 401,
					"AUTHENTICATION_REQUIRED");
		}

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void aUserWithProgressCannotBeDeletedBecauseNothingCascades() throws Exception {
		var browser = learner();
		String user = userId(browser);
		ok(lessonState(browser, INTRO, "completed"));
		ok(exerciseState(browser, E01, "solved"));
		var before = snapshot();

		assertThatThrownBy(() -> jdbc.update("delete from code_academy.users where id = ?::uuid", user))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("enrollments_user_fkey");
		assertThatThrownBy(() -> jdbc.update("delete from code_academy.enrollments where user_id = ?::uuid", user))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("delete from code_academy.lessons where id = ?", INTRO))
			.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(snapshot()).isEqualTo(before);
	}

	private record Request(String method, String path, String body) {
	}

	/** The six routes with valid input, so only the missing session, token or credentials can reject them. */
	private static List<Request> allRoutes() {
		return List.of(new Request("GET", "/api/me/courses", null),
				new Request("PUT", "/api/me/courses/" + EJEMPLO, null),
				new Request("GET", "/api/me/courses/" + EJEMPLO + "/progress", null),
				new Request("PUT", "/api/me/lessons/" + EJEMPLO_LESSON + "/state", state("completed")),
				new Request("PUT", "/api/me/exercises/" + EJEMPLO_E01 + "/state", state("solved")),
				new Request("PUT", "/api/me/courses/" + EJEMPLO + "/continuation", "{\"lessonId\":\"" + EJEMPLO_LESSON + "\"}"));
	}

	/** Bodies that break the schema of a one-string-field object; a missing body is the null entry's absence. */
	private static List<String> schemaViolations(String field, String valid) {
		String pair = "\"" + field + "\":\"" + valid + "\"";
		return java.util.Arrays.asList(
				null,
				"",
				"{}",
				"null",
				"[]",
				"\"" + valid + "\"",
				"{" + pair + "," + pair + "}",
				"{" + pair + ",\"" + field + "\":\"other\"}",
				"{" + pair + ",\"extra\":1}",
				"{" + pair + ",\"extra\":null}",
				"{\"" + field + "\":1}",
				"{\"" + field + "\":true}",
				"{\"" + field + "\":[\"" + valid + "\"]}",
				"{\"" + field + "\":{\"value\":\"" + valid + "\"}}",
				"{\"" + field + "\":null}",
				"{\"" + field.toUpperCase() + "\":\"" + valid + "\"}",
				"{" + pair,
				"[{" + pair + "}]");
	}

	private static HttpResponse<String> send(ApiClient browser, String route, String segment, String body)
			throws Exception {
		String[] parts = route.split(" ", 2);
		return browser.send(parts[0], parts[1].replace("{id}", segment), body);
	}
}
