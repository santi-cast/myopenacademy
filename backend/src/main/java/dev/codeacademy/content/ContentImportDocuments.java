package dev.codeacademy.content;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;

/**
 * Reads the pinned source course with the same Markdown, heading and table rules the runtime uses: one parser
 * ({@link Markdown}), GitHub anchors from {@link HeadingIds} and the shared percent decoding of {@link LessonPipeline}.
 * The course index is read as the source inventory: one row per module and one row per evolved-project stage.
 */
final class ContentImportDocuments {

	private static final String PROJECT = "Proyecto evolutivo";
	private static final Pattern MODULE = Pattern.compile("[0-9]{2}\\.[0-9]{2}");
	private static final Pattern STAGE = Pattern.compile("◆\\s*([0-9]+)");
	private static final Set<String> IMAGES = Set.of("image/png", "image/jpeg", "image/svg+xml");
	private static final Set<String> TEXT = Set.of("java", "kt", "txt", "csv", "json", "xml", "properties", "yml", "yaml",
		"html", "htm", "css", "js", "ts", "tsx", "jsx", "sql", "sh", "bat", "cmd", "gradle", "toml", "ini", "conf",
		"gitignore", "gitattributes", "dockerignore", "dockerfile", "mod", "sum", "tsv", "env");

	/** One link or image of a document, with the first line of the document that contains its destination. */
	record Reference(String destination, int line, boolean image) {
	}

	/** One module row of the source index: the title and availability the index declares for its source path. */
	record LessonRow(String title, boolean available) {
	}

	/** One «Proyecto evolutivo» row: a project stage, separate from the counted lessons. */
	record StageRow(int position, String title, String sourcePath, boolean available) {
	}

	private ContentImportDocuments() {
	}

	/** The final GitHub heading IDs of one document, in order: the source anchors that links may target. */
	static List<String> anchors(String markdown) {
		return HeadingIds.forMarkdown(markdown);
	}

	static List<Reference> references(String markdown) {
		List<Reference> references = new ArrayList<>();
		Markdown.parse(markdown).accept(new AbstractVisitor() {
			@Override
			public void visit(Link link) {
				references.add(new Reference(link.getDestination(), line(markdown, link.getDestination()), false));
				visitChildren(link);
			}

			@Override
			public void visit(Image image) {
				references.add(new Reference(image.getDestination(), line(markdown, image.getDestination()), true));
				visitChildren(image);
			}
		});
		return references;
	}

	/** One row per module of the source index, keyed by the source path the index links to. */
	static Map<String, LessonRow> lessons(String markdown) {
		Map<String, LessonRow> rows = new TreeMap<>();
		for (TableBlock table : tables(Markdown.parse(markdown))) {
			for (TableRow row : rows(table)) {
				List<TableCell> cells = cells(row);
				if (cells.size() < 2 || !MODULE.matcher(HeadingIds.plainText(cells.get(0)).strip()).matches()) continue;
				Link link = firstLink(cells.get(1));
				if (link == null) continue;
				String path = LessonPipeline.decode(link.getDestination());
				rows.put(path == null ? link.getDestination() : path,
					new LessonRow(HeadingIds.plainText(link).strip(), available(cells)));
			}
		}
		return rows;
	}

	static List<StageRow> stages(String markdown) {
		List<StageRow> rows = new ArrayList<>();
		boolean section = false;
		for (Node node = Markdown.parse(markdown).getFirstChild(); node != null; node = node.getNext()) {
			if (node instanceof Heading heading) {
				section = HeadingIds.plainText(heading).contains(PROJECT);
				continue;
			}
			if (!section || !(node instanceof TableBlock table)) continue;
			for (TableRow row : rows(table)) {
				List<TableCell> cells = cells(row);
				if (cells.size() < 2) continue;
				Matcher marker = STAGE.matcher(HeadingIds.plainText(cells.get(0)).strip());
				if (!marker.matches()) continue;
				Link link = firstLink(cells.get(1));
				if (link == null) continue;
				String path = LessonPipeline.decode(link.getDestination());
				rows.add(new StageRow(Integer.parseInt(marker.group(1)), HeadingIds.plainText(link).strip(),
					path == null ? link.getDestination() : path, available(cells)));
			}
		}
		rows.sort(Comparator.comparingInt(StageRow::position));
		return rows;
	}

	/** A deterministic media type from the source extension; unknown files stay downloadable octets. */
	static String mediaType(String sourcePath) {
		String name = sourcePath.substring(sourcePath.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
		int dot = name.lastIndexOf('.');
		String extension = dot < 0 ? name : name.substring(dot + 1);
		// .env.example, the one .env.* name the policy lets through, is a plain text template.
		if (TEXT.contains(extension) || name.equals(".env.example")) return "text/plain; charset=utf-8";
		return switch (extension) {
			case "png" -> "image/png";
			case "jpg", "jpeg" -> "image/jpeg";
			case "svg" -> "image/svg+xml";
			case "gif" -> "image/gif";
			case "webp" -> "image/webp";
			case "pdf" -> "application/pdf";
			case "zip" -> "application/zip";
			case "md" -> "text/markdown; charset=utf-8";
			default -> "application/octet-stream";
		};
	}

	/** Images the shared pipeline may serve inline; any other file is a download. */
	static boolean image(String sourcePath) {
		return IMAGES.contains(mediaType(sourcePath));
	}

	/** The first line that contains the destination; the CommonMark build of this project reports no line numbers. */
	static int line(String markdown, String destination) {
		if (destination.isEmpty()) return 0;
		String[] lines = markdown.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].contains(destination)) return i + 1;
		}
		return 0;
	}

	private static boolean available(List<TableCell> cells) {
		return cells.stream().anyMatch(cell -> HeadingIds.plainText(cell).contains("✅"));
	}

	private static List<TableBlock> tables(Node document) {
		return all(document, TableBlock.class);
	}

	private static List<TableRow> rows(TableBlock table) {
		return all(table, TableRow.class);
	}

	private static List<TableCell> cells(TableRow row) {
		return all(row, TableCell.class);
	}

	private static Link firstLink(Node node) {
		List<Link> links = all(node, Link.class);
		return links.isEmpty() ? null : links.getFirst();
	}

	/** Every descendant of one node type, in document order: table nodes are extension nodes without typed visits. */
	private static <T extends Node> List<T> all(Node root, Class<T> type) {
		List<T> found = new ArrayList<>();
		collect(root, type, found);
		return found;
	}

	private static <T extends Node> void collect(Node node, Class<T> type, List<T> found) {
		if (type.isInstance(node)) found.add(type.cast(node));
		for (Node child = node.getFirstChild(); child != null; child = child.getNext()) collect(child, type, found);
	}
}
