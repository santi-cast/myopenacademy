package dev.codeacademy.content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Text;

/**
 * GitHub-compatible heading IDs: the rule of ancla() in the source course (lower case, drop everything but word
 * characters, hyphens and spaces, spaces become hyphens) plus GitHub's numbering of repeated IDs.
 * One instance numbers the headings of one document, in order.
 */
final class HeadingIds {

	private final Map<String, Integer> occurrences = new HashMap<>();

	/** The next heading of the document, from its rendered plain text. */
	String next(String plainText) {
		String base = slug(plainText);
		String id = base;
		while (occurrences.containsKey(id)) {
			int count = occurrences.merge(base, 1, Integer::sum);
			id = base + "-" + count;
		}
		occurrences.put(id, 0);
		return id;
	}

	static String slug(String text) {
		String lower = text.strip().toLowerCase(Locale.ROOT);
		StringBuilder id = new StringBuilder(lower.length());
		lower.codePoints().forEach(cp -> {
			if (cp == ' ') id.append('-');
			else if (cp == '-' || isWordCharacter(cp)) id.appendCodePoint(cp);
		});
		return id.toString();
	}

	/** Python's Unicode \w: letters, numbers of any kind and the underscore; not combining marks. */
	private static boolean isWordCharacter(int cp) {
		if (cp == '_') return true;
		return switch (Character.getType(cp)) {
			case Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
					Character.MODIFIER_LETTER, Character.OTHER_LETTER, Character.DECIMAL_DIGIT_NUMBER,
					Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true;
			default -> false;
		};
	}

	/** The text a reader sees in the heading: text and inline code, without Markdown syntax or raw HTML. */
	static String plainText(Node node) {
		StringBuilder text = new StringBuilder();
		node.accept(new AbstractVisitor() {
			@Override
			public void visit(Text t) {
				text.append(t.getLiteral());
			}

			@Override
			public void visit(Code code) {
				text.append(code.getLiteral());
			}
		});
		return text.toString();
	}

	/** The IDs of every heading of a Markdown document, in order. */
	static List<String> forMarkdown(String markdown) {
		HeadingIds ids = new HeadingIds();
		List<String> result = new ArrayList<>();
		Markdown.parse(markdown).accept(new AbstractVisitor() {
			@Override
			public void visit(Heading heading) {
				result.add(ids.next(plainText(heading)));
			}
		});
		return result;
	}
}
