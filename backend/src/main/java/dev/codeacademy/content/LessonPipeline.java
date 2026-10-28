package dev.codeacademy.content;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Document;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;

import dev.codeacademy.content.Manifest.Lesson;
import dev.codeacademy.content.Manifest.Resource;
import dev.codeacademy.content.Manifest.SourceLink;

/**
 * Renders the available lessons of a validated manifest: GitHub heading IDs, extraction of the exercises section,
 * link rewriting through sourceToId and sanitizing. Parsing all lessons first lets links check fragments of other
 * lessons. Every problem names the lesson (and exercise part) where it was found.
 */
final class LessonPipeline {

	static final String EXERCISES_SECTION = "Ejercicios prácticos resueltos";
	private static final Pattern EXERCISE_HEADING = Pattern.compile("Ejercicio \\d+ · (.+)");
	private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");
	private static final Pattern EXTERNAL = Pattern.compile("(?i)https?://[^\\s/?#]+.*");
	private static final Pattern DETAILS_OPEN = Pattern.compile("(?is)<details>.*");
	private static final Pattern DETAILS_CLOSE = Pattern.compile("(?is).*</details>");

	private final Manifest manifest;
	private final Map<String, Lesson> lessons = new HashMap<>();
	private final Map<String, Resource> resources = new HashMap<>();
	private final Map<String, String> originalNames;
	private final Map<String, SourceLink> sourceToId = new HashMap<>();
	private final Map<String, SourceLink> lessonLinks = new HashMap<>();
	private final Map<String, ParsedLesson> parsed = new LinkedHashMap<>();
	private final List<String> problems = new ArrayList<>();

	LessonPipeline(Manifest manifest, Map<String, String> originalNames) {
		this.manifest = manifest;
		this.originalNames = originalNames;
		manifest.lessons().forEach(lesson -> lessons.put(lesson.id(), lesson));
		manifest.resources().forEach(resource -> resources.put(resource.resourceId(), resource));
		for (SourceLink link : manifest.sourceToId()) {
			sourceToId.put(link.sourcePath(), link);
			if (link.lessonId() != null) lessonLinks.put(link.lessonId(), link);
		}
	}

	List<String> problems() {
		return problems;
	}

	// --- parsing and exercise extraction ---

	private record Part(String label, Document document) {
	}

	private static final class ParsedLesson {
		final Lesson lesson;
		final Map<Heading, String> headingIds = new IdentityHashMap<>();
		Document body;
		final List<String> titles = new ArrayList<>();
		final List<Document[]> exercises = new ArrayList<>();
		final Set<String> renderedIds = new HashSet<>();
		final Map<String, String> exerciseHeadings = new HashMap<>();

		ParsedLesson(Lesson lesson) {
			this.lesson = lesson;
		}

		/** The final fragment for a generated heading ID, or null when no rendered target carries it. */
		String target(String headingId) {
			if (headingId == null) return null;
			if (exerciseHeadings.containsKey(headingId)) return exerciseHeadings.get(headingId);
			return renderedIds.contains(headingId) ? headingId : null;
		}
	}

	void parse(Lesson lesson, String markdown) {
		ParsedLesson result = new ParsedLesson(lesson);
		Document document = (Document) Markdown.parse(markdown);
		HeadingIds ids = new HeadingIds();
		document.accept(new AbstractVisitor() {
			@Override
			public void visit(Heading heading) {
				result.headingIds.put(heading, ids.next(HeadingIds.plainText(heading)));
			}
		});
		List<String> exerciseHeadingIds = extractExercises(result, document);
		result.body = document;
		int expected = lesson.exercises().size();
		if (result.exercises.size() != expected) {
			problems.add(lesson.id() + ": " + result.exercises.size() + " exercises in the lesson, " + expected
					+ " in the manifest");
		}
		else {
			for (int i = 0; i < expected; i++) {
				result.exerciseHeadings.put(exerciseHeadingIds.get(i), lesson.exercises().get(i).id());
			}
		}
		List<Document> rendered = new ArrayList<>(List.of(document));
		result.exercises.forEach(parts -> rendered.addAll(List.of(parts)));
		rendered.forEach(part -> result.renderedIds.addAll(headingIdsIn(part, result.headingIds)));
		parsed.put(lesson.id(), result);
	}

	/** Removes the exercises section from the document; returns the heading ID of each exercise found. */
	private List<String> extractExercises(ParsedLesson result, Document document) {
		String lessonId = result.lesson.id();
		List<Heading> sections = new ArrayList<>();
		for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
			if (node instanceof Heading heading && HeadingIds.plainText(heading).contains(EXERCISES_SECTION)) {
				sections.add(heading);
			}
		}
		List<String> exerciseHeadingIds = new ArrayList<>();
		if (sections.isEmpty()) return exerciseHeadingIds;
		if (sections.size() > 1) {
			problems.add(lessonId + ": more than one «" + EXERCISES_SECTION + "» section");
			return exerciseHeadingIds;
		}
		Heading section = sections.get(0);
		List<Node> nodes = new ArrayList<>();
		for (Node node = section.getNext(); node != null; node = node.getNext()) {
			if (node instanceof Heading heading && heading.getLevel() <= section.getLevel()) break;
			nodes.add(node);
		}
		section.unlink();
		nodes.forEach(Node::unlink);

		List<Node> current = null;
		String currentTitle = null;
		List<List<Node>> exerciseNodes = new ArrayList<>();
		for (Node node : nodes) {
			if (node instanceof Heading heading) {
				Matcher matcher = EXERCISE_HEADING.matcher(HeadingIds.plainText(heading).strip());
				if (matcher.matches()) {
					current = new ArrayList<>();
					currentTitle = matcher.group(1).strip();
					exerciseNodes.add(current);
					result.titles.add(currentTitle);
					exerciseHeadingIds.add(result.headingIds.get(heading));
				}
				else {
					current = null; // level headings (🟢 Básico...) belong to no exercise and leave with the section
				}
			}
			else if (current != null) current.add(node);
			else if (!(node instanceof ThematicBreak)) {
				problems.add(lessonId + ": content outside any exercise in «" + EXERCISES_SECTION + "»");
			}
		}
		for (int i = 0; i < exerciseNodes.size(); i++) {
			Document[] parts = split(lessonId, result.titles.get(i), exerciseNodes.get(i));
			if (parts != null) result.exercises.add(parts);
		}
		return exerciseHeadingIds;
	}

	/** Statement up to the &lt;details&gt; block, and the solution block itself. */
	private Document[] split(String lessonId, String title, List<Node> nodes) {
		int open = -1;
		int close = -1;
		for (int i = 0; i < nodes.size(); i++) {
			if (nodes.get(i) instanceof HtmlBlock html) {
				String literal = html.getLiteral().strip();
				if (open < 0 && DETAILS_OPEN.matcher(literal).matches()) open = i;
				if (open >= 0 && DETAILS_CLOSE.matcher(literal).matches()) {
					close = i;
					break;
				}
			}
		}
		String name = lessonId + ": exercise " + title;
		if (open < 0 || close < 0) {
			problems.add(name + " has no <details> solution");
			return null;
		}
		if (open == 0) {
			problems.add(name + " has an empty statement");
			return null;
		}
		if (nodes.subList(close + 1, nodes.size()).stream().anyMatch(node -> !(node instanceof ThematicBreak))) {
			problems.add(name + " has content after its solution");
			return null;
		}
		return new Document[] { document(nodes.subList(0, open)), document(nodes.subList(open, close + 1)) };
	}

	private static Document document(List<Node> nodes) {
		Document document = new Document();
		nodes.forEach(document::appendChild);
		return document;
	}

	private static Set<String> headingIdsIn(Node document, Map<Heading, String> headingIds) {
		Set<String> ids = new HashSet<>();
		document.accept(new AbstractVisitor() {
			@Override
			public void visit(Heading heading) {
				ids.add(headingIds.get(heading));
			}
		});
		return ids;
	}

	// --- rendering ---

	Map<String, LessonContent> render() {
		Map<String, LessonContent> rendered = new LinkedHashMap<>();
		for (ParsedLesson lesson : parsed.values()) {
			String lessonId = lesson.lesson.id();
			Set<String> used = new LinkedHashSet<>();
			String body = render(lesson, new Part(lessonId, lesson.body), used);
			List<ExerciseContent> exercises = new ArrayList<>();
			for (int i = 0; i < lesson.exercises.size(); i++) {
				String exerciseId = lesson.lesson.exercises().get(i).id();
				Document[] parts = lesson.exercises.get(i);
				String statement = render(lesson, new Part(lessonId + " " + exerciseId + " statement", parts[0]), used);
				String solution = render(lesson, new Part(lessonId + " " + exerciseId + " solution", parts[1]), used);
				exercises.add(new ExerciseContent(exerciseId, lessonId, manifest.course().id(), lesson.titles.get(i),
						statement, solution));
			}
			rendered.put(lessonId, new LessonContent(lessonId, body, List.copyOf(exercises), List.copyOf(used)));
		}
		return Collections.unmodifiableMap(rendered);
	}

	private String render(ParsedLesson lesson, Part part, Set<String> usedResources) {
		List<Link> links = new ArrayList<>();
		part.document().accept(new AbstractVisitor() {
			@Override
			public void visit(Link link) {
				links.add(link);
				visitChildren(link);
			}
		});
		Set<String> hrefs = new HashSet<>();
		for (Link link : links) rewrite(lesson, part.label(), link, hrefs, usedResources);
		String html = Markdown.render(part.document(), lesson.headingIds);
		List<String> removed = new ArrayList<>();
		String clean = ContentSanitizer.sanitize(html, headingIdsIn(part.document(), lesson.headingIds), hrefs, removed);
		removed.forEach(item -> problems.add(part.label() + ": sanitizer removed " + item));
		return clean;
	}

	private void rewrite(ParsedLesson lesson, String label, Link link, Set<String> hrefs, Set<String> usedResources) {
		String destination = link.getDestination();
		if (SCHEME.matcher(destination).find() || destination.startsWith("/") || destination.startsWith("\\")) {
			if (EXTERNAL.matcher(destination).matches()) {
				hrefs.add(destination);
				return;
			}
			problems.add(label + ": disallowed link " + destination);
			unwrap(link);
			return;
		}
		String href = resolve(lesson, link, destination, hrefs, usedResources);
		if (href == null) {
			problems.add(label + ": unresolved link " + destination);
			unwrap(link);
		}
		else if (!href.isEmpty()) {
			link.setDestination(href);
			hrefs.add(href);
		}
	}

	/** The new href, "" when the link was replaced by folder file links, or null when it does not resolve. */
	private String resolve(ParsedLesson lesson, Link link, String destination, Set<String> hrefs, Set<String> usedResources) {
		if (destination.isEmpty() || destination.contains("?")) return null;
		int hash = destination.indexOf('#');
		String path = decode(hash < 0 ? destination : destination.substring(0, hash));
		String fragment = hash < 0 ? null : decode(destination.substring(hash + 1));
		if (path == null || (hash >= 0 && (fragment == null || fragment.isEmpty()))) return null;

		SourceLink target;
		if (path.isEmpty()) target = lessonLinks.get(lesson.lesson.id());
		else {
			String resolved = resolvePath(lesson.lesson.sourcePath(), path);
			if (resolved == null) return null;
			target = sourceToId.get(resolved);
			if (target == null && !resolved.endsWith("/")) target = sourceToId.get(resolved + "/");
		}
		if (target == null) return null;

		if (target.lessonId() != null) return lessonHref(lesson, target, fragment);
		if (fragment != null) return null;
		if (target.courseId() != null) return "/courses/" + target.courseId();
		if (target.resourceId() != null) {
			usedResources.add(target.resourceId());
			return "/api/resources/" + target.resourceId();
		}
		replaceFolder(link, target.resourceIds(), hrefs, usedResources);
		return "";
	}

	private String lessonHref(ParsedLesson current, SourceLink target, String fragment) {
		String lessonId = target.lessonId();
		ParsedLesson available = parsed.get(lessonId);
		if (fragment == null) return "/lessons/" + lessonId;
		// A pending lesson has no body to check the fragment against: link to the lesson, never a guessed fragment.
		if (available == null) return "/lessons/" + lessonId;
		Map<String, String> fragments = target.fragments() == null ? Map.of() : target.fragments();
		String id = available.target(fragments.get(fragment));
		if (id == null) return null;
		return available == current ? "#" + id : "/lessons/" + lessonId + "#" + id;
	}

	/** The folder text stays as plain text, followed by one link per file named like the original file. */
	private void replaceFolder(Link link, List<String> resourceIds, Set<String> hrefs, Set<String> usedResources) {
		unwrapChildren(link);
		link.insertBefore(new Text(" ("));
		for (int i = 0; i < resourceIds.size(); i++) {
			String resourceId = resourceIds.get(i);
			if (i > 0) link.insertBefore(new Text(", "));
			Link file = new Link("/api/resources/" + resourceId, null);
			file.appendChild(new Text(originalNames.get(resourceId)));
			link.insertBefore(file);
			hrefs.add(file.getDestination());
			usedResources.add(resourceId);
		}
		link.insertBefore(new Text(")"));
		link.unlink();
	}

	private static void unwrap(Link link) {
		unwrapChildren(link);
		link.unlink();
	}

	private static void unwrapChildren(Link link) {
		Node child = link.getFirstChild();
		while (child != null) {
			Node next = child.getNext();
			link.insertBefore(child);
			child = next;
		}
	}

	/** Percent-decoded text, or null when the encoding is malformed. */
	private static String decode(String text) {
		try {
			return URLDecoder.decode(text.replace("+", "%2B"), StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	/** Resolves a relative path against the directory of the source document; null if it climbs above the source root. */
	static String resolvePath(String documentPath, String relative) {
		Deque<String> segments = new ArrayDeque<>();
		int slash = documentPath.lastIndexOf('/');
		if (slash > 0) {
			for (String segment : documentPath.substring(0, slash).split("/")) segments.addLast(segment);
		}
		for (String segment : relative.split("/")) {
			if (segment.isEmpty() || segment.equals(".")) continue;
			if (segment.equals("..")) {
				if (segments.isEmpty()) return null;
				segments.removeLast();
			}
			else segments.addLast(segment);
		}
		if (segments.isEmpty()) return null;
		return String.join("/", segments) + (relative.endsWith("/") ? "/" : "");
	}
}
