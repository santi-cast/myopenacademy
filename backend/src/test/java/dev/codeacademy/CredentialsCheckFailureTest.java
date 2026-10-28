package dev.codeacademy;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import dev.codeacademy.account.AccountPrincipal;
import dev.codeacademy.account.CurrentCredentials;
import static dev.codeacademy.ApiClient.*;
import static dev.codeacademy.RegistrationApiTest.*;
import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CredentialsCheckFailureTest {
	@MockitoSpyBean CurrentCredentials credentials;
	@LocalServerPort int port;

	@Test
	void databaseFailureDuringCredentialsCheckIsInternalError() throws Exception {
		String user = unique("ff_");
		assertThat(new ApiClient(port).send("POST", "/api/auth/register", credentials(user, PASSWORD)).statusCode()).isEqualTo(201);
		var browser = new ApiClient(port);
		assertThat(browser.send("POST", "/api/auth/login", credentials(user, PASSWORD)).statusCode()).isEqualTo(200);
		Mockito.doThrow(new IllegalStateException("db down")).when(credentials).isCurrent(Mockito.any(AccountPrincipal.class));
		assertError(browser.get("/api/me"), 500, "INTERNAL_ERROR");
	}
}
