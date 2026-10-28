package dev.codeacademy.content;

import java.util.List;
import java.util.Map;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/** CommonMark with GFM tables. Fenced code keeps its language as class language-x, escaped and not highlighted. */
final class Markdown {

	private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());
	private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();

	private Markdown() {
	}

	static Node parse(String markdown) {
		return PARSER.parse(markdown);
	}

	/** Raw HTML is passed through here; the sanitizer decides what survives. */
	static String render(Node document, Map<Heading, String> headingIds) {
		return HtmlRenderer.builder()
			.extensions(EXTENSIONS)
			.attributeProviderFactory(context -> (node, tagName, attributes) -> {
				if (node instanceof Heading heading && headingIds.containsKey(heading)) {
					attributes.put("id", headingIds.get(heading));
				}
			})
			.build()
			.render(document);
	}
}
