package dev.codeacademy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.codeacademy.content.ExampleContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static dev.codeacademy.ApiClient.assertError;
import static dev.codeacademy.ApiClient.credentials;
import static dev.codeacademy.ApiClient.json;
import static dev.codeacademy.RegistrationApiTest.PASSWORD;
import static dev.codeacademy.RegistrationApiTest.unique;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The catalog routes against the kit's two example courses, installed by the real importer, and the catalog
 * synchronized at startup.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogApiTest {

	static final String COURSE = ExampleContent.COURSE;
	static final String LESSON = ExampleContent.LESSON;
	static final String SCRIPT = "ejemplo.ficheros-json-r-contarsh-ef17ab44";
	static final String IMAGE = "ejemplo.ficheros-json-r-estructurasvg-1e17e92d";

	@DynamicPropertySource
	static void content(DynamicPropertyRegistry registry) {
		registry.add("code-academy.content-dir", () -> ExampleContent.root().toString());
	}

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
		for (String path : List.of("/api/courses", "/api/courses/" + COURSE, "/api/lessons/" + LESSON,
				"/api/resources/" + SCRIPT)) {
			assertError(anonymous.get(path), 401, "AUTHENTICATION_REQUIRED");
			var download = anonymous.download(path, "text/html,*/*;q=0.8");
			assertThat(download.statusCode()).isEqualTo(401);
			assertThat(new String(download.body(), StandardCharsets.UTF_8)).doesNotContain("Clientes de");
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
		for (JsonNode lesson : JsonMapper.builder().build()
			.readTree(ExampleContent.root().resolve("ejemplo/manifest.json").toFile()).path("lessons")) {
			if (lesson.path("id").asString().equals(LESSON)) {
				lesson.path("exercises").forEach(e -> manifest.add(e.path("title").asString()));
			}
		}
		var api = new ArrayList<String>();
		json(browser.get("/api/lessons/" + LESSON)).path("exercises").forEach(e -> api.add(e.path("title").asString()));

		assertThat(manifest).containsExactly("Un pedido de la tienda", "La coma de más");
		assertThat(api).isEqualTo(manifest);
	}

	@Test
	void coursesListsEveryInstalledCourse() throws Exception {
		var response = browser.get("/api/courses");

		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
		var body = json(response);
		assertThat(body.propertyNames()).containsExactly("courses");
		List<String> courses = new ArrayList<>();
		for (JsonNode course : body.path("courses")) {
			assertThat(course.propertyNames()).containsExactlyInAnyOrder("id", "title", "description", "recommended");
			courses.add(course.path("id").asString() + " " + course.path("title").asString());
		}
		assertThat(courses).containsExactlyInAnyOrder("ejemplo Curso de ejemplo",
				"ejemplo-destino Curso de destino de ejemplo");
		// From curso.json (H7, T6): descripcion and conviene_saber_antes. «inicial» is not installed: no title.
		JsonNode ejemplo = null;
		for (JsonNode course : body.path("courses")) if (course.path("id").asString().equals("ejemplo")) ejemplo = course;
		assertThat(ejemplo.path("description").asString())
			.isEqualTo("Un curso pequeño que cumple FORMATO.md, para las pruebas del kit.");
		assertThat(ejemplo.path("recommended")).hasSize(1);
		assertThat(ejemplo.path("recommended").get(0).path("id").asString()).isEqualTo("inicial");
		assertThat(ejemplo.path("recommended").get(0).path("title").isNull()).isTrue();
	}

	@Test
	void courseReturnsTheFullSyllabusInOrderWithThePendingModule() throws Exception {
		var response = browser.get("/api/courses/" + COURSE);

		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		var body = json(response);
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("course", "phases", "lessons");
		assertThat(body.path("course").path("id").asString()).isEqualTo(COURSE);
		JsonNode phases = body.path("phases");
		assertThat(phases.size()).isEqualTo(1);
		assertThat(phases.get(0).propertyNames()).containsExactlyInAnyOrder("id", "title", "position");
		assertThat(phases.get(0).path("id").asString()).isEqualTo("ejemplo.fase.primeros-pasos");
		assertThat(phases.get(0).path("title").asString()).isEqualTo("Primeros pasos");
		assertThat(phases.get(0).path("position").asInt()).isEqualTo(1);
		JsonNode lessons = body.path("lessons");
		assertThat(lessons.size()).isEqualTo(2);
		List<String> rows = new ArrayList<>();
		for (int i = 0; i < lessons.size(); i++) {
			JsonNode lesson = lessons.get(i);
			assertThat(lesson.propertyNames()).containsExactlyInAnyOrder("id", "courseId", "title", "position",
					"phaseId", "available");
			assertThat(lesson.path("position").asInt()).isEqualTo(i + 1);
			assertThat(lesson.path("courseId").asString()).isEqualTo(COURSE);
			assertThat(lesson.path("phaseId").asString()).isEqualTo("ejemplo.fase.primeros-pasos");
			rows.add(lesson.path("id").asString() + " " + lesson.path("title").asString() + " "
					+ lesson.path("available").asBoolean());
		}
		// The pending module of curso.json is listed in its place, not available.
		assertThat(rows).containsExactly(LESSON + " Ficheros JSON true", ExampleContent.PENDING + " Ficheros YAML false");
	}

	@Test
	void availableLessonHasBodyExercisesResourcesAndNoUnavailableNeighbour() throws Exception {
		var response = browser.get("/api/lessons/" + LESSON);

		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		var body = json(response);
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("lesson", "bodyHtml", "exercises", "resources",
				"previousLessonId", "nextLessonId");
		assertThat(body.path("lesson").propertyNames()).containsExactlyInAnyOrder("id", "courseId", "title", "position",
				"phaseId", "available");
		assertThat(body.path("lesson").path("id").asString()).isEqualTo(LESSON);
		assertThat(body.path("lesson").path("phaseId").asString()).isEqualTo("ejemplo.fase.primeros-pasos");
		assertThat(body.path("lesson").path("available").asBoolean()).isTrue();
		assertThat(body.path("lesson").path("position").asInt()).isEqualTo(1);
		// The first module has no previous one, and the next one is pending: neighbours are available lessons only.
		assertThat(body.path("previousLessonId").isNull()).isTrue();
		assertThat(body.path("nextLessonId").isNull()).isTrue();

		String bodyHtml = body.path("bodyHtml").asString();
		assertThat(bodyHtml).contains("<a href=\"#12-objetos-y-listas\">")
				.doesNotContain("Ejercicios prácticos resueltos", "Ver solución", "Pista 1");

		JsonNode exercises = body.path("exercises");
		List<String> ids = new ArrayList<>();
		for (JsonNode exercise : exercises) {
			assertThat(exercise.propertyNames()).containsExactlyInAnyOrder("id", "lessonId", "courseId", "title",
					"statementHtml", "solutionHtml");
			assertThat(exercise.path("lessonId").asString()).isEqualTo(LESSON);
			assertThat(exercise.path("courseId").asString()).isEqualTo(COURSE);
			assertThat(exercise.path("statementHtml").asString()).contains("<summary>Pista 1</summary>");
			assertThat(exercise.path("solutionHtml").asString()).startsWith("<details>");
			ids.add(exercise.path("id").asString());
		}
		assertThat(ids).containsExactly(ExampleContent.E01, ExampleContent.E02);
		assertThat(exercises.get(1).path("title").asString()).isEqualTo("La coma de más");

		// Eleven code files and one image, plus one ZIP per linked folder (five).
		JsonNode resources = body.path("resources");
		assertThat(resources.size()).isEqualTo(17);
		int files = 0;
		int zips = 0;
		for (JsonNode resource : resources) {
			assertThat(resource.propertyNames()).containsExactlyInAnyOrder("resourceId", "url", "mediaType", "fileName");
			String id = resource.path("resourceId").asString();
			assertThat(resource.path("url").asString()).isEqualTo("/api/resources/" + id);
			if (id.startsWith(LESSON + "-r-")) {
				files++;
				assertThat(resource.path("mediaType").asString()).isIn("text/plain; charset=utf-8", "image/svg+xml");
				assertThat(resource.path("fileName").asString()).doesNotContain(LESSON, "/");
			}
			else {
				zips++;
				assertThat(id).startsWith(LESSON + "-zip-");
				assertThat(resource.path("mediaType").asString()).isEqualTo("application/zip");
				assertThat(resource.path("fileName").asString()).endsWith(".zip").doesNotContain(LESSON, "/");
			}
		}
		assertThat(files).isEqualTo(12);
		assertThat(zips).isEqualTo(5);
		assertThat(find(resources, SCRIPT).path("fileName").asString()).isEqualTo("contar.sh");
		assertThat(find(resources, IMAGE).path("fileName").asString()).isEqualTo("estructura.svg");
		assertThat(find(resources, "ejemplo.ficheros-json-zip-ej01-un-pedido-550beecd").path("fileName").asString())
				.isEqualTo("ej01-un-pedido.zip");
		// Only the GitHub address of a link to another course names a source folder (until H8).
		assertThat(response.body()).doesNotContain("localPath", "sourcePath", "href=\\\"codigo/", "href=\\\"fase-01");
	}

	@Test
	void aLessonOfTheSecondCourseIsServedToo() throws Exception {
		var body = json(browser.get("/api/lessons/ejemplo-destino.mi-primer-repositorio"));

		assertThat(body.path("lesson").path("courseId").asString()).isEqualTo("ejemplo-destino");
		assertThat(body.path("exercises").get(0).path("id").asString())
				.isEqualTo("ejemplo-destino.mi-primer-repositorio.el-readme-del-proyecto");
	}

	@Test
	void unknownCourseLessonOrResourceIs404() throws Exception {
		var before = TestDatabase.snapshot(jdbc);

		assertError(browser.get("/api/courses/python"), 404, "NOT_FOUND");
		assertError(browser.get("/api/courses/" + LESSON), 404, "NOT_FOUND");
		assertError(browser.get("/api/lessons/ejemplo.no-existe"), 404, "NOT_FOUND");
		assertError(browser.get("/api/lessons/" + COURSE), 404, "NOT_FOUND");
		assertError(browser.get("/api/lessons/" + ExampleContent.E01), 404, "NOT_FOUND");
		assertError(browser.get("/api/resources/" + LESSON + "-r-unknown"), 404, "NOT_FOUND");
		assertError(browser.get("/api/resources/" + LESSON), 404, "NOT_FOUND");

		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	/** The pending module is in the catalog but has no body to serve. */
	@Test
	void thePendingModuleIsNotServed() throws Exception {
		var response = browser.get("/api/lessons/" + ExampleContent.PENDING);

		assertThat(response.statusCode()).as(response.body()).isIn(404, 409);
		assertThat(response.body()).doesNotContain("bodyHtml");
	}

	@Test
	void resourceIsDownloadedWithItsOriginalNameAndSafeHeaders() throws Exception {
		byte[] expected = Files.readAllBytes(ExampleContent.root().resolve("ejemplo/resources/" + SCRIPT));

		// Browsers navigating to a download send text/html first: Accept never blocks a resource.
		for (String accept : List.of("text/html,application/xhtml+xml,*/*;q=0.8", "application/json", "*/*")) {
			var response = browser.download("/api/resources/" + SCRIPT, accept);

			assertThat(response.statusCode()).as(accept).isEqualTo(200);
			assertThat(response.body()).isEqualTo(expected);
			var headers = response.headers();
			assertThat(MediaType.parseMediaType(headers.firstValue("Content-Type").orElseThrow()))
					.isEqualTo(MediaType.parseMediaType("text/plain;charset=utf-8"));
			assertThat(headers.allValues("Content-Disposition"))
					.containsExactly("attachment; filename=\"contar.sh\"; filename*=UTF-8''contar.sh");
			assertThat(headers.allValues("X-Content-Type-Options")).containsExactly("nosniff");
			assertThat(headers.allValues("Content-Security-Policy")).containsExactly("sandbox");
			assertThat(headers.firstValue("Cache-Control").orElse("")).contains("no-store");
			assertThat(headers.firstValue("Cache-Control").orElse("")).doesNotContain("public");
		}
		assertThat(new String(expected, StandardCharsets.UTF_8)).contains("Clientes de");
	}

	/**
	 * Resources with non-ASCII letters and spaces in their names download through their percent-encoded address, as a
	 * browser requests the link of the lesson, with their exact names in Content-Disposition: filename* in UTF-8,
	 * spaces as %20 and never as «+». IDs have no spaces (the generator turns them into hyphens), so the address has
	 * only letters to encode.
	 */
	@Test
	void resourcesWithNonAsciiLettersAndSpacesDownloadWithTheirExactNames() throws Exception {
		assertDownload("ejemplo.ficheros-json-r-año-pequeñocsv-8ab61787",
				"/api/resources/ejemplo.ficheros-json-r-a%C3%B1o-peque%C3%B1ocsv-8ab61787",
				"attachment; filename=\"a_o-peque_o.csv\"; filename*=UTF-8''a%C3%B1o-peque%C3%B1o.csv");
		assertDownload("ejemplo.ficheros-json-r-canción-del-añotxt-e0a88b36",
				"/api/resources/ejemplo.ficheros-json-r-canci%C3%B3n-del-a%C3%B1otxt-e0a88b36",
				"attachment; filename=\"canci_n_del_a_o.txt\"; filename*=UTF-8''canci%C3%B3n%20del%20a%C3%B1o.txt");
	}

	private void assertDownload(String id, String path, String disposition) throws Exception {
		assertThat(java.net.URI.create(path).getPath()).isEqualTo("/api/resources/" + id);
		byte[] expected = Files.readAllBytes(ExampleContent.root().resolve("ejemplo/resources/" + id));

		var response = browser.download(path, "*/*");

		assertThat(response.statusCode()).as(path).isEqualTo(200);
		assertThat(response.body()).isEqualTo(expected);
		assertThat(response.headers().allValues("Content-Disposition")).containsExactly(disposition);
		assertThat(response.headers().firstValue("Content-Disposition").orElseThrow()).doesNotContain("+");
		assertThat(response.headers().allValues("X-Content-Type-Options")).containsExactly("nosniff");
	}

	@Test
	void theImageIsServedSandboxed() throws Exception {
		var response = browser.download("/api/resources/" + IMAGE, "image/avif,image/webp,*/*");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo(Files.readAllBytes(ExampleContent.root().resolve("ejemplo/resources/" + IMAGE)));
		assertThat(response.headers().allValues("X-Content-Type-Options")).containsExactly("nosniff");
		assertThat(response.headers().allValues("Content-Security-Policy")).containsExactly("sandbox");
	}

	@Test
	void pathLikeResourceIdsNeverReachAFile() throws Exception {
		for (String path : List.of("/api/resources/..%2F..%2Fejemplo%2Fmanifest.json",
				"/api/resources/..%2F..%2Fcontent%2Fejemplo%2Fresources%2F" + SCRIPT,
				"/api/resources/" + SCRIPT + ".sh", "/api/resources/ejemplo%2Fresources%2F" + SCRIPT,
				"/api/resources/%2e%2e", "/api/resources/..", "/api/resources/" + SCRIPT + "%00",
				"/api/resources/" + SCRIPT + "%2F", "/api/resources/" + SCRIPT.toUpperCase())) {
			var response = browser.download(path, null);
			String body = new String(response.body(), StandardCharsets.UTF_8);

			assertThat(response.statusCode()).as(path).isIn(400, 404);
			assertThat(body).as(path).doesNotContain("Clientes de", "schemaVersion", "versionRef");
			assertThat(response.headers().firstValue("Content-Disposition")).as(path).isEmpty();
		}
	}

	@Test
	void catalogRowsMatchTheManifestsAfterStartup() {
		assertThat(jdbc.queryForList("select id || ' ' || title from code_academy.courses", String.class))
				.containsExactlyInAnyOrder("ejemplo Curso de ejemplo", "ejemplo-destino Curso de destino de ejemplo");
		assertThat(jdbc.queryForObject("select count(*) from code_academy.lessons where course_id = 'ejemplo'",
				Integer.class)).isEqualTo(2);
		assertThat(jdbc.queryForList("select id from code_academy.lessons where available", String.class))
				.containsExactlyInAnyOrder(LESSON, "ejemplo-destino.mi-primer-repositorio");
		assertThat(jdbc.queryForList("select id from code_academy.lessons where source_available",
				String.class)).containsExactlyInAnyOrder(LESSON, "ejemplo-destino.mi-primer-repositorio");
		assertThat(jdbc.queryForList(
				"select id from code_academy.exercises where lesson_id = ? and course_id = 'ejemplo' order by position",
				String.class, LESSON)).containsExactly(ExampleContent.E01, ExampleContent.E02);
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
		assertThat(jdbc.queryForObject("select count(*) from code_academy.phases where course_id = 'ejemplo'",
				Integer.class)).isEqualTo(1);
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
