package dev.codeacademy.account;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class Account {

	@Id
	private UUID id;

	@Column(nullable = false, length = 32)
	private String username;

	@Column(name = "username_canonical", nullable = false, length = 32)
	private String usernameCanonical;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "credentials_version", nullable = false)
	private long credentialsVersion;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** "user" or "admin" (H9). */
	@Column(nullable = false, length = 16)
	private String role;

	/** A deactivated account cannot sign in and keeps its progress (H9). */
	@Column(nullable = false)
	private boolean active;

	/** Set by an administrator's reset: the user must choose a new password before anything else (H9, D3). */
	@Column(name = "must_change_password", nullable = false)
	private boolean mustChangePassword;

	public static final String USER = "user";
	public static final String ADMIN = "admin";

	protected Account() {
	}

	Account(String username, String passwordHash) {
		this.id = UUID.randomUUID();
		this.username = username;
		this.usernameCanonical = canonical(username);
		this.passwordHash = passwordHash;
		this.credentialsVersion = 1;
		this.createdAt = Instant.now();
		this.role = USER;
		this.active = true;
	}

	Account(String username, String passwordHash, String role) {
		this(username, passwordHash);
		this.role = role;
	}

	/** Usernames are ASCII by contract, so the root locale gives the same lower case as PostgreSQL. */
	static String canonical(String username) {
		return username.toLowerCase(Locale.ROOT);
	}

	public UUID id() {
		return id;
	}

	public String username() {
		return username;
	}

	String passwordHash() {
		return passwordHash;
	}

	public long credentialsVersion() {
		return credentialsVersion;
	}

	public String role() {
		return role;
	}

	public boolean admin() {
		return ADMIN.equals(role);
	}

	public boolean active() {
		return active;
	}

	public boolean mustChangePassword() {
		return mustChangePassword;
	}

	public Instant createdAt() {
		return createdAt;
	}
}
