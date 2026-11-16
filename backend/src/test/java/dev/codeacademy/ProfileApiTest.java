package dev.codeacademy;

import dev.codeacademy.content.ExampleContent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static dev.codeacademy.ApiClient.assertError;
import static dev.codeacademy.ApiClient.credentials;
import static dev.codeacademy.RegistrationApiTest.PASSWORD;
import static dev.codeacademy.RegistrationApiTest.unique;
import static org.assertj.core.api.Assertions.assertThat;

/** H9, T4: change your own password with the current one; this session goes on, the others close. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProfileApiTest {

	@DynamicPropertySource
	static void content(DynamicPropertyRegistry registry) {
		registry.add("code-academy.content-dir", () -> ExampleContent.root().toString());
	}

	static final String NEW_PASSWORD = "una frase nueva y bastante larga";

	@LocalServerPort int port;

	static String body(String current, String next) {
		return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
	}

	@Test
	void theCurrentPasswordChangesItThisSessionGoesOnAndTheOthersClose() throws Exception {
		String username = unique("Perfil_");
		var here = ProgressTestSupport.learner(port, username);
		var elsewhere = new ApiClient(port);
		ProgressTestSupport.login(elsewhere, username);

		assertError(here.send("PUT", "/api/me/password", body(PASSWORD + "x", NEW_PASSWORD)), 400, "WRONG_PASSWORD");
		assertError(here.send("PUT", "/api/me/password", body(PASSWORD, "corta")), 400, "INVALID_PASSWORD");
		var changed = here.send("PUT", "/api/me/password", body(PASSWORD, NEW_PASSWORD));
		assertThat(changed.statusCode()).as(changed.body()).isEqualTo(200);

		assertThat(here.get("/api/me").statusCode()).isEqualTo(200);
		assertThat(elsewhere.get("/api/me").statusCode()).isEqualTo(401);
		assertError(new ApiClient(port).send("POST", "/api/auth/login", credentials(username, PASSWORD)), 401, "AUTHENTICATION_FAILED");
		assertThat(new ApiClient(port).send("POST", "/api/auth/login", credentials(username, NEW_PASSWORD)).statusCode()).isEqualTo(200);
		assertError(new ApiClient(port).send("PUT", "/api/me/password", body(NEW_PASSWORD, PASSWORD)), 401, "AUTHENTICATION_REQUIRED");
	}
}
