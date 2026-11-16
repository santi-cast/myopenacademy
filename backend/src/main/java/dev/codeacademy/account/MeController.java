package dev.codeacademy.account;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

	/** The security chain has already checked the credentials version against the database. */
	@GetMapping(path = "/api/me", produces = MediaType.APPLICATION_JSON_VALUE)
	UserView me(@AuthenticationPrincipal AccountPrincipal principal) {
		return new UserView(principal.id().toString(), principal.username(), principal.role(),
				principal.mustChangePassword());
	}
}
