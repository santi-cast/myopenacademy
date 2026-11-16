package dev.codeacademy.admin;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * What anyone may know about this academy, even before signing in (its name and whether registration is open), and the
 * administrator's settings (H9).
 */
@RestController
class InstanceController {

	record InstanceView(String name, boolean registrationOpen) {
	}

	record SettingsRequest(Boolean registrationOpen, String instanceName) {
	}

	private final InstanceSettings settings;

	InstanceController(InstanceSettings settings) {
		this.settings = settings;
	}

	@GetMapping(path = "/api/instance", produces = MediaType.APPLICATION_JSON_VALUE)
	InstanceView instance() {
		var current = settings.current();
		return new InstanceView(current.instanceName(), current.registrationOpen());
	}

	@GetMapping(path = "/api/admin/settings", produces = MediaType.APPLICATION_JSON_VALUE)
	InstanceSettings.Settings get() {
		return settings.current();
	}

	@PutMapping(path = "/api/admin/settings", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	InstanceSettings.Settings put(@RequestBody SettingsRequest request) {
		if (request == null || request.registrationOpen() == null) {
			throw new dev.codeacademy.api.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_SETTINGS",
				"registrationOpen is required.");
		}
		return settings.update(request.registrationOpen(), request.instanceName());
	}
}
