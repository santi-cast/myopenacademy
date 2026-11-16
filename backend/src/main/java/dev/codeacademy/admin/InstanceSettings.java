package dev.codeacademy.admin;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.codeacademy.api.ApiException;

/** The settings of this academy (H9): whether anyone may register, and the name it shows (null: its own). */
@Service
public class InstanceSettings {

	public record Settings(boolean registrationOpen, String instanceName) {
	}

	static final int MAX_NAME = 80;

	private final JdbcTemplate jdbc;

	InstanceSettings(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Settings current() {
		return jdbc.queryForObject("select registration_open, instance_name from code_academy.instance_settings where id = 1",
			(row, i) -> new Settings(row.getBoolean("registration_open"), row.getString("instance_name")));
	}

	public boolean registrationOpen() {
		return current().registrationOpen();
	}

	/** A blank name goes back to the academy's own; a name is plain text, trimmed, up to 80 characters. */
	@Transactional
	Settings update(boolean registrationOpen, String instanceName) {
		String name = instanceName == null || instanceName.isBlank() ? null : instanceName.strip();
		if (name != null && (name.codePointCount(0, name.length()) > MAX_NAME || name.chars().anyMatch(Character::isISOControl))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INSTANCE_NAME", "The name is invalid.");
		}
		jdbc.update("update code_academy.instance_settings set registration_open = ?, instance_name = ?, updated_at = now() "
			+ "where id = 1", registrationOpen, name);
		return current();
	}
}
