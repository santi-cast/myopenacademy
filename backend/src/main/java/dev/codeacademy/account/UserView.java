package dev.codeacademy.account;

/** The public User shape: never the hash or the credentials version. */
record UserView(String id, String username, String role, boolean mustChangePassword) {

	static UserView of(Account account) {
		return new UserView(account.id().toString(), account.username(), account.role(), account.mustChangePassword());
	}
}
