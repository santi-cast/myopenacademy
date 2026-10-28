package dev.codeacademy.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiErrorHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> contract(ApiException exception) {
		return ResponseEntity.status(exception.status()).body(new ApiError(exception.code(), exception.getMessage()));
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class })
	ResponseEntity<ApiError> unreadable(Exception exception) {
		return contract(ApiException.invalidRequest());
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> unexpected(Exception exception) {
		// Log the type only: messages may echo request content or credentials.
		log.error("Unexpected {}", exception.getClass().getName());
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ApiError("INTERNAL_ERROR", "Unexpected error."));
	}
}
