package dev.codeacademy.content;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H12, T13 (S15): Markdown task lists («- [ ]», «- [x]») render as disabled checkboxes. Only the checkbox the task list
 * extension emits survives the sanitizer, recognised by a marker that changes with every render; an input written as
 * raw HTML by the author is removed and reported exactly as before.
 */
class TaskListItemsTest {

	private static final String MARKER = "3f1c9a52-6d0e-4b7a-9c1e-0a2b4c6d8e0f";

	@TempDir
	Path tempDir;

	private String body(String markdown) {
		ContentFixture fixture = ContentFixture.copyInto(tempDir);
		fixture.replace(ContentFixture.INTRO, "Texto de cuerpo.", "Texto de cuerpo.\n\n" + markdown + "\n");
		return fixture.load().lessons().get("demo.01.01").bodyHtml();
	}

	@Test
	void uncheckedAndCheckedItemsRenderAsDisabledCheckboxesInsideTheirListItem() {
		String html = body("- [ ] Pintar el salón\n- [x] Comprar la pintura");

		assertThat(html).contains("<ul><li><input type=\"checkbox\" disabled=\"\" /> Pintar el salón</li>"
				+ "<li><input type=\"checkbox\" disabled=\"\" checked=\"\" /> Comprar la pintura</li></ul>");
		assertThat(html.split("<input", -1)).hasSize(3);
		assertThat(html).doesNotContain("[ ]", "[x]", "data-task", MARKER);
	}

	@Test
	void anUppercaseXAndANestedOrOrderedTaskItemAlsoRenderAsCheckboxes() {
		String html = body("1. [X] Hecha\n   - [ ] Dentro");

		assertThat(html).contains("<ol><li><input type=\"checkbox\" disabled=\"\" checked=\"\" /> Hecha\n"
				+ "<ul><li><input type=\"checkbox\" disabled=\"\" /> Dentro</li></ul>\n</li></ol>");
	}

	@Test
	void taskListSyntaxInsideAFencedCodeBlockStaysText() {
		String html = body("```markdown\n- [ ] Pintar el salón\n- [x] Comprar la pintura\n```");

		assertThat(html).contains("<code class=\"language-markdown\">- [ ] Pintar el salón\n- [x] Comprar la pintura\n</code>")
			.doesNotContain("<input");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"<input type=\"checkbox\">",
			"<input type=\"checkbox\" disabled>",
			"<input type=\"checkbox\" disabled=\"\" checked=\"\" />",
			"Texto <input type=\"checkbox\" disabled> en línea.",
			"- [ ] <input type=\"checkbox\" disabled>",
			"- <input type=\"checkbox\" disabled> escrita a mano",
			"<input type=\"checkbox\" disabled data-task=\"" + MARKER + "\">",
			"<input type=\"text\" disabled>",
	})
	void aCheckboxWrittenAsRawHtmlIsStillRejected(String raw) {
		ContentFixture fixture = ContentFixture.copyInto(tempDir);
		fixture.replace(ContentFixture.INTRO, "Texto de cuerpo.", "Texto de cuerpo.\n\n" + raw + "\n");

		fixture.rejected("sanitizer removed <input>");
	}

	@Test
	void theMarkedDisabledCheckboxPassesWithoutItsMarker() {
		List<String> removed = new ArrayList<>();

		String clean = sanitize("<ul>\n<li><input type=\"checkbox\" disabled=\"\" data-task=\"" + MARKER + "\"> a</li>\n"
				+ "<li><input type=\"checkbox\" disabled=\"\" checked=\"\" data-task=\"" + MARKER + "\"> b</li>\n</ul>",
				removed);

		assertThat(removed).isEmpty();
		// The sanitizer drops the whitespace between list items, as for every list.
		assertThat(clean).isEqualTo("<ul><li><input type=\"checkbox\" disabled=\"\" /> a</li>"
				+ "<li><input type=\"checkbox\" disabled=\"\" checked=\"\" /> b</li></ul>");
	}

	/** Exactly what the sanitizer removed and reported before task lists existed: the element, its text kept. */
	@ParameterizedTest
	@ValueSource(strings = {
			"<p>a <input type=\"checkbox\"> b</p>",
			"<p>a <input type=\"checkbox\" disabled=\"\"> b</p>",
			"<p>a <input type=\"checkbox\" disabled=\"\" checked=\"\"> b</p>",
			"<p>a <input type=\"text\" disabled> b</p>",
			"<p>a <input type=\"checkbox\" disabled name=\"n\" value=\"v\" onclick=\"x()\"> b</p>",
			"<p>a <input> b</p>",
			// A marker that is not this render's, or a checkbox that is enabled or not a checkbox, never passes.
			"<p>a <input type=\"checkbox\" disabled=\"\" data-task=\"otro\"> b</p>",
			"<p>a <input type=\"checkbox\" data-task=\"" + MARKER + "\"> b</p>",
			"<p>a <input type=\"checkbox\" checked=\"\" data-task=\"" + MARKER + "\"> b</p>",
			"<p>a <input type=\"radio\" disabled=\"\" data-task=\"" + MARKER + "\"> b</p>",
			"<p>a <input disabled=\"\" data-task=\"" + MARKER + "\"> b</p>",
			"<p>a <input type=\"checkbox\" disabled=\"no\" data-task=\"" + MARKER + "\"> b</p>",
	})
	void anyOtherInputIsRemovedAndReportedAsBefore(String html) {
		for (String marker : new String[] { MARKER, null }) {
			List<String> removed = new ArrayList<>();

			String clean = ContentSanitizer.sanitize(html, Set.of(), Set.of(), Set.of(), marker, removed);

			assertThat(clean).as(html).isEqualTo("<p>a  b</p>");
			assertThat(removed).as(html).containsExactly("<input>");
		}
	}

	@Test
	void withoutAMarkerEvenTheExtensionsCheckboxIsRemoved() {
		List<String> removed = new ArrayList<>();

		String clean = ContentSanitizer.sanitize("<p>a <input type=\"checkbox\" disabled=\"\" data-task=\"" + MARKER
				+ "\"> b</p>", Set.of(), Set.of(), removed);

		assertThat(clean).isEqualTo("<p>a  b</p>");
		assertThat(removed).containsExactly("<input>");
	}

	@ParameterizedTest
	@ValueSource(strings = { "name=\"n\"", "value=\"v\"", "onclick=\"x()\"", "id=\"c\"", "style=\"color:red\"",
			"class=\"c\"", "form=\"f\"" })
	void anExtraAttributeOnTheMarkedCheckboxIsReportedAndNeverReachesTheOutput(String attribute) {
		List<String> removed = new ArrayList<>();

		String clean = sanitize("<p><input type=\"checkbox\" disabled=\"\" " + attribute + " data-task=\"" + MARKER
				+ "\"> b</p>", removed);

		String name = attribute.substring(0, attribute.indexOf('='));
		assertThat(removed).containsExactly("attribute [" + name + "] on <input>");
		assertThat(ContentSanitizer.inert(removed.get(0))).isFalse();
		assertThat(clean).doesNotContain(name + "=", "data-task");
	}

	private static String sanitize(String html, List<String> removed) {
		return ContentSanitizer.sanitize(html, Set.of(), Set.of(), Set.of(), MARKER, removed);
	}
}
