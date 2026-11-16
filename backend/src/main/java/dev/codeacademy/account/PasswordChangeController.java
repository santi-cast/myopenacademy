package dev.codeacademy.account;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

import dev.codeacademy.api.ApiException;
import dev.codeacademy.api.StrictJson;

/**
 * The profile (H9, T4): change your own password with the current one. Your other sessions close (the credentials
 * version goes up); this one goes on, renewed like a login.
 */
@RestController
class PasswordChangeController {

	private final AccountRepository accounts;
	private final PasswordEncoder passwordEncoder;
	private final SessionAuthenticationStrategy sessionStrategy;
	private final SecurityContextRepository contextRepository;
	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	PasswordChangeController(AccountRepository accounts, PasswordEncoder passwordEncoder,
			SessionAuthenticationStrategy sessionStrategy, SecurityContextRepository contextRepository) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.sessionStrategy = sessionStrategy;
		this.contextRepository = contextRepository;
	}

	@PutMapping(path = "/api/me/password", produces = MediaType.APPLICATION_JSON_VALUE)
	UserView change(@RequestBody(required = false) JsonNode body, @AuthenticationPrincipal AccountPrincipal principal,
			HttpServletRequest request, HttpServletResponse response) {
		List<String> fields = StrictJson.strings(body, "currentPassword", "newPassword");
		Account account = accounts.findById(principal.id())
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication is required."));
		if (!CredentialsPolicy.withinLoginLimit(fields.get(0)) || !passwordEncoder.matches(fields.get(0), account.passwordHash())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "The current password is incorrect.");
		}
		if (!CredentialsPolicy.validNewPassword(fields.get(1))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "Password is invalid.");
		}
		if (fields.get(1).equals(fields.get(0))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_PASSWORD", "The new password must be different.");
		}
		accounts.replacePasswordById(account.id(), passwordEncoder.encode(fields.get(1)), false);
		Account changed = accounts.findById(account.id()).orElseThrow();
		Authentication authentication = AuthController.authentication(changed);
		sessionStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = contextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		contextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
		return UserView.of(changed);
	}
}
