package dev.codeacademy.content;

import java.util.List;
import java.util.Map;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * CommonMark with GFM tables, strikethrough and task list items. Fenced code keeps its language, escaped and not
 * highlighted.
 */
final class Markdown {

	/** Attribute that marks the checkbox of a task list item for the sanitizer; it never reaches the output. */
	static final String TASK_MARKER_ATTRIBUTE = "data-task";

	private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(), StrikethroughExtension.create(),
		TaskListItemsExtension.create());
	private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();

	private Markdown() {
	}

	static Node parse(String markdown) {
		Node document = PARSER.parse(markdown);
		separateHeaderFields(document);
		return document;
	}

	/**
	 * The header of a module or of the challenges (FORMATO.md 5.2) is a quote right after the title whose lines are
	 * fields: «**Versiones:** …», «**Tiempo estimado:** …». Written one after the other, Markdown joins them in one
	 * paragraph; here each field starts its own line (a hard break before every field but the first), without
	 * changing the format of the courses. A line that does not start with a field keeps joining the one before.
	 */
	static void separateHeaderFields(Node document) {
		Node title = document.getFirstChild();
		if (!(title instanceof Heading heading) || heading.getLevel() != 1) return;
		if (!(title.getNext() instanceof BlockQuote quote)) return;
		for (Node block = quote.getFirstChild(); block != null; block = block.getNext()) {
			if (!(block instanceof Paragraph paragraph) || !field(paragraph.getFirstChild())) continue;
			Node node = paragraph.getFirstChild();
			while (node != null) {
				Node next = node.getNext();
				if (node instanceof SoftLineBreak && field(next)) {
					node.insertBefore(new HardLineBreak());
					node.unlink();
				}
				node = next;
			}
		}
	}

	/** «**Nombre:**»: strong text that ends with a colon. */
	private static boolean field(Node node) {
		return node instanceof StrongEmphasis strong && strong.getFirstChild() instanceof Text text
			&& strong.getFirstChild().getNext() == null && text.getLiteral().endsWith(":");
	}

	/** As below, without a task marker: a task list checkbox is then removed by the sanitizer like any input. */
	static String render(Node document, Map<Heading, String> headingIds) {
		return render(document, headingIds, null);
	}

	/**
	 * Raw HTML is passed through here; the sanitizer decides what survives. The checkbox of a task list item carries
	 * {@code taskMarker}, a value that changes with every render, so the sanitizer can tell it from an input written
	 * as raw HTML, which cannot know it.
	 */
	static String render(Node document, Map<Heading, String> headingIds, String taskMarker) {
		return HtmlRenderer.builder()
			.extensions(EXTENSIONS)
			.attributeProviderFactory(context -> (node, tagName, attributes) -> {
				if (node instanceof Heading heading && headingIds.containsKey(heading)) {
					attributes.put("id", headingIds.get(heading));
				}
				if (taskMarker != null && node instanceof TaskListItemMarker && tagName.equals("input")) {
					attributes.put(TASK_MARKER_ATTRIBUTE, taskMarker);
				}
			})
			.build()
			.render(document);
	}
}
