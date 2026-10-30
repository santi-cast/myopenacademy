package dev.codeacademy.content;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.owasp.html.HtmlChangeListener;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/**
 * Explicit allow-list for rendered lesson HTML. Only heading IDs generated for the fragment and only links produced by
 * the rewriting survive; absolute http/https links always open a new tab (target="_blank") with
 * rel="noopener noreferrer", internal, fragment and resource links never get a target. Everything removed is reported, so
 * real content that would be altered fails validation instead of being mangled silently.
 */
final class ContentSanitizer {

	static final Pattern LANGUAGE_CLASS = Pattern.compile("language-[a-z0-9+#-]+");
	static final Pattern HREF = Pattern.compile(
			"(/(?:lessons|stages)/[A-Za-z0-9._-]+(#[^\\s\"<>]+)?|/courses/[A-Za-z0-9._-]+|/api/resources/[A-Za-z0-9._-]+|#[^\\s\"<>]+|https?://[^\\s\"<>]+)");
	static final String EXTERNAL_TARGET = "_blank";
	static final String EXTERNAL_REL = "noopener noreferrer";

	private static final String[] TEXT_ELEMENTS = { "p", "blockquote", "hr", "br", "h1", "h2", "h3", "h4", "h5", "h6",
			"ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td", "pre", "code", "em", "strong", "del", "kbd",
			"sup", "sub", "details", "summary" };

	private ContentSanitizer() {
	}

	/** Sanitizes html and appends to removed one entry per discarded element or attribute. */
	static String sanitize(String html, Set<String> headingIds, Set<String> hrefs, List<String> removed) {
		return sanitize(html, headingIds, hrefs, Set.of(), removed);
	}

	/** Image URLs must come from resolved Markdown nodes; raw HTML is checked separately without this permission. */
	static String sanitize(String html, Set<String> headingIds, Set<String> hrefs, Set<String> imageSources,
			List<String> removed) {
		HtmlPolicyBuilder builder = new HtmlPolicyBuilder();
		if (!imageSources.isEmpty()) {
			builder.allowElements("img")
				.allowAttributes("src").matching(imageSources::contains).onElements("img")
				.allowAttributes("alt").onElements("img");
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
		return policy.sanitize(html, new HtmlChangeListener<List<String>>() {
			@Override
			public void discardedTag(List<String> context, String elementName) {
				context.add("<" + elementName + ">");
			}

			@Override
			public void discardedAttributes(List<String> context, String tagName, String... attributeNames) {
				context.add("attribute " + Arrays.toString(attributeNames) + " on <" + tagName + ">");
			}
		}, removed);
	}
}
