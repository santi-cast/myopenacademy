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

	RegistrationService(AccountRepository accounts, PasswordEncoder passwordEncoder) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
	}

	Account register(String username, String password) {
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
