package dev.codeacademy.api;

import org.springframework.http.HttpStatus;

/** A contract error from docs/api.md: the status and stable code the client decides on. */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;

	public ApiException(HttpStatus status, String code, String message) {
		super(message, null, false, false);
		this.status = status;
		this.code = code;
	}

	public static ApiException invalidRequest() {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is invalid.");
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}
}
