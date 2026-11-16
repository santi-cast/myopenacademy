package dev.codeacademy;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import dev.codeacademy.account.CurrentCredentials;
import dev.codeacademy.api.ApiErrorWriter;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

	/** Not built in console reset mode, which has no web server; the other beans stay shared. */
	@Bean
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokens,
			SecurityContextRepository contexts, CurrentCredentials credentials) throws Exception {
		AuthenticationEntryPoint authenticationRequired = (request, response, exception) ->
				error(response, 401, "AUTHENTICATION_REQUIRED", "Authentication is required.");
		return http
				// The same repository renews the token on login (loginSessionStrategy) and logout.
				.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokens))
				.securityContext(context -> context.securityContextRepository(contexts))
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(logout -> logout
						.logoutRequestMatcher(PathPatternRequestMatcher.withDefaults()
								.matcher(HttpMethod.POST, "/api/auth/logout"))
						.addLogoutHandler((request, response, authentication) ->
								response.addHeader(HttpHeaders.SET_COOKIE, expiredSessionCookie(request)))
						.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
				.requestCache(AbstractHttpConfigurer::disable)
				.addFilterAfter(new CredentialsVersionFilter(credentials), LogoutFilter.class)
				// After the version check: a stale session is already gone; a temporary password limits a current one.
				.addFilterAfter(new PasswordChangeRequiredFilter(), CredentialsVersionFilter.class)
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(authenticationRequired)
						.accessDeniedHandler((request, response, exception) -> {
							if (exception instanceof CsrfException) {
								error(response, 403, "CSRF_REJECTED", "CSRF token is missing or invalid.");
							}
							else {
								error(response, 403, "ACCESS_DENIED", "Access is denied.");
							}
						}))
				.authorizeHttpRequests(requests -> requests
						// Only the container's error forward; a direct request to /error is still denied.
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(HttpMethod.GET, "/actuator/health", "/api/instance").permitAll()
						// The administration (H9): only an administrator's session.
						.requestMatchers(HttpMethod.GET, "/api/admin/settings", "/api/admin/users", "/api/admin/courses")
								.hasRole("ADMIN")
						.requestMatchers(HttpMethod.PUT, "/api/admin/settings", "/api/admin/users/*/active",
								"/api/admin/users/*/password").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/logout")
								.permitAll()
						.requestMatchers(HttpMethod.GET, "/api/me").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/courses", "/api/courses/*", "/api/lessons/*",
								"/api/resources/*", "/api/courses/*/stages", "/api/stages/*", "/api/documents/*").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/me/courses", "/api/me/courses/*/progress",
								"/api/me/courses/*/stage-progress").authenticated()
						.requestMatchers(HttpMethod.PUT, "/api/me/courses/*", "/api/me/courses/*/continuation",
								"/api/me/lessons/*/state", "/api/me/exercises/*/state", "/api/me/stages/*/state",
								"/api/me/courses/*/stage-continuation", "/api/me/password").authenticated()
						.anyRequest().denyAll())
				.build();
	}

	/** The repository csrf.spa() would create, shared so login can renew the token. */
	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		return CookieCsrfTokenRepository.withHttpOnlyFalse();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/** Applied by the login endpoint: rotate an existing session ID, then renew the CSRF token. */
	@Bean
	SessionAuthenticationStrategy loginSessionStrategy(CsrfTokenRepository csrfTokens) {
		return new CompositeSessionAuthenticationStrategy(List.of(
				new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokens)));
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
	}

	/** Tomcat expires a zero-age servlet Cookie with Expires only; ResponseCookie also writes Max-Age=0. */
	private static String expiredSessionCookie(HttpServletRequest request) {
		String path = request.getContextPath().isEmpty() ? "/" : request.getContextPath();
		return ResponseCookie.from("JSESSIONID", "").path(path).maxAge(0).httpOnly(true).sameSite("Lax")
				.build().toString();
	}

	private static void error(HttpServletResponse response, int status, String code, String message)
			throws IOException {
		ApiErrorWriter.write(response, status, code, message);
	}
}
