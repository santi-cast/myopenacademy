package dev.codeacademy.account;

import java.util.regex.Pattern;

/** Credential rules from docs/api.md; passwords are counted in Unicode code points and never altered. */
final class CredentialsPolicy {

	static final int MIN_PASSWORD_CODE_POINTS = 15;
	static final int MAX_PASSWORD_CODE_POINTS = 128;

	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,32}");

	private CredentialsPolicy() {
	}

	static boolean validUsername(String username) {
		return USERNAME.matcher(username).matches();
	}

	/** Registration and console reset: 15–128 code points, spaces and phrases allowed, no composition rules. */
	static boolean validNewPassword(CharSequence password) {
		if (!wellFormed(password)) return false;
		int codePoints = Character.codePointCount(password, 0, password.length());
		return codePoints >= MIN_PASSWORD_CODE_POINTS && codePoints <= MAX_PASSWORD_CODE_POINTS;
	}

	/** Login only guards the hashing cost; it never applies the registration minimum. */
	static boolean withinLoginLimit(String password) {
		return wellFormed(password) && password.codePointCount(0, password.length()) <= MAX_PASSWORD_CODE_POINTS;
	}

	/** An unpaired UTF-16 surrogate is not Unicode text: it cannot be encoded for Argon2, so it is never hashed. */
	static boolean wellFormed(CharSequence password) {
		for (int i = 0; i < password.length(); i++) {
			char c = password.charAt(i);
			if (Character.isHighSurrogate(c) && i + 1 < password.length()
					&& Character.isLowSurrogate(password.charAt(i + 1))) {
				i++;
			}
			else if (Character.isSurrogate(c)) {
				return false;
			}
		}
		return true;
	}
}
