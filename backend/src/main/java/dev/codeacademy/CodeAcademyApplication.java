package dev.codeacademy;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

import dev.codeacademy.account.CreateAdminCommand;
import dev.codeacademy.account.PasswordResetCommand;
import dev.codeacademy.account.PasswordTerminal;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CodeAcademyApplication {

	private static final String RESET_ARGUMENT = "--reset-password";
	private static final String ADMIN_ARGUMENT = "--create-admin";

	public static void main(String[] args) {
		Optional<String> resetUsername = passwordResetUsername(args);
		if (resetUsername.isPresent()) {
			System.exit(resetPassword(resetUsername.get(), args));
		}
		Optional<String> adminUsername = argument(args, ADMIN_ARGUMENT);
		if (adminUsername.isPresent()) {
			System.exit(createAdmin(adminUsername.get(), args));
		}
		application(args).run(args);
	}

	/** The value of {@code <flag>=<value>}; the bare flag selects the mode with an empty value. */
	static Optional<String> argument(String[] args, String flag) {
		return Arrays.stream(args)
				.filter(arg -> arg.equals(flag) || arg.startsWith(flag + "="))
				.map(arg -> arg.substring(Math.min(arg.length(), flag.length() + 1)))
				.findFirst();
	}

	/** Console mode (a password reset or creating the administrator): never a web server. */
	static boolean consoleMode(String[] args) {
		return passwordResetUsername(args).isPresent() || argument(args, ADMIN_ARGUMENT).isPresent();
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
		if (consoleMode(args)) {
			application.setWebApplicationType(WebApplicationType.NONE);
			application.setBannerMode(Banner.Mode.OFF);
		}
		return application;
	}

	private static int createAdmin(String username, String[] args) {
		var out = new PrintWriter(System.out, true);
		var err = new PrintWriter(System.err, true);
		Optional<PasswordTerminal> terminal = PasswordTerminal.system();
		if (terminal.isEmpty()) return PasswordResetCommand.refuseWithoutTerminal(err);
		try (var context = application(args).run(args)) {
			int exitCode = context.getBean(CreateAdminCommand.class).run(username, terminal, out, err);
			return SpringApplication.exit(context, () -> exitCode);
		}
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
