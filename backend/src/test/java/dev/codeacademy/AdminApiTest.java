package dev.codeacademy;

import dev.codeacademy.content.ExampleContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;

import static dev.codeacademy.ApiClient.assertError;
import static dev.codeacademy.ApiClient.credentials;
import static dev.codeacademy.RegistrationApiTest.PASSWORD;
import static dev.codeacademy.RegistrationApiTest.unique;
import static org.assertj.core.api.Assertions.assertThat;

/** H9, T2: the administration API, only for an administrator's session; the public instance view. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminApiTest {

	@DynamicPropertySource
	static void content(DynamicPropertyRegistry registry) {
		registry.add("code-academy.content-dir", () -> ExampleContent.root().toString());
	}

	static final String OTHER_PASSWORD = "otra frase bastante larga";

	@Autowired JdbcTemplate jdbc;
	@LocalServerPort int port;

	@AfterEach
	void openAgain() {
		jdbc.update("update code_academy.instance_settings set registration_open = true, instance_name = null where id = 1");
	}

	ApiClient admin() throws Exception {
		String username = unique("Admin_");
		var browser = ProgressTestSupport.learner(port, username);
		jdbc.update("update code_academy.users set role = 'admin', credentials_version = credentials_version + 1 where username = ?",
			username);
		ProgressTestSupport.login(browser, username);
		return browser;
	}

	JsonNode ok(java.net.http.HttpResponse<String> response) throws Exception {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		return ApiClient.JSON.readTree(response.body());
	}

	@Test
	void onlyAnAdministratorReachesTheAdministration() throws Exception {
		var learner = ProgressTestSupport.learner(port, unique("Alumno_"));
		for (String path : new String[] { "/api/admin/settings", "/api/admin/users", "/api/admin/courses" }) {
			assertError(learner.get(path), 403, "ACCESS_DENIED");
			assertError(new ApiClient(port).get(path), 401, "AUTHENTICATION_REQUIRED");
		}
		assertError(learner.send("PUT", "/api/admin/settings", "{\"registrationOpen\":false}"), 403, "ACCESS_DENIED");
		var admin = admin();
		assertThat(ok(admin.get("/api/admin/users")).path("users").size()).isGreaterThan(1);
	}

	@Test
	void closingRegistrationRefusesNewAccountsAndTheNameIsPublic() throws Exception {
		var admin = admin();
		JsonNode settings = ok(admin.send("PUT", "/api/admin/settings",
			"{\"registrationOpen\":false,\"instanceName\":\"  Academia de casa  \"}"));
		assertThat(settings.path("registrationOpen").asBoolean()).isFalse();
		assertThat(settings.path("instanceName").asString()).isEqualTo("Academia de casa");
		JsonNode instance = ok(new ApiClient(port).get("/api/instance"));
		assertThat(instance.path("name").asString()).isEqualTo("Academia de casa");
		assertThat(instance.path("registrationOpen").asBoolean()).isFalse();
		long users = jdbc.queryForObject("select count(*) from code_academy.users", Long.class);
		assertError(new ApiClient(port).send("POST", "/api/auth/register", credentials(unique("Nuevo_"), PASSWORD)), 403,
			"REGISTRATION_CLOSED");
		assertThat(jdbc.queryForObject("select count(*) from code_academy.users", Long.class)).isEqualTo(users);
		assertError(admin.send("PUT", "/api/admin/settings", "{\"registrationOpen\":true,\"instanceName\":\"" + "x".repeat(81) + "\"}"),
			400, "INVALID_INSTANCE_NAME");
		JsonNode cleared = ok(admin.send("PUT", "/api/admin/settings", "{\"registrationOpen\":true,\"instanceName\":\" \"}"));
		assertThat(cleared.path("instanceName").isNull()).isTrue();
	}

	@Test
	void deactivatingClosesTheSessionsKeepsTheAccountAndCanBeUndone() throws Exception {
		var admin = admin();
		String username = unique("Alumno_");
		var learner = ProgressTestSupport.learner(port, username);
		String id = ApiClient.JSON.readTree(learner.get("/api/me").body()).path("id").asString();

		JsonNode off = ok(admin.send("PUT", "/api/admin/users/" + id + "/active", "{\"active\":false}"));
		assertThat(off.path("active").asBoolean()).isFalse();
		assertThat(learner.get("/api/me").statusCode()).isEqualTo(401);
		assertError(new ApiClient(port).send("POST", "/api/auth/login", credentials(username, PASSWORD)), 403, "ACCOUNT_DISABLED");

		assertThat(ok(admin.send("PUT", "/api/admin/users/" + id + "/active", "{\"active\":true}")).path("active").asBoolean()).isTrue();
		ProgressTestSupport.login(new ApiClient(port), username);
	}

	@Test
	void anAdministratorNeverDeactivatesThemselves() throws Exception {
		var admin = admin();
		String id = ApiClient.JSON.readTree(admin.get("/api/me").body()).path("id").asString();
		assertError(admin.send("PUT", "/api/admin/users/" + id + "/active", "{\"active\":false}"), 409, "CANNOT_DEACTIVATE_SELF");
		assertError(admin.send("PUT", "/api/admin/users/no-es-un-id/active", "{\"active\":false}"), 404, "USER_NOT_FOUND");
	}

	@Test
	void aNewPasswordClosesTheSessionsAndTheOldOneNoLongerWorks() throws Exception {
		var admin = admin();
		String username = unique("Alumno_");
		var learner = ProgressTestSupport.learner(port, username);
		String id = ApiClient.JSON.readTree(learner.get("/api/me").body()).path("id").asString();
		assertError(admin.send("PUT", "/api/admin/users/" + id + "/password", "{\"password\":\"corta\"}"), 400, "INVALID_PASSWORD");
		ok(admin.send("PUT", "/api/admin/users/" + id + "/password", "{\"password\":\"" + OTHER_PASSWORD + "\"}"));
		assertThat(learner.get("/api/me").statusCode()).isEqualTo(401);
		assertError(new ApiClient(port).send("POST", "/api/auth/login", credentials(username, PASSWORD)), 401, "AUTHENTICATION_FAILED");
		// Temporary (D3): with it, the session may only see who it is, change the password or sign out.
		var temporary = new ApiClient(port);
		var login = temporary.send("POST", "/api/auth/login", credentials(username, OTHER_PASSWORD));
		assertThat(login.statusCode()).isEqualTo(200);
		assertThat(ApiClient.JSON.readTree(login.body()).path("mustChangePassword").asBoolean()).isTrue();
		assertThat(ok(temporary.get("/api/me")).path("mustChangePassword").asBoolean()).isTrue();
		assertError(temporary.get("/api/courses"), 403, "PASSWORD_CHANGE_REQUIRED");
		assertError(temporary.send("PUT", "/api/me/courses/ejemplo", null), 403, "PASSWORD_CHANGE_REQUIRED");
		String change = "{\"currentPassword\":\"" + OTHER_PASSWORD + "\",\"newPassword\":\"";
		assertError(temporary.send("PUT", "/api/me/password", change + OTHER_PASSWORD + "\"}"), 400, "SAME_PASSWORD");
		JsonNode changed = ok(temporary.send("PUT", "/api/me/password", change + "la definitiva que solo sé yo\"}"));
		assertThat(changed.path("mustChangePassword").asBoolean()).isFalse();
		assertThat(temporary.get("/api/courses").statusCode()).isEqualTo(200);
		assertThat(ok(admin.get("/api/admin/users")).path("users").findValues("mustChangePassword").stream()
			.allMatch(value -> !value.asBoolean())).isTrue();
	}

	@Test
	void theInstalledCoursesWithTheirVersion() throws Exception {
		JsonNode courses = ok(admin().get("/api/admin/courses")).path("courses");
		assertThat(courses.size()).isEqualTo(2);
		JsonNode ejemplo = courses.get(0).path("id").asString().equals("ejemplo") ? courses.get(0) : courses.get(1);
		assertThat(ejemplo.path("version").asString()).isEqualTo("1.0.0");
		assertThat(ejemplo.path("commit").asString()).matches("[0-9a-f]{40}");
		assertThat(ejemplo.path("repository").asString()).isEqualTo("https://github.com/myopenacademy/curso-ejemplo");
	}
}
