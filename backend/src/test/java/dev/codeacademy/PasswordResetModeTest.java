package dev.codeacademy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

import dev.codeacademy.account.PasswordResetCommand;

import static org.assertj.core.api.Assertions.assertThat;

/** The reset mode is chosen from the arguments and starts the application without a web server. */
class PasswordResetModeTest {

	@Test
	void onlyTheResetArgumentSelectsResetMode() {
		assertThat(CodeAcademyApplication.passwordResetUsername(
				new String[] { "--spring.profiles.active=local", "--reset-password=Ana_1" })).contains("Ana_1");
		assertThat(CodeAcademyApplication.passwordResetUsername(new String[] { "--spring.profiles.active=local" }))
				.isEqualTo(Optional.empty());
		assertThat(CodeAcademyApplication.passwordResetUsername(new String[0])).isEmpty();
		// A bare flag must still mean reset mode, never a normal start with a web server.
		assertThat(CodeAcademyApplication.passwordResetUsername(new String[] { "--reset-password" })).contains("");

		assertThat(CodeAcademyApplication.application(new String[] { "--reset-password=Ana_1" })
				.getWebApplicationType()).isEqualTo(WebApplicationType.NONE);
		assertThat(CodeAcademyApplication.application(new String[0]).getWebApplicationType())
				.isEqualTo(WebApplicationType.SERVLET);
	}

	@Test
	void resetModeContextStartsWithoutWebServerAndProvidesTheCommand() {
		String[] args = { "--reset-password=Ana_1" };
		var application = CodeAcademyApplication.application(args);
		application.addPrimarySources(List.of(TestcontainersConfiguration.class));

		try (var context = application.run(args)) {
			assertThat(context).isNotInstanceOf(WebServerApplicationContext.class);
			assertThat(context.getBeansOfType(PasswordResetCommand.class)).hasSize(1);
		}
	}
}
