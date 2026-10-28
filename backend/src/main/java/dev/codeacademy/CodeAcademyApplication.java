package dev.codeacademy;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

import dev.codeacademy.account.PasswordResetCommand;
import dev.codeacademy.account.PasswordTerminal;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CodeAcademyApplication {

	private static final String RESET_ARGUMENT = "--reset-password";

	public static void main(String[] args) {
		Optional<String> resetUsername = passwordResetUsername(args);
		if (resetUsername.isPresent()) {
			System.exit(resetPassword(resetUsername.get(), args));
		}
		application(args).run(args);
	}

	/** The username of {@code --reset-password=<username>}; the bare flag selects reset mode with no user. */
	static Optional<String> passwordResetUsername(String[] args) {
		return Arrays.stream(args)
				.filter(arg -> arg.equals(RESET_ARGUMENT) || arg.startsWith(RESET_ARGUMENT + "="))
				.map(arg -> arg.substring(Math.min(arg.length(), RESET_ARGUMENT.length() + 1)))
				.findFirst();
	}

	/** Reset mode runs the console command only, so it never starts a web server. */
	static SpringApplication application(String[] args) {
		var application = new SpringApplication(CodeAcademyApplication.class);
		if (passwordResetUsername(args).isPresent()) {
			application.setWebApplicationType(WebApplicationType.NONE);
			application.setBannerMode(Banner.Mode.OFF);
		}
		return application;
	}

	private static int resetPassword(String username, String[] args) {
		var out = new PrintWriter(System.out, true);
		var err = new PrintWriter(System.err, true);
		Optional<PasswordTerminal> terminal = PasswordTerminal.system();
		// Refuse before startup: without a terminal nothing is asked and no database is needed.
		if (terminal.isEmpty()) return PasswordResetCommand.refuseWithoutTerminal(err);
		try (var context = application(args).run(args)) {
			int exitCode = context.getBean(PasswordResetCommand.class).run(username, terminal, out, err);
			return SpringApplication.exit(context, () -> exitCode);
		}
	}

}
