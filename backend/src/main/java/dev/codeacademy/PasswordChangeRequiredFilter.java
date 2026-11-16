package dev.codeacademy;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import dev.codeacademy.account.AccountPrincipal;
import dev.codeacademy.api.ApiErrorWriter;

/**
 * A password an administrator set is temporary (H9, D3): until the user chooses a new one, the session may only see who
 * it is, change the password or sign out. Anything else answers 403 PASSWORD_CHANGE_REQUIRED.
 */
class PasswordChangeRequiredFilter extends OncePerRequestFilter {

	private static final Set<String> ALLOWED = Set.of("GET /api/me", "PUT /api/me/password", "POST /api/auth/logout",
		"GET /api/instance", "GET /actuator/health");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal
				&& principal.mustChangePassword()) {
			String path = request.getRequestURI().substring(request.getContextPath().length());
			if (!ALLOWED.contains(request.getMethod() + " " + path)) {
				ApiErrorWriter.write(response, 403, "PASSWORD_CHANGE_REQUIRED", "Choose a new password first.");
				return;
			}
		}
		chain.doFilter(request, response);
	}
}
