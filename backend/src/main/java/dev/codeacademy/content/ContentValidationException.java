package dev.codeacademy.content;

import java.util.List;

/** The content cannot be loaded; nothing from it may be used. Lists every problem found in the failing phase. */
public class ContentValidationException extends RuntimeException {

	private final List<String> problems;

	public ContentValidationException(List<String> problems) {
		super("Invalid content:\n- " + String.join("\n- ", problems));
		this.problems = List.copyOf(problems);
	}

	public ContentValidationException(String problem) {
		this(List.of(problem));
	}

	public List<String> problems() {
		return problems;
	}
}
