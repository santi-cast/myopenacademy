package dev.codeacademy.content;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.owasp.html.HtmlChangeListener;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.HtmlSanitizer;
import org.owasp.html.PolicyFactory;

/**
 * Explicit allow-list for rendered lesson HTML. Only heading IDs generated for the fragment and only links produced by
 * the rewriting survive; absolute http/https links always open a new tab (target="_blank") with
 * rel="noopener noreferrer", internal, fragment and resource links never get a target. Everything removed is reported, so
 * real content that would be altered fails validation instead of being mangled silently.
 */
final class ContentSanitizer {

	static final Pattern LANGUAGE_CLASS = Pattern.compile("language-[a-z0-9+#-]+");
	// Resource IDs keep the letters of their source file name (for example «año»), so only that segment admits
	// Unicode letters and digits; lesson, stage, document and course IDs stay ASCII, and no form admits '/', '%' or spaces.
	static final Pattern HREF = Pattern.compile(
			"(/(?:lessons|stages|documents)/[A-Za-z0-9._-]+(#[^\\s\"<>]+)?|/courses/[A-Za-z0-9._-]+|/api/resources/[\\p{L}\\p{N}._-]+|#[^\\s\"<>]+|https?://[^\\s\"<>]+)");
	/** The marker of a task list checkbox for one render: a UUID, so it needs no escaping in an attribute. */
	static final Pattern TASK_MARKER = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
	static final String EXTERNAL_TARGET = "_blank";
	static final String EXTERNAL_REL = "noopener noreferrer";

	private static final String[] TEXT_ELEMENTS = { "p", "blockquote", "hr", "br", "h1", "h2", "h3", "h4", "h5", "h6",
			"ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td", "pre", "code", "em", "strong", "del", "kbd",
			"sup", "sub", "details", "summary" };

	/**
	 * Presentational and semantic containers whose removal cannot execute, embed or fetch anything: the element goes,
	 * its text stays. Nothing that can run, navigate, load a URL or hide a real payload is in this set. An anchor is
	 * handled separately by {@link #inertAnchor(String)}, because its href can be gone with it. Only the element as
	 * written without any attribute is inert: every attribute it carried is reported too and keeps failing.
	 */
	private static final Set<String> INERT = Set.of("span", "div", "u", "b", "i", "s", "mark", "ins", "small",
		"big", "tt", "font", "center", "strike", "abbr", "acronym", "cite", "q", "var", "samp", "dfn", "bdi", "bdo",
		"ruby", "rp", "rt", "wbr", "figure", "figcaption", "section", "article", "header", "footer", "main", "aside",
		"nav", "hgroup", "address", "time", "dl", "dt", "dd");

	private ContentSanitizer() {
	}

	/**
	 * True only when a reported removal is inert formatting: a tag that cannot run, embed, load an image or navigate,
	 * or the anchor pair the sanitizer drops together. An anchor that loses its href loses the capability too — the
	 * element and its href are gone and its text stays — so only that exact pair is inert. A removed attribute, an
	 * anchor that keeps a handler or any other attribute, a removed element that can run, embed, load an image or
	 * navigate, and every unknown ID keep failing the load; the rendered HTML never changes.
	 */
	static boolean inert(String removed) {
		if (removed == null) return false;
		if (inertAnchor(removed)) return true;
		if (!removed.startsWith("<") || !removed.endsWith(">")) return false;
		return INERT.contains(removed.substring(1, removed.length() - 1).toLowerCase(Locale.ROOT));
	}

	/**
	 * The reader gains no link capability: a removed anchor element, or the href it lost — the sanitizer reports both
	 * and drops both, so its text stays as inert formatting. Anything else lost on an anchor still fails.
	 */
	private static boolean inertAnchor(String removed) {
		return removed.equals("<a>") || removed.equals("attribute [href] on <a>");
	}

	/** Sanitizes html and appends to removed one entry per discarded element or attribute. */
	static String sanitize(String html, Set<String> headingIds, Set<String> hrefs, List<String> removed) {
		return sanitize(html, headingIds, hrefs, Set.of(), removed);
	}

	/** Image URLs must come from resolved Markdown nodes; raw HTML is checked separately without this permission. */
	static String sanitize(String html, Set<String> headingIds, Set<String> hrefs, Set<String> imageSources,
			List<String> removed) {
		return sanitize(html, headingIds, hrefs, imageSources, null, removed);
	}

	/**
	 * As above, and with a {@code taskMarker} the checkbox of a Markdown task list item survives (H12, T13): only
	 * {@code <input type="checkbox" disabled="">}, optionally {@code checked=""}, carrying that render's marker, which
	 * is then removed from the output. Raw HTML cannot know the marker, so any input it writes, and any input that is
	 * enabled, of another type or without the marker, is removed and reported as before; another attribute on the
	 * marked checkbox is reported too, so the load fails.
	 */
	static String sanitize(String html, Set<String> headingIds, Set<String> hrefs, Set<String> imageSources,
			String taskMarker, List<String> removed) {
		HtmlPolicyBuilder builder = new HtmlPolicyBuilder();
		if (!imageSources.isEmpty()) {
			builder.allowElements("img")
				.allowAttributes("src").matching(imageSources::contains).onElements("img")
				.allowAttributes("alt").onElements("img");
		}
		String markerAttribute = null;
		if (taskMarker != null) {
			if (!TASK_MARKER.matcher(taskMarker).matches()) throw new IllegalArgumentException("bad task marker");
			markerAttribute = " " + Markdown.TASK_MARKER_ATTRIBUTE + "=\"" + taskMarker + "\"";
			builder.allowAttributes("type").matching(false, "checkbox").onElements("input")
				.allowAttributes("disabled", "checked").matching(false, "").onElements("input")
				.allowAttributes(Markdown.TASK_MARKER_ATTRIBUTE).matching(taskMarker::equals).onElements("input")
				.allowElements((name, attributes) -> taskCheckbox(attributes, taskMarker) ? name : null, "input");
		}
		PolicyFactory policy = builder
			.allowElements(TEXT_ELEMENTS)
			.allowElements((name, attributes) -> {
				int href = attributes.indexOf("href");
				if (href >= 0 && attributes.get(href + 1).matches("(?i)https?:.*")) {
					attributes.addAll(List.of("target", EXTERNAL_TARGET, "rel", EXTERNAL_REL));
				}
				return name;
			}, "a")
			.allowAttributes("id").matching(headingIds::contains).onElements("h1", "h2", "h3", "h4", "h5", "h6")
			.allowAttributes("class").matching(LANGUAGE_CLASS).onElements("code")
			.allowAttributes("align").matching(true, "left", "center", "right").onElements("th", "td")
			.allowAttributes("start").matching(Pattern.compile("[0-9]{1,9}")).onElements("ol")
			.allowAttributes("href").matching(value -> hrefs.contains(value) && HREF.matcher(value).matches()).onElements("a")
			.allowUrlProtocols("http", "https")
			.toFactory();
		String clean = policy.sanitize(html, new HtmlChangeListener<List<String>>() {
			@Override
			public void discardedTag(List<String> context, String elementName) {
				context.add("<" + elementName + ">");
			}

			@Override
			public void discardedAttributes(List<String> context, String tagName, String... attributeNames) {
				context.add("attribute " + Arrays.toString(attributeNames) + " on <" + tagName + ">");
			}
		}, removed);
		reportDroppedAttributes(html, removed);
		return markerAttribute == null ? clean : clean.replace(markerAttribute, "");
	}

	/** What survived the attribute policies is the extension's checkbox: checkbox, disabled and this render's marker. */
	private static boolean taskCheckbox(List<String> attributes, String taskMarker) {
		boolean checkbox = false, disabled = false, marked = false;
		for (int i = 0; i < attributes.size(); i += 2) {
			String name = attributes.get(i), value = attributes.get(i + 1);
			if (name.equals("type")) checkbox = value.equals("checkbox");
			else if (name.equals("disabled")) disabled = value.isEmpty();
			else if (name.equals(Markdown.TASK_MARKER_ATTRIBUTE)) marked = value.equals(taskMarker);
			else if (!name.equals("checked")) return false;
		}
		return checkbox && disabled && marked;
	}

	/**
	 * The policy reports a dropped element by its name only, so the attributes an inert element carried as written
	 * are reported here, in the same form: a handler, an id or a style on it fails the load as on any other element.
	 */
	private static void reportDroppedAttributes(String html, List<String> removed) {
		HtmlSanitizer.sanitize(html, new HtmlSanitizer.Policy() {
			@Override
			public void openTag(String elementName, List<String> attributes) {
				if (!INERT.contains(elementName) || attributes.isEmpty()) return;
				List<String> names = new ArrayList<>();
				for (int i = 0; i < attributes.size(); i += 2) names.add(attributes.get(i));
				removed.add("attribute " + names + " on <" + elementName + ">");
			}

			@Override
			public void closeTag(String elementName) {
			}

			@Override
			public void text(String text) {
			}

			@Override
			public void openDocument() {
			}

			@Override
			public void closeDocument() {
			}
		});
	}
}
