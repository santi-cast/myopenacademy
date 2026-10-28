package dev.codeacademy.account;

import java.io.Serializable;
import java.util.UUID;

/** What the session keeps: identity and the credentials version it was granted under, never the hash. */
public record AccountPrincipal(UUID id, String username, long credentialsVersion) implements Serializable {

	static AccountPrincipal of(Account account) {
		return new AccountPrincipal(account.id(), account.username(), account.credentialsVersion());
	}
}
