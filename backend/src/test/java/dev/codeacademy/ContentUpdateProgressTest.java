package dev.codeacademy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import dev.codeacademy.catalog.CatalogConflictException;

import static dev.codeacademy.ApiClient.assertError;
import static dev.codeacademy.ProgressTestSupport.*;
import static dev.codeacademy.RegistrationApiTest.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;

/**
 * S5/S6 (T6): the real update from the H4 Java copy (only java-02.01 available) to the imported course (199 lessons,
 * 14 stages), with two learners, on a loopback database of its own. The H4 copy is assembled from a test fixture of
 * its manifest plus the files it names, which content/java still holds byte for byte; content/ is never written.
 */
class ContentUpdateProgressTest {

	static final Path H4_MANIFEST = Path.of("src/test/resources/h4-java/manifest.json");
	static final Path IMPORTED = CatalogApiTest.CONTENT;
	/** SHA-256 of every file the H4 manifest names, as H4 delivered them; the manifest itself carries no hashes. */
	static final Map<String, String> H4_FILES = Map.ofEntries(
		Map.entry("java/lessons/java-02.01.md", "f005134d134dc5f378377c39834fb98f55e45e2721cc86937e6bb14b6afa7bfd"),
		Map.entry("java/resources/java-02.01-r-1.10-hello.java", "00d83b675456f5c18ada44c6fd47a9e2c848c49e179e4dbd89181637264028fc"),
		Map.entry("java/resources/java-02.01-r-2.1-hello.java", "2fffbdd15d36ec887682115a62e7eb703de5f9b622a51e718406778483b1b932"),
		Map.entry("java/resources/java-02.01-r-2.2-order-status.java", "60319f291b8ae91257a2faa07400f024c8826ecfa7a93f96db48a92e27d4484f"),
		Map.entry("java/resources/java-02.01-r-2.3-order-status.java", "72c658b1407e52b58c648bff8a1112de4698929ab02c440fbd7e007ec899923a"),
		Map.entry("java/resources/java-02.01-r-2.4-opening-hours.java", "0d79e272a19268e5de006aa4ab0cce97a03aa7f760049608b2d011f68909129d"),
		Map.entry("java/resources/java-02.01-r-2.5-purchase-summary.java", "dd7d9e543a88a839775a6f9dbd233cea0b9510b91b0fed313565b013488409c1"),
		Map.entry("java/resources/java-02.01-r-2.6-special-characters.java", "6bf650fcf6caef6888c82c939586ab3eea0871c641035de2d3393c930ce5f2c2"),
		Map.entry("java/resources/java-02.01-r-2.7-bookshop-receipt.java", "fa4da8702fb1d1644bf54dea608fb3cb856da40e0e6a2d96b3f52ca09198e051"),
		Map.entry("java/resources/java-02.01-r-2.8-launch.java", "ddcdd2e3a328caba5260d8bf9edfa5e68e0f88a86e29eff542397e1f2ea303c2"),
		Map.entry("java/resources/java-02.01-r-ej01-store-card.java", "95dfe7b91aba59fc800dc55473cb802b0e8ac80fffab5b5d2f6987c73c745835"),
		Map.entry("java/resources/java-02.01-r-ej02-predict.java", "284c03735f2e74370b8bc71d9e58bce8ac8073e50635d5da26bf553bbbb229a1"),
		Map.entry("java/resources/java-02.01-r-ej03-delivery-notice.java", "b7cff831e8cf1c095b1e61d2e31d72a13f7b39e42ac55ff5285757e10f5ebc18"),
		Map.entry("java/resources/java-02.01-r-ej04-backup-notice.java", "85b7a9fedaa05c7b9419f4baf8a29854ed6ba15dbdb0a9f27b89f6a1556fbf54"),
		Map.entry("java/resources/java-02.01-r-ej04-backup-notice-readable.java", "cc5dd162b197eaa9849b06bbaca79ec88e1e0127daefe68a11e91fadab0c2efb"),
		Map.entry("java/resources/java-02.01-r-ej05-cafe-receipt.java", "f209b656ead68b29ec8002e3face3f0c0875abf1d40e736881266230bda7ec6b"),
		Map.entry("java/resources/java-02.01-r-ej05-cafe-receipt-compact.java", "ae81affb62c9d22c961e948759324ef6dac1918fa68c1d4108adfd6b2d37e9ef"),
		Map.entry("java/resources/java-02.01-r-ej06-shipping-rates.java", "904d2b7f81dddf4b376f7ba2bd20e2715cf2a0bf496c7e2a269f9ed675a7a714"),
		Map.entry("java/resources/java-02.01-r-ej06-shipping-rates-before.java", "ee0a02e6df6176510e9b6285b2a9a214198570b923d94aa9a38f0ad2568102c0"),
		Map.entry("java/resources/java-02.01-r-ej08-programa-a-report.java", "8d470fd48cbcd0485541ecd0f71748f2466641dd07cf60fe3ee886bb6ee37355"),
		Map.entry("java/resources/java-02.01-r-ej08-programa-b-report.java", "8f3083dcdeb41a158c9466b98b904d2d8a2241f88b6672b211816d61b74061b3"),
		Map.entry("java/resources/java-02.01-r-ej08-programa-c-report.java", "cc33410f7d591036861ccc0008e129d3b35276faf3ad302ec3e7f5e79c802e72"),
		Map.entry("java/resources/java-02.01-r-ej09-library-app.java", "da176e6cd839353d6f31ec03e8d07eb70554d32fc97d41903c2813e6e4c9e8ac"));
	static final String E01 = "java-02.01-e01";
	static final String E02 = "java-02.01-e02";
	static final String E03 = "java-02.01-e03";
	static final String STAGE_1 = "java-stage-01";
	static final String STAGE_2 = "java-stage-02";
	static final String STAGE_3 = "java-stage-03";
	static final String[] PROGRESS_TABLES = {"users", "enrollments", "lesson_states", "exercise_states", "stage_states"};

	@TempDir Path temp;

	@Test
	void theImportedCourseKeepsTwoLearnersProgressAndAConflictingCandidateChangesNothing() throws Exception {
		Path h4 = h4Copy(temp.resolve("h4"));
		String ana = unique("Ana_");
		String bruno = unique("Bruno_");
		try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"))
			.withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
				new PortBinding(Ports.Binding.bindIp("127.0.0.1"), new ExposedPort(5432))))) {
			postgres.start();
			var jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));

			JsonNode anaH4;
			JsonNode brunoH4;
			try (var server = start(postgres, h4)) {
				var first = learner(port(server), ana);
				var second = learner(port(server), bruno);
				ok(lessonState(first, JAVA_LESSON, "completed"));
				ok(exerciseState(first, E01, "solved"));
				ok(exerciseState(first, E02, "in_progress"));
				ok(continuation(first, "java", JAVA_LESSON));
				ok(lessonState(second, JAVA_LESSON, "not_completed"));
				ok(exerciseState(second, E01, "pending"));
				ok(exerciseState(second, E03, "solved"));
				ok(continuation(second, "java", JAVA_LESSON));
				// Really the H4 catalog: the next lesson cannot be marked yet.
				assertError(lessonState(first, JAVA_PENDING, "completed"), 409, "LESSON_UNAVAILABLE");

				anaH4 = ok(progress(first, "java"));
				brunoH4 = ok(progress(second, "java"));
				assertCounts(anaH4.path("progress"), 1, 1, 199, 100.0);
				assertCounts(brunoH4.path("progress"), 0, 1, 199, 0.0);
				StageTestSupport.stageCounts(ok(StageTestSupport.stageProgress(first, "java")).path("progress"), 0, 0, 0);
			}
			var progressRows = TestDatabase.snapshot(jdbc, PROGRESS_TABLES);

			List<JsonNode> anaSaved;
			List<JsonNode> brunoSaved;
			try (var server = start(postgres, IMPORTED)) {
				assertThat(TestDatabase.snapshot(jdbc, PROGRESS_TABLES)).isEqualTo(progressRows);
				var first = browser(server, ana);
				var second = browser(server, bruno);
				var anaJava = ok(progress(first, "java"));
				var brunoJava = ok(progress(second, "java"));
				assertKept(anaJava, anaH4, Map.of(JAVA_LESSON, "completed"), Map.of(E01, "solved", E02, "in_progress"));
				assertKept(brunoJava, brunoH4, Map.of(JAVA_LESSON, "not_completed"), Map.of(E01, "pending", E03, "solved"));
				assertCounts(anaJava.path("progress"), 1, 199, 199, 100.0 / 199);
				assertCounts(brunoJava.path("progress"), 0, 199, 199, 0.0);
				for (ApiClient browser : List.of(first, second)) {
					var stages = ok(StageTestSupport.stageProgress(browser, "java"));
					StageTestSupport.stageProgressShape(stages, "java", true, null);
					assertThat(stages.path("stageStates").size()).isZero();
					StageTestSupport.stageCounts(stages.path("progress"), 0, 14, 14);
				}

				// Newly available lessons and stages, each learner apart; the other one's progress never moves.
				var brunoBefore = personal(second);
				ok(continuation(first, "java", JAVA_PENDING));
				ok(StageTestSupport.mark(first, STAGE_1, "completed"));
				ok(StageTestSupport.open(first, "java", STAGE_2));
				assertThat(personal(second)).isEqualTo(brunoBefore);
				ok(StageTestSupport.mark(second, STAGE_3, "not_completed"));
				ok(StageTestSupport.open(second, "java", STAGE_1));
				assertContinuation(ok(progress(second, "java")).path("continuation"), JAVA_LESSON);
				anaSaved = personal(first);
				brunoSaved = personal(second);
			}

			try (var server = start(postgres, IMPORTED)) {
				var first = browser(server, ana);
				var second = browser(server, bruno);
				assertThat(personal(first)).isEqualTo(anaSaved);
				assertThat(personal(second)).isEqualTo(brunoSaved);
				assertContinuation(ok(progress(first, "java")).path("continuation"), JAVA_PENDING);
				assertStages(first, Map.of(STAGE_1, "completed"), STAGE_2, 1);
				assertStages(second, Map.of(STAGE_3, "not_completed"), STAGE_1, 0);
			}

			// A candidate in which an existing exercise lost its ID is refused at startup, before any write.
			Path conflict = conflictingCopy(temp.resolve("conflict"));
			var before = TestDatabase.snapshot(jdbc);
			Throwable failure = catchThrowable(() -> start(postgres, conflict).close());
			assertThat(NestedExceptionUtils.getMostSpecificCause(failure)).isInstanceOf(CatalogConflictException.class)
				.hasMessageContaining("nothing was changed")
				.hasMessageContaining("exercise java-02.01-e01 is no longer in the manifest");
			assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);

			try (var server = start(postgres, IMPORTED)) {
				var first = browser(server, ana);
				var second = browser(server, bruno);
				assertThat(personal(first)).isEqualTo(anaSaved);
				assertThat(personal(second)).isEqualTo(brunoSaved);
				assertCounts(ok(progress(first, "java")).path("progress"), 1, 199, 199, 100.0 / 199);
				assertCounts(ok(progress(second, "java")).path("progress"), 0, 199, 199, 0.0);
			}
		}
	}

	/** Same states, timestamps and continuation as before the update, and exactly this learner's own ones. */
	private static void assertKept(JsonNode after, JsonNode before, Map<String, String> lessons, Map<String, String> exercises) {
		assertThat(after.path("lessonStates")).isEqualTo(before.path("lessonStates"));
		assertThat(after.path("exerciseStates")).isEqualTo(before.path("exerciseStates"));
		assertThat(after.path("continuation")).isEqualTo(before.path("continuation"));
		assertThat(states(after.path("lessonStates"), "lessonId")).isEqualTo(lessons);
		assertThat(states(after.path("exerciseStates"), "exerciseId")).isEqualTo(exercises);
		assertContinuation(after.path("continuation"), JAVA_LESSON);
	}

	private static void assertStages(ApiClient browser, Map<String, String> states, String last, int completed) throws Exception {
		var stages = ok(StageTestSupport.stageProgress(browser, "java"));
		StageTestSupport.stageProgressShape(stages, "java", true, last);
		assertThat(states(stages.path("stageStates"), "stageId")).isEqualTo(states);
		StageTestSupport.stageCounts(stages.path("progress"), completed, 14, 14);
	}

	private static Map<String, String> states(JsonNode list, String idField) {
		Map<String, String> states = new TreeMap<>();
		list.forEach(item -> states.put(item.path(idField).asString(), item.path("state").asString()));
		return states;
	}

	/** Both Java progress domains and the course list, through public routes only. */
	private static List<JsonNode> personal(ApiClient browser) throws Exception {
		return List.of(ok(progress(browser, "java")), ok(StageTestSupport.stageProgress(browser, "java")),
			ok(browser.get("/api/me/courses")));
	}

	/** A new browser logged in on a new server: sessions never outlive a restart. */
	private static ApiClient browser(ConfigurableApplicationContext server, String username) throws Exception {
		var browser = new ApiClient(port(server));
		login(browser, username);
		return browser;
	}

	/** Always an explicit content directory and this test's own database; nothing from the environment. */
	private static ConfigurableApplicationContext start(PostgreSQLContainer postgres, Path content) {
		String[] args = {"--server.port=0", "--code-academy.content-dir=" + content,
			"--spring.datasource.url=" + postgres.getJdbcUrl(), "--spring.datasource.username=" + postgres.getUsername(),
			"--spring.datasource.password=" + postgres.getPassword()};
		return CodeAcademyApplication.application(args).run(args);
	}

	private static int port(ConfigurableApplicationContext server) {
		return ((WebServerApplicationContext) server).getWebServer().getPort();
	}

	/** The H4 manifest plus every file it names, each taken from content/ only after its H4 hash matches. */
	private static Path h4Copy(Path root) throws Exception {
		JsonNode manifest = ApiClient.JSON.readTree(Files.readString(H4_MANIFEST));
		List<String> named = new ArrayList<>();
		manifest.path("lessons").forEach(lesson -> {
			if (!lesson.path("bodyPath").isNull()) named.add(lesson.path("bodyPath").asString());
		});
		manifest.path("resources").forEach(resource -> named.add(resource.path("localPath").asString()));
		assertThat(named).as("files named by " + H4_MANIFEST).containsExactlyInAnyOrderElementsOf(H4_FILES.keySet());
		for (String path : named) {
			Path source = IMPORTED.resolve(path);
			if (!Files.isRegularFile(source)) fail("H4 file " + path + " named by " + H4_MANIFEST + " is missing from " + IMPORTED);
			String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)));
			if (!hash.equals(H4_FILES.get(path))) {
				fail("H4 file " + path + " in " + IMPORTED + " has SHA-256 " + hash + ", not the H4 " + H4_FILES.get(path));
			}
			Files.createDirectories(root.resolve(path).getParent());
			Files.copy(source, root.resolve(path));
		}
		Files.copy(H4_MANIFEST, root.resolve("java/manifest.json"));
		return root;
	}

	/** A temporary copy of the imported course whose manifest swaps the titles of java-02.01's first two exercises. */
	private static Path conflictingCopy(Path root) throws IOException {
		Path java = IMPORTED.resolve("java");
		try (Stream<Path> files = Files.walk(java)) {
			for (Path source : files.toList()) {
				Path target = root.resolve("java").resolve(java.relativize(source).toString());
				if (Files.isDirectory(source)) Files.createDirectories(target);
				else Files.copy(source, target);
			}
		}
		Path manifestPath = root.resolve("java/manifest.json");
		JsonNode manifest = ApiClient.JSON.readTree(Files.readString(manifestPath));
		for (JsonNode lesson : manifest.path("lessons")) {
			if (!lesson.path("id").asString().equals(JAVA_LESSON)) continue;
			// The same exercise under a new ID: its stored ID disappears, which only an equivalence could resolve.
			((ObjectNode) lesson.path("exercises").get(0)).put("id", JAVA_LESSON + "-e99");
		}
		Files.writeString(manifestPath, ApiClient.JSON.writeValueAsString(manifest));
		return root;
	}
}
