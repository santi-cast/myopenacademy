package dev.codeacademy.api;

import java.io.IOException;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Last resort for errors the container forwards to /error: the original status keeps its contract code,
 * any other client error becomes INVALID_REQUEST and anything else INTERNAL_ERROR. Replaces Boot's
 * HTML/JSON error page so no request data or exception message is ever echoed.
 */
@RestController
class ApiErrorController implements ErrorController {

	private static final Logger log = LoggerFactory.getLogger(ApiErrorController.class);

	@RequestMapping("${server.error.path:/error}")
	void error(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable exception) {
			log.error("Unexpected {}", exception.getClass().getName());
		}
		int status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code ? code : 500;
		response.resetBuffer();
		switch (status) {
			case 401 -> ApiErrorWriter.write(response, 401, "AUTHENTICATION_REQUIRED", "Authentication is required.");
			case 403 -> ApiErrorWriter.write(response, 403, "ACCESS_DENIED", "Access is denied.");
			case 404 -> ApiErrorWriter.write(response, 404, "NOT_FOUND", "Resource was not found.");
			case 406 -> ApiErrorWriter.write(response, 406, "NOT_ACCEPTABLE", "Response must be acceptable as JSON.");
			default -> {
				if (status >= 400 && status < 500) {
					ApiErrorWriter.write(response, 400, "INVALID_REQUEST", "Request is invalid.");
				}
				else {
					ApiErrorWriter.internalError(response);
				}
			}
		}
	}
}
