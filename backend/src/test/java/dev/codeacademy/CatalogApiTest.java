package dev.codeacademy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static dev.codeacademy.ApiClient.assertError;
import static dev.codeacademy.ApiClient.credentials;
import static dev.codeacademy.ApiClient.json;
import static dev.codeacademy.RegistrationApiTest.PASSWORD;
import static dev.codeacademy.RegistrationApiTest.unique;
import static org.assertj.core.api.Assertions.assertThat;

/** The catalog routes against the real content directory and the catalog synchronized at startup. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogApiTest {

	static final Path CONTENT = Path.of("../content");
	static final String LESSON = "java-02.01";
	static final String HELLO = "java-02.01-r-2.1-hello";

	@Autowired JdbcTemplate jdbc;
	@LocalServerPort int port;

	ApiClient browser;

	@BeforeEach
	void loggedIn() throws Exception {
		browser = new ApiClient(port);
		String username = unique("Lector_");
		assertThat(browser.send("POST", "/api/auth/register", credentials(username, PASSWORD)).statusCode())
				.isEqualTo(201);
		assertThat(browser.send("POST", "/api/auth/login", credentials(username, PASSWORD)).statusCode())
				.isEqualTo(200);
	}

	@Test
	void everyCatalogRouteRequiresASession() throws Exception {
		var anonymous = new ApiClient(port);
		for (String path : List.of("/api/courses", "/api/courses/java", "/api/lessons/" + LESSON,
				"/api/resources/" + HELLO)) {
			assertError(anonymous.get(path), 401, "AUTHENTICATION_REQUIRED");
			var download = anonymous.download(path, "text/html,*/*;q=0.8");
			assertThat(download.statusCode()).isEqualTo(401);
			assertThat(new String(download.body(), StandardCharsets.UTF_8)).doesNotContain("IO.println");
		}
	}

	@Test
	void exerciseTitlesAreTheSameInTheApiAndTheCatalog() throws Exception {
		var api = new ArrayList<String>();
		json(browser.get("/api/lessons/" + LESSON)).path("exercises").forEach(e -> api.add(e.path("title").asString()));
		assertThat(api).isEqualTo(jdbc.queryForList(
				"select title from code_academy.exercises where lesson_id = ? order by position", String.class, LESSON));
	}

	@Test
	void exerciseTitlesInTheApiAreExactlyTheManifestTitles() throws Exception {
		var manifest = new ArrayList<String>();
		for (JsonNode lesson : JsonMapper.builder().build().readTree(CONTENT.resolve("java/manifest.json").toFile())
			.path("lessons")) {
			if (lesson.path("id").asString().equals(LESSON)) {
				lesson.path("exercises").forEach(e -> manifest.add(e.path("title").asString()));
			}
		}
		var api = new ArrayList<String>();
		json(browser.get("/api/lessons/" + LESSON)).path("exercises").forEach(e -> api.add(e.path("title").asString()));

		assertThat(manifest).hasSize(9).contains("¿Qué main se ejecuta?");
		assertThat(api).isEqualTo(manifest);
	}

	@Test
	void coursesListsOnlyJava() throws Exception {
		var response = browser.get("/api/courses");

		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
		var body = json(response);
		assertThat(body.propertyNames()).containsExactly("courses");
		assertThat(body.path("courses").size()).isEqualTo(1);
		JsonNode java = body.path("courses").get(0);
		assertThat(java.propertyNames()).containsExactlyInAnyOrder("id", "title");
		assertThat(java.path("id").asString()).isEqualTo("java");
		assertThat(java.path("title").asString()).isEqualTo("Java");
	}

	@Test
	void courseReturnsTheFullSyllabusInOrderWithEveryLessonAvailable() throws Exception {
		var response = browser.get("/api/courses/java");

		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		var body = json(response);
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("course", "phases", "lessons");
		assertThat(body.path("course").path("id").asString()).isEqualTo("java");
		JsonNode phases = body.path("phases");
		assertThat(phases.size()).isEqualTo(21);
		List<String> phaseIds = new ArrayList<>();
		for (int i = 0; i < phases.size(); i++) {
			JsonNode phase = phases.get(i);
			assertThat(phase.propertyNames()).containsExactlyInAnyOrder("id", "title", "position");
			assertThat(phase.path("position").asInt()).isEqualTo(i + 1);
			phaseIds.add(phase.path("id").asString());
		}
		assertThat(phaseIds).startsWith("java-phase-00", "java-phase-01", "java-phase-02").endsWith("java-phase-20");
		assertThat(phases.get(2).path("title").asString()).isEqualTo("Fundamentos de Java");
		assertThat(phases.get(20).path("title").asString()).isEqualTo("Desarrollo profesional");
		JsonNode lessons = body.path("lessons");
		assertThat(lessons.size()).isEqualTo(199);
		List<String> ids = new ArrayList<>();
		List<String> available = new ArrayList<>();
		for (int i = 0; i < lessons.size(); i++) {
			JsonNode lesson = lessons.get(i);
			assertThat(lesson.propertyNames()).containsExactlyInAnyOrder("id", "courseId", "title", "position",
					"phaseId", "available");
			assertThat(lesson.path("position").asInt()).isEqualTo(i + 1);
			assertThat(phaseIds).contains(lesson.path("phaseId").asString());
			assertThat(lesson.path("courseId").asString()).isEqualTo("java");
			assertThat(lesson.path("available").isBoolean()).isTrue();
			ids.add(lesson.path("id").asString());
			if (lesson.path("available").asBoolean()) available.add(lesson.path("id").asString());
		}
		// The H5 import made all 199 lessons available; the syllabus still lists them in order.
		assertThat(available).hasSize(199).isEqualTo(ids).contains(LESSON);
		assertThat(lessons.get(0).path("id").asString()).isEqualTo("java-00.01");
		assertThat(lessons.get(0).path("title").asString()).isEqualTo("Qué es programar");
		assertThat(lessons.get(12).path("id").asString()).isEqualTo(LESSON);
		assertThat(lessons.get(12).path("phaseId").asString()).isEqualTo("java-phase-02");
		assertThat(lessons.get(0).path("phaseId").asString()).isEqualTo("java-phase-00");
		assertThat(lessons.get(198).path("phaseId").asString()).isEqualTo("java-phase-20");
	}

	@Test
	void availableLessonHasBodyExercisesResourcesAndItsAvailableNeighbours() throws Exception {
		var response = browser.get("/api/lessons/" + LESSON);

		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		var body = json(response);
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("lesson", "bodyHtml", "exercises", "resources",
				"previousLessonId", "nextLessonId");
		assertThat(body.path("lesson").propertyNames()).containsExactlyInAnyOrder("id", "courseId", "title", "position",
				"phaseId", "available");
		assertThat(body.path("lesson").path("id").asString()).isEqualTo(LESSON);
		assertThat(body.path("lesson").path("phaseId").asString()).isEqualTo("java-phase-02");
		assertThat(body.path("lesson").path("available").asBoolean()).isTrue();
		assertThat(body.path("lesson").path("position").asInt()).isEqualTo(13);
		assertThat(body.path("previousLessonId").asString()).isEqualTo("java-01.09");
		assertThat(body.path("nextLessonId").asString()).isEqualTo("java-02.02");
		// The ends of the syllabus have no neighbour on their outer side.
		JsonNode first = json(browser.get("/api/lessons/java-00.01"));
		assertThat(first.path("previousLessonId").isNull()).isTrue();
		assertThat(first.path("nextLessonId").asString()).isEqualTo("java-00.02");
		JsonNode last = json(browser.get("/api/lessons/java-20.06"));
		assertThat(last.path("previousLessonId").asString()).isEqualTo("java-20.05");
		assertThat(last.path("nextLessonId").isNull()).isTrue();

		String bodyHtml = body.path("bodyHtml").asString();
		assertThat(bodyHtml).contains("<a href=\"#java-02.01-e08\">ejercicio 8</a>")
				.doesNotContain("Ejercicios prácticos resueltos", "Ver solución", "<details");

		JsonNode exercises = body.path("exercises");
		assertThat(exercises.size()).isEqualTo(9);
		List<String> ids = new ArrayList<>();
		for (JsonNode exercise : exercises) {
			assertThat(exercise.propertyNames()).containsExactlyInAnyOrder("id", "lessonId", "courseId", "title",
					"statementHtml", "solutionHtml");
			assertThat(exercise.path("lessonId").asString()).isEqualTo(LESSON);
			assertThat(exercise.path("courseId").asString()).isEqualTo("java");
			assertThat(exercise.path("solutionHtml").asString()).startsWith("<details>");
			ids.add(exercise.path("id").asString());
		}
		assertThat(ids).containsExactly("java-02.01-e01", "java-02.01-e02", "java-02.01-e03", "java-02.01-e04",
				"java-02.01-e05", "java-02.01-e06", "java-02.01-e07", "java-02.01-e08", "java-02.01-e09");
		assertThat(exercises.get(7).path("title").asString()).isEqualTo("¿Qué main se ejecuta?");

		// The 22 H4 files keep their identities; H5 adds one ZIP per linked folder.
		JsonNode resources = body.path("resources");
		assertThat(resources.size()).isEqualTo(41);
		int files = 0;
		int zips = 0;
		for (JsonNode resource : resources) {
			assertThat(resource.propertyNames()).containsExactlyInAnyOrder("resourceId", "url", "mediaType", "fileName");
			String id = resource.path("resourceId").asString();
			assertThat(resource.path("url").asString()).isEqualTo("/api/resources/" + id);
			if (id.startsWith("java-02.01-r-")) {
				files++;
				assertThat(resource.path("mediaType").asString()).isEqualTo("text/plain; charset=utf-8");
				assertThat(resource.path("fileName").asString()).endsWith(".java").doesNotContain("java-02.01-r-", "/");
			}
			else {
				zips++;
				assertThat(id).startsWith("java-02.01-zip-");
				assertThat(resource.path("mediaType").asString()).isEqualTo("application/zip");
				assertThat(resource.path("fileName").asString()).endsWith(".zip").doesNotContain("java-02.01-zip-", "/");
			}
		}
		assertThat(files).isEqualTo(22);
		assertThat(zips).isEqualTo(19);
		JsonNode hello = find(resources, HELLO);
		assertThat(hello.path("fileName").asString()).isEqualTo("Hello.java");
		assertThat(find(resources, "java-02.01-r-ej08-programa-b-report").path("fileName").asString())
				.isEqualTo("Report.java");
		assertThat(response.body()).doesNotContain("localPath", "sourcePath", "href=\\\"codigo/", "fase-02");
	}

	@Test
	void unknownCourseLessonOrResourceIs404() throws Exception {
		var before = TestDatabase.snapshot(jdbc);

		assertError(browser.get("/api/courses/python"), 404, "NOT_FOUND");
		assertError(browser.get("/api/courses/java-02.01"), 404, "NOT_FOUND");
		assertError(browser.get("/api/lessons/java-99.99"), 404, "NOT_FOUND");
		assertError(browser.get("/api/lessons/java"), 404, "NOT_FOUND");
		assertError(browser.get("/api/lessons/java-02.01-e01"), 404, "NOT_FOUND");
		assertError(browser.get("/api/resources/java-02.01-r-unknown"), 404, "NOT_FOUND");
		assertError(browser.get("/api/resources/" + LESSON), 404, "NOT_FOUND");

		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void resourceIsDownloadedWithItsOriginalNameAndSafeHeaders() throws Exception {
		byte[] expected = Files.readAllBytes(CONTENT.resolve("java/resources/" + HELLO + ".java"));

		// Browsers navigating to a download send text/html first: Accept never blocks a resource.
		for (String accept : List.of("text/html,application/xhtml+xml,*/*;q=0.8", "application/json", "*/*")) {
			var response = browser.download("/api/resources/" + HELLO, accept);

			assertThat(response.statusCode()).as(accept).isEqualTo(200);
			assertThat(response.body()).isEqualTo(expected);
			var headers = response.headers();
			assertThat(MediaType.parseMediaType(headers.firstValue("Content-Type").orElseThrow()))
					.isEqualTo(MediaType.parseMediaType("text/plain;charset=utf-8"));
			assertThat(headers.allValues("Content-Disposition"))
					.containsExactly("attachment; filename=\"Hello.java\"; filename*=UTF-8''Hello.java");
			assertThat(headers.allValues("X-Content-Type-Options")).containsExactly("nosniff");
			assertThat(headers.allValues("Content-Security-Policy")).containsExactly("sandbox");
			assertThat(headers.firstValue("Cache-Control").orElse("")).contains("no-store");
			assertThat(headers.firstValue("Cache-Control").orElse("")).doesNotContain("public");
		}
		assertThat(new String(expected, StandardCharsets.UTF_8)).contains("IO.println");
	}

	@Test
	void aResourceWhoseIdHasAUnicodeLetterDownloadsThroughItsEncodedLink() throws Exception {
		String id = "java-12.16-r-presupuesto-año-2026txt-0979a0bb";
		byte[] expected = Files.readAllBytes(CONTENT.resolve("java/resources/" + id));

		var response = browser.download("/api/resources/" + java.net.URLEncoder.encode(id, StandardCharsets.UTF_8), "*/*");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo(expected);
		assertThat(response.headers().allValues("X-Content-Type-Options")).containsExactly("nosniff");
	}

	@Test
	void pathLikeResourceIdsNeverReachAFile() throws Exception {
		for (String path : List.of("/api/resources/..%2F..%2Fjava%2Fmanifest.json",
				"/api/resources/..%2F..%2Fcontent%2Fjava%2Fresources%2F" + HELLO + ".java",
				"/api/resources/" + HELLO + ".java", "/api/resources/java%2Fresources%2F" + HELLO + ".java",
				"/api/resources/%2e%2e", "/api/resources/..", "/api/resources/" + HELLO + "%00",
				"/api/resources/" + HELLO + "%2F", "/api/resources/java-02.01-r-2.1-HELLO")) {
			var response = browser.download(path, null);
			String body = new String(response.body(), StandardCharsets.UTF_8);

			assertThat(response.statusCode()).as(path).isIn(400, 404);
			assertThat(body).as(path).doesNotContain("IO.println", "schemaVersion", "versionRef");
			assertThat(response.headers().firstValue("Content-Disposition")).as(path).isEmpty();
		}
	}

	@Test
	void catalogRowsMatchTheManifestAfterStartup() {
		assertThat(jdbc.queryForList("select id, title from code_academy.courses"))
				.containsExactly(java.util.Map.of("id", "java", "title", "Java"));
		assertThat(jdbc.queryForObject("select count(*) from code_academy.lessons where course_id = 'java'",
				Integer.class)).isEqualTo(199);
		assertThat(jdbc.queryForObject("select count(*) from code_academy.lessons where available",
				Integer.class)).isEqualTo(199);
		assertThat(jdbc.queryForObject("select count(*) from code_academy.lessons where source_available",
				Integer.class)).isEqualTo(199);
		assertThat(jdbc.queryForList(
				"select id from code_academy.exercises where lesson_id = ? and course_id = 'java' order by position",
				String.class, LESSON)).hasSize(9).startsWith("java-02.01-e01").endsWith("java-02.01-e09");
		assertThat(jdbc.queryForList("""
				select c.conrelid::regclass::text as table_name, c.contype::text as type,
				       array_to_string(array(select a.attname from unnest(c.conkey) with ordinality k(n, i)
				           join pg_attribute a on a.attrelid = c.conrelid and a.attnum = k.n order by k.i), ',') as columns
				from pg_constraint c join pg_namespace n on n.oid = c.connamespace
				where n.nspname = 'code_academy' and c.contype in ('u', 'f')
				  and c.conrelid::regclass::text in ('code_academy.phases', 'code_academy.lessons', 'code_academy.exercises')
				""")).extracting(row -> row.get("table_name") + " " + row.get("type") + " " + row.get("columns"))
				.contains("code_academy.phases u course_id,position", "code_academy.phases u id,course_id",
						"code_academy.phases f course_id", "code_academy.lessons u course_id,position",
						"code_academy.lessons u id,course_id", "code_academy.lessons f course_id",
						"code_academy.lessons f phase_id,course_id", "code_academy.exercises u id,course_id",
						"code_academy.exercises f lesson_id,course_id");
		assertThat(jdbc.queryForObject("select count(*) from code_academy.phases where course_id = 'java'",
				Integer.class)).isEqualTo(21);
		assertThat(jdbc.queryForObject("select count(*) from code_academy.lessons where phase_id is null",
				Integer.class)).isZero();
		// Neither bodies nor HTML are stored: the catalog has no text column beyond titles.
		assertThat(jdbc.queryForList("""
				select table_name || '.' || column_name from information_schema.columns
				where table_schema = 'code_academy' and table_name in ('courses', 'phases', 'lessons', 'exercises')
				""", String.class)).containsExactlyInAnyOrder("courses.id", "courses.title", "phases.id",
				"phases.course_id", "phases.title", "phases.position", "lessons.id", "lessons.course_id",
				"lessons.title", "lessons.position", "lessons.phase_id", "lessons.available",
				"lessons.source_available", "exercises.id", "exercises.course_id", "exercises.lesson_id",
				"exercises.title", "exercises.position");
		// No cascading deletes anywhere in the catalog.
		assertThat(jdbc.queryForObject("""
				select count(*) from pg_constraint c join pg_namespace n on n.oid = c.connamespace
				where n.nspname = 'code_academy' and c.contype = 'f' and c.confdeltype <> 'a'
				""", Integer.class)).isZero();
	}

	private static JsonNode find(JsonNode resources, String id) {
		for (JsonNode resource : resources) {
			if (resource.path("resourceId").asString().equals(id)) return resource;
		}
		throw new AssertionError(id);
	}
}
