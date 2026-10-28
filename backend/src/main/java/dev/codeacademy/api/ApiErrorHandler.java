package dev.codeacademy.api;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Every handler writes raw JSON, so no error falls through to a second, converter-chosen status. */
@RestControllerAdvice
class ApiErrorHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

	@ExceptionHandler(ApiException.class)
	void contract(ApiException exception, HttpServletResponse response) throws IOException {
		ApiErrorWriter.write(response, exception.status().value(), exception.code(), exception.getMessage());
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class })
	void unreadable(Exception exception, HttpServletResponse response) throws IOException {
		contract(ApiException.invalidRequest(), response);
	}

	/** Raised at handler lookup, before the handler runs: nothing is written and no session is created. */
	@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
	void notAcceptable(Exception exception, HttpServletResponse response) throws IOException {
		ApiErrorWriter.write(response, 406, "NOT_ACCEPTABLE", "Response must be acceptable as JSON.");
	}

	@ExceptionHandler(Exception.class)
	void unexpected(Exception exception, HttpServletResponse response) throws IOException {
		// Log the type only: messages may echo request content or credentials.
		log.error("Unexpected {}", exception.getClass().getName());
		ApiErrorWriter.internalError(response);
	}
}
