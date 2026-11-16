package dev.codeacademy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.codeacademy.content.ContentFixture;
import dev.codeacademy.content.ImageFixture;
import dev.codeacademy.content.ZipFixture;

import static dev.codeacademy.ApiClient.assertError;
import static dev.codeacademy.ApiClient.credentials;
import static dev.codeacademy.RegistrationApiTest.PASSWORD;
import static dev.codeacademy.RegistrationApiTest.unique;
import static org.assertj.core.api.Assertions.assertThat;

/** Real authenticated delivery against a temporary v2 copy and a Testcontainers database, never the owner database. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogContentApiTest {

	@TempDir static Path tempDir;
	ContentFixture fixture;
	ConfigurableApplicationContext context;
	JdbcTemplate jdbc;
	ApiClient first;
	ApiClient second;
	int port;

	@BeforeAll
	void start() throws Exception {
		fixture = ContentFixture.copyInto(tempDir);
		for (String type : List.of("png", "jpeg", "svg")) ImageFixture.register(fixture, type);
		fixture.addResource("demo.image-unavailable", "pending/image.png", "image/png", ImageFixture.bytes("png"));
		fixture.addResource("demo.stage-image-unavailable", "pending/stage.png", "image/png", ImageFixture.bytes("png"));
		fixture.addResource("demo.image-mutable", "mutable/image.png", "image/png", ImageFixture.bytes("png"));
		fixture.addResource("demo.image-unicode", "assets/diagrama útil.png", "image/png", ImageFixture.bytes("png"));
		fixture.editManifest(m -> {
			for (var resource : m.get("resources")) {
				if (resource.get("resourceId").asString().equals("demo.image-unavailable")) {
					((tools.jackson.databind.node.ObjectNode) resource).put("lessonId", "demo.02.01");
				}
			}
		});
		ZipFixture.install(fixture);
		fixture.editManifest(m -> {
			var stages = (tools.jackson.databind.node.ArrayNode) m.get("projectStages");
			stages.addObject().put("id", "demo.stage-ready").put("title", "Ready").put("position", 1)
				.put("sourcePath", "project/ready.md").put("sourceAvailable", true).put("available", true)
				.put("bodyPath", "demo/lessons/stage.md");
			stages.addObject().put("id", "demo.stage-pending").put("title", "Pending").put("position", 2)
				.put("sourcePath", "project/pending.md").put("sourceAvailable", false).put("available", false).putNull("bodyPath");
			var links = (tools.jackson.databind.node.ArrayNode) m.get("sourceToId");
			links.addObject().put("sourcePath", "project/ready.md").put("stageId", "demo.stage-ready");
			links.addObject().put("sourcePath", "project/pending.md").put("stageId", "demo.stage-pending");
			for (var resource : m.get("resources")) {
				String id = resource.get("resourceId").asString();
				if (id.equals(ZipFixture.ZIP) || id.equals("demo.nested") || id.equals("demo.01.01-r-hello")
						|| id.equals("demo.01.01-r-other") || id.equals("demo.stage-image-unavailable")
						|| id.equals("demo.image-png") || id.equals("demo.image-svg")) {
					var node = (tools.jackson.databind.node.ObjectNode) resource;
					node.remove("lessonId");
					node.put("stageId", id.equals("demo.stage-image-unavailable") ? "demo.stage-pending" : "demo.stage-ready");
				}
			}
		});
		Files.writeString(fixture.root.resolve("demo/lessons/stage.md"), "# Stage\n\n[folder](../fase-01/codigo/hola/)\n");
		ZipFixture.refresh(fixture);
		String[] args = {"--server.port=0", "--code-academy.content-dir=" + fixture.root};
		var application = CodeAcademyApplication.application(args);
		application.addPrimarySources(List.of(TestcontainersConfiguration.class));
		context = application.run(args);
		port = ((WebServerApplicationContext) context).getWebServer().getPort();
		jdbc = context.getBean(JdbcTemplate.class);
		first = login();
		second = login();
	}

	@AfterAll
	void stop() {
		if (context != null) context.close();
		// JUnit removes the temporary controlled copy, including any symlink, after the server closes.
	}

	@Test
	void authenticatedRasterImagesAreInlineWithPrivateSandboxedDelivery() throws Exception {
		var before = TestDatabase.snapshot(jdbc);
		for (ApiClient browser : List.of(first, second)) {
			for (String type : List.of("png", "jpeg")) {
				var response = browser.download("/api/resources/demo.image-" + type, "image/avif,image/webp,image/*,*/*;q=0.8");
				assertThat(response.statusCode()).isEqualTo(200);
				assertThat(response.body()).isEqualTo(ImageFixture.bytes(type));
				assertThat(response.headers().allValues("Content-Type")).containsExactly("image/" + type);
				assertThat(response.headers().allValues("Content-Disposition")).containsExactly(
					"inline; filename=\"diagram." + type + "\"; filename*=UTF-8''diagram." + type);
				assertSafeHeaders(response);
			}
		}
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void svgAndExistingCodeResourcesRemainAttachmentsWithOriginalNames() throws Exception {
		var before = TestDatabase.snapshot(jdbc);
		for (String id : List.of("demo.image-svg", "demo.01.01-r-hello")) {
			var response = first.download("/api/resources/" + id, "text/html,*/*;q=0.8");
			assertThat(response.statusCode()).isEqualTo(200);
			String name = id.equals("demo.image-svg") ? "diagram.svg" : "Hello.java";
			String type = id.equals("demo.image-svg") ? "image/svg+xml" : "text/plain;charset=utf-8";
			assertThat(response.headers().allValues("Content-Disposition")).containsExactly(
				"attachment; filename=\"" + name + "\"; filename*=UTF-8''" + name);
			assertThat(response.headers().allValues("Content-Type")).containsExactly(type);
			byte[] expected = id.equals("demo.image-svg") ? ImageFixture.bytes("svg")
				: Files.readAllBytes(fixture.root.resolve("demo/resources/demo.01.01-r-hello.java"));
			assertThat(response.body()).isEqualTo(expected);
			assertSafeHeaders(response);
		}
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void inlineRasterNamesKeepSafeAsciiFallbackAndExactUtf8Filename() throws Exception {
		var response = first.download("/api/resources/demo.image-unicode", "image/*");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo(ImageFixture.bytes("png"));
		assertThat(response.headers().allValues("Content-Disposition")).containsExactly(
			"inline; filename=\"diagrama__til.png\"; filename*=UTF-8''diagrama%20%C3%BAtil.png");
		assertSafeHeaders(response);
	}

	@Test
	void imagesRequireASessionAndRegisteredAvailableOwnerWithoutChangingEitherUser() throws Exception {
		var before = TestDatabase.snapshot(jdbc);
		var anonymous = new ApiClient(port);
		for (String type : List.of("png", "jpeg", "svg")) {
			assertError(anonymous.get("/api/resources/demo.image-" + type), 401, "AUTHENTICATION_REQUIRED");
		}
		for (ApiClient browser : List.of(first, second)) {
			assertError(browser.get("/api/resources/demo.image-unknown"), 404, "NOT_FOUND");
			assertError(browser.get("/api/resources/demo.image-unavailable"), 404, "NOT_FOUND");
		}
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void pathLikeImageIdsCannotReadPhysicalFiles() throws Exception {
		var before = TestDatabase.snapshot(jdbc);
		for (String id : List.of("..%2Fdemo%2Fmanifest.json", "demo%2Fresources%2Fdemo-image-png",
			"demo.image-png.png", "demo.image-png%00", "%2e%2e")) {
			var response = first.download("/api/resources/" + id, "image/*");
			assertThat(response.statusCode()).as(id).isIn(400, 404);
			assertThat(response.headers().firstValue("Content-Disposition")).isEmpty();
			assertThat(new String(response.body(), StandardCharsets.UTF_8))
				.doesNotContain("schemaVersion", "versionRef", fixture.root.toString());
			assertThat(response.body()).isNotEqualTo(ImageFixture.bytes("png"));
		}
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void serveTimeSymlinkReplacementIsRejectedWithoutChangingEitherUser() throws Exception {
		var before = TestDatabase.snapshot(jdbc);
		Path loaded = fixture.root.resolve("demo/resources/demo.image-mutable");
		Path outside = tempDir.resolve("outside.png");
		Files.write(outside, ImageFixture.bytes("png"));
		Files.move(loaded, tempDir.resolve("saved.png"));
		Files.createSymbolicLink(loaded, outside);

		assertError(first.get("/api/resources/demo.image-mutable"), 404, "NOT_FOUND");
		assertError(second.get("/api/resources/demo.image-mutable"), 404, "NOT_FOUND");
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	/** Moved from CatalogApiTest: since H5 the real course has no pending lesson, the fixture's demo.02.01 still does. */
	@Test
	void pendingLessonIs409WithoutChangingAnything() throws Exception {
		var before = TestDatabase.snapshot(jdbc);

		for (ApiClient browser : List.of(first, second)) {
			assertError(browser.get("/api/lessons/demo.02.01"), 409, "LESSON_UNAVAILABLE");
		}

		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void stageOwnedZipAndMembersRequireSessionButNotEnrollment() throws Exception {
		var before = TestDatabase.snapshot(jdbc);
		assertError(new ApiClient(port).get("/api/resources/" + ZipFixture.ZIP), 401, "AUTHENTICATION_REQUIRED");
		for (ApiClient browser : List.of(first, second)) {
			var response = browser.download("/api/resources/" + ZipFixture.ZIP, "text/html");
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.body()).isEqualTo(Files.readAllBytes(fixture.root.resolve(ZipFixture.LOCAL)));
			assertThat(response.headers().allValues("Content-Type")).containsExactly("application/zip");
			assertThat(response.headers().allValues("Content-Disposition")).containsExactly(
				"attachment; filename=\"hola.zip\"; filename*=UTF-8''hola.zip");
			assertSafeHeaders(response);
			var member = browser.download("/api/resources/demo.nested", "*/*");
			assertThat(member.statusCode()).isEqualTo(200);
			assertThat(member.body()).containsExactly(0, 1, 2, 3);
			assertThat(member.headers().allValues("Content-Disposition")).containsExactly(
				"attachment; filename=\"_til.java\"; filename*=UTF-8''%C3%BAtil.java");
			assertSafeHeaders(member);
			// Lesson JSON must remain usable even when other resources belong to stages.
			assertThat(browser.get("/api/lessons/demo.01.01").statusCode()).isEqualTo(200);
			assertError(browser.get("/api/resources/demo.stage-image-unavailable"), 404, "NOT_FOUND");
		}
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	private ApiClient login() throws Exception {
		var browser = new ApiClient(port);
		String username = unique("Image_");
		assertThat(browser.send("POST", "/api/auth/register", credentials(username, PASSWORD)).statusCode()).isEqualTo(201);
		assertThat(browser.send("POST", "/api/auth/login", credentials(username, PASSWORD)).statusCode()).isEqualTo(200);
		return browser;
	}

	private static void assertSafeHeaders(java.net.http.HttpResponse<byte[]> response) {
		assertThat(response.headers().allValues("X-Content-Type-Options")).containsExactly("nosniff");
		assertThat(response.headers().allValues("Content-Security-Policy")).containsExactly("sandbox");
		assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("private", "no-store")
			.doesNotContain("public");
	}
}
