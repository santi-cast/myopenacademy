package dev.codeacademy.account;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import dev.codeacademy.api.ApiException;

@Service
class LoginService {

	/** Hashed in place of malformed input, so an oversized or ill-formed password never reaches Argon2. */
	private static final String PLACEHOLDER = "placeholder-for-timing-only";

	private final AccountRepository accounts;
	private final PasswordEncoder passwordEncoder;
	private final String dummyHash;

	LoginService(AccountRepository accounts, PasswordEncoder passwordEncoder) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.dummyHash = passwordEncoder.encode(PLACEHOLDER);
	}

	/** Every invalid credential fails the same way; unknown users still pay one hash comparison. */
	Account authenticate(String username, String password) {
		boolean wellFormed = CredentialsPolicy.validUsername(username) && CredentialsPolicy.withinLoginLimit(password);
		Optional<Account> account = wellFormed
				? accounts.findByUsernameCanonical(Account.canonical(username))
				: Optional.empty();
		if (account.isPresent()) {
			if (passwordEncoder.matches(password, account.get().passwordHash())) {
				// Only after the right password: a deactivated account says so, and nothing else is revealed (H9).
				if (!account.get().active()) {
					throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "This account is disabled.");
				}
				return account.get();
			}
		}
		else {
			passwordEncoder.matches(wellFormed ? password : PLACEHOLDER, dummyHash);
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Username or password is incorrect.");
	}
}
