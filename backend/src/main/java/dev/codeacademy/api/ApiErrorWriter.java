package dev.codeacademy.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes the contract error shape straight to the response, bypassing message converters,
 * so the client's Accept header can never turn an error into a different status.
 */
public final class ApiErrorWriter {

	private ApiErrorWriter() {
	}

	/** Fixed strings only: no exception messages, credentials or request data reach the response. */
	public static void write(HttpServletResponse response, int status, String code, String message)
			throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType("application/json");
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
	}

	public static void internalError(HttpServletResponse response) throws IOException {
		write(response, 500, "INTERNAL_ERROR", "Unexpected error.");
	}
}
