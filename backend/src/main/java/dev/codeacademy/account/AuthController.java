package dev.codeacademy.account;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import dev.codeacademy.api.StrictJson;

/** Logout is not here: the security chain's logout filter handles it. */
@RestController
@RequestMapping(path = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
class AuthController {

	private final RegistrationService registration;
	private final LoginService login;
	private final SessionAuthenticationStrategy sessionStrategy;
	private final SecurityContextRepository contextRepository;
	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	AuthController(RegistrationService registration, LoginService login,
			SessionAuthenticationStrategy sessionStrategy, SecurityContextRepository contextRepository) {
		this.registration = registration;
		this.login = login;
		this.sessionStrategy = sessionStrategy;
		this.contextRepository = contextRepository;
	}

	/** Creates the account only; the client logs in separately. */
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	UserView register(@RequestBody(required = false) JsonNode body) {
		List<String> fields = StrictJson.strings(body, "username", "password");
		return UserView.of(registration.register(fields.get(0), fields.get(1)));
	}

	/** Rotates the session ID and renews the CSRF token before storing the new security context. */
	@PostMapping("/login")
	UserView login(@RequestBody(required = false) JsonNode body, HttpServletRequest request,
			HttpServletResponse response) {
		List<String> fields = StrictJson.strings(body, "username", "password");
		Account account = login.authenticate(fields.get(0), fields.get(1));
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
				AccountPrincipal.of(account), null, AuthorityUtils.createAuthorityList(
					account.admin() ? new String[] { "ROLE_USER", "ROLE_ADMIN" } : new String[] { "ROLE_USER" }));
		sessionStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = contextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		contextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
		return UserView.of(account);
	}
}
