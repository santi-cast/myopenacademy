package dev.codeacademy.account;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import dev.codeacademy.api.StrictJson;

@RestController
@RequestMapping("/api/auth")
class AuthController {

	private final RegistrationService registration;

	AuthController(RegistrationService registration) {
		this.registration = registration;
	}

	/** Creates the account only; the client logs in separately. */
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	UserView register(@RequestBody(required = false) JsonNode body) {
		List<String> fields = StrictJson.strings(body, "username", "password");
		return UserView.of(registration.register(fields.get(0), fields.get(1)));
	}
}
