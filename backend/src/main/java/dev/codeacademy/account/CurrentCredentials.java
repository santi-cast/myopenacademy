package dev.codeacademy.account;

import org.springframework.stereotype.Service;

/** Compares a session's credentials version with the database on every authenticated request. */
@Service
public class CurrentCredentials {

	private final AccountRepository accounts;

	CurrentCredentials(AccountRepository accounts) {
		this.accounts = accounts;
	}

	/** A query per call, no cache: a console reset invalidates older sessions on their next request. */
	public boolean isCurrent(AccountPrincipal principal) {
		return accounts.findCredentialsVersionById(principal.id())
				.map(version -> version == principal.credentialsVersion())
				.orElse(false);
	}
}
