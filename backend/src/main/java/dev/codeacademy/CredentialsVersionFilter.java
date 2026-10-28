package dev.codeacademy;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

import dev.codeacademy.account.AccountPrincipal;
import dev.codeacademy.account.CurrentCredentials;
import dev.codeacademy.api.ApiErrorWriter;

/**
 * Ends a session whose credentials version no longer matches the database and lets the request continue
 * as anonymous: protected routes then answer 401 through the entry point, public routes work as usual.
 * Built inside the security chain, not as a bean, so the servlet container never registers it twice.
 */
class CredentialsVersionFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(CredentialsVersionFilter.class);

	private final CurrentCredentials credentials;
	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	CredentialsVersionFilter(CurrentCredentials credentials) {
		this.credentials = credentials;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = contextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
			boolean current;
			try {
				current = credentials.isCurrent(principal);
			}
			catch (RuntimeException exception) {
				// Answered here, never through /error; the type only, and the session is left as is.
				log.error("Unexpected {}", exception.getClass().getName());
				ApiErrorWriter.internalError(response);
				return;
			}
			if (!current) {
				HttpSession session = request.getSession(false);
				if (session != null) session.invalidate();
				contextHolder.clearContext();
			}
		}
		chain.doFilter(request, response);
	}
}
