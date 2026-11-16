package dev.codeacademy.account;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import dev.codeacademy.api.ApiException;

@Service
class RegistrationService {

	private final AccountRepository accounts;
	private final PasswordEncoder passwordEncoder;
	private final dev.codeacademy.admin.InstanceSettings settings;

	RegistrationService(AccountRepository accounts, PasswordEncoder passwordEncoder, dev.codeacademy.admin.InstanceSettings settings) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.settings = settings;
	}

	Account register(String username, String password) {
		// The administrator may close registration (H9): nothing is checked or created then.
		if (!settings.registrationOpen()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "REGISTRATION_CLOSED", "Registration is closed.");
		}
		if (!CredentialsPolicy.validUsername(username)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USERNAME", "Username is invalid.");
		}
		if (!CredentialsPolicy.validNewPassword(password)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "Password is invalid.");
		}
		if (accounts.existsByUsernameCanonical(Account.canonical(username))) throw usernameTaken();
		try {
			return accounts.saveAndFlush(new Account(username, passwordEncoder.encode(password)));
		}
		catch (DataIntegrityViolationException concurrentRegistration) {
			// The unique constraint decides when two registrations race for the same name.
			throw usernameTaken();
		}
	}

	private static ApiException usernameTaken() {
		return new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Username is already taken.");
	}
}
