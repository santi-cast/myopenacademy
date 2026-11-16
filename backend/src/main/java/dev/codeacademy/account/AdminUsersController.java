package dev.codeacademy.account;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.codeacademy.api.ApiException;

/**
 * The users in the administration (H9): list them, deactivate or reactivate one and set a new password. Every change
 * closes that user's open sessions. An administrator never deactivates themselves, so an active one always remains.
 */
@RestController
class AdminUsersController {

	record AdminUserView(String id, String username, String role, boolean active, Instant createdAt) {
		static AdminUserView of(Account account) {
			return new AdminUserView(account.id().toString(), account.username(), account.role(), account.active(),
				account.createdAt());
		}
	}

	record UsersView(List<AdminUserView> users) {
	}

	record ActiveRequest(Boolean active) {
	}

	record PasswordRequest(String password) {
	}

	private final AccountRepository accounts;
	private final PasswordEncoder passwordEncoder;

	AdminUsersController(AccountRepository accounts, PasswordEncoder passwordEncoder) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
	}

	@GetMapping(path = "/api/admin/users", produces = MediaType.APPLICATION_JSON_VALUE)
	UsersView users() {
		return new UsersView(accounts.findAllByOrderByUsernameCanonicalAsc().stream().map(AdminUserView::of).toList());
	}

	@Transactional
	@PutMapping(path = "/api/admin/users/{id}/active", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	AdminUserView active(@PathVariable String id, @RequestBody ActiveRequest request,
			@AuthenticationPrincipal AccountPrincipal admin) {
		if (request == null || request.active() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "active is required.");
		}
		Account account = account(id);
		if (!request.active()) {
			if (account.id().equals(admin.id())) {
				throw new ApiException(HttpStatus.CONFLICT, "CANNOT_DEACTIVATE_SELF", "You cannot deactivate yourself.");
			}
			if (account.admin() && account.active() && accounts.countActiveAdmins() <= 1) {
				throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN", "The last active administrator cannot be deactivated.");
			}
		}
		if (account.active() != request.active()) accounts.setActive(account.id(), request.active());
		return AdminUserView.of(account(id));
	}

	@Transactional
	@PutMapping(path = "/api/admin/users/{id}/password", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	AdminUserView password(@PathVariable String id, @RequestBody PasswordRequest request) {
		Account account = account(id);
		if (request == null || request.password() == null || !CredentialsPolicy.validNewPassword(request.password())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "Password is invalid.");
		}
		accounts.replacePasswordById(account.id(), passwordEncoder.encode(request.password()));
		return AdminUserView.of(account(id));
	}

	private Account account(String id) {
		UUID uuid;
		try {
			uuid = UUID.fromString(id);
		}
		catch (IllegalArgumentException ex) {
			throw notFound();
		}
		return accounts.findById(uuid).orElseThrow(AdminUsersController::notFound);
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found.");
	}
}
