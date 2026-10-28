package dev.codeacademy;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import dev.codeacademy.account.AccountPrincipal;
import dev.codeacademy.account.CurrentCredentials;

/**
 * Ends a session whose credentials version no longer matches the database.
 * Built inside the security chain, not as a bean, so the servlet container never registers it twice.
 */
class CredentialsVersionFilter extends OncePerRequestFilter {

	private final CurrentCredentials credentials;
	private final AuthenticationEntryPoint entryPoint;
	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	CredentialsVersionFilter(CurrentCredentials credentials, AuthenticationEntryPoint entryPoint) {
		this.credentials = credentials;
		this.entryPoint = entryPoint;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = contextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal
				&& !credentials.isCurrent(principal)) {
			HttpSession session = request.getSession(false);
			if (session != null) session.invalidate();
			contextHolder.clearContext();
			entryPoint.commence(request, response,
					new InsufficientAuthenticationException("Credentials version changed"));
			return;
		}
		chain.doFilter(request, response);
	}
}
