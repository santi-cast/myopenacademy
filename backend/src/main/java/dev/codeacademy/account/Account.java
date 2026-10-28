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

	protected Account() {
	}

	Account(String username, String passwordHash) {
		this.id = UUID.randomUUID();
		this.username = username;
		this.usernameCanonical = canonical(username);
		this.passwordHash = passwordHash;
		this.credentialsVersion = 1;
		this.createdAt = Instant.now();
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
}
