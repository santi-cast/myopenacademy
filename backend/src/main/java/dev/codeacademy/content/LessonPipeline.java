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

import dev.codeacademy.content.Manifest.Exercise;
import dev.codeacademy.content.Manifest.Lesson;
import dev.codeacademy.content.Manifest.Resource;
import dev.codeacademy.content.Manifest.SourceLink;

/**
 * Renders the available lessons of a validated manifest: GitHub heading IDs, extraction of the exercises section,
 * link rewriting through sourceToId and sanitizing. Parsing all lessons first lets links check fragments of other
 * lessons. Every problem names the lesson (and exercise part) where it was found.
 * <p>
 * An exercise of the lesson is associated with the manifest exercise whose title is exactly the plain text of its
 * heading after «Ejercicio N · » (code point equality, no normalization); its position and N never identify it.
 */
final class LessonPipeline {

	static final String EXERCISES_SECTION = "Ejercicios prácticos resueltos";
	private static final Pattern EXERCISE_HEADING = Pattern.compile("Ejercicio \\d+ · (.+)");
	private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");
	private static final Pattern EXTERNAL = Pattern.compile("(?i)https?://[^\\s/?#]+.*");
	private static final Pattern DETAILS_OPEN = Pattern.compile("(?is)<details>.*");
	private static final Pattern DETAILS_CLOSE = Pattern.compile("(?is).*</details>");
	private static final Pattern ID_ATTRIBUTE = Pattern.compile("\\sid=\"([^\"]*)\"");

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

	/** An exercise as found in the lesson: its heading title and ID, and its statement and solution (null if broken). */
	private record FoundExercise(String title, String headingId, Document[] parts) {
	}

	/** A manifest exercise with the statement and solution of its heading. */
	private record MatchedExercise(Exercise exercise, Document[] parts) {
	}

	private static final class ParsedLesson {
		final Lesson lesson;
		final Map<Heading, String> headingIds = new IdentityHashMap<>();
		Document body;
		final List<FoundExercise> found = new ArrayList<>();
		final List<MatchedExercise> exercises = new ArrayList<>();
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
		extractExercises(result, document);
		result.body = document;
		associate(result);
		List<Document> rendered = new ArrayList<>(List.of(document));
		result.exercises.forEach(matched -> rendered.addAll(List.of(matched.parts())));
		rendered.forEach(part -> result.renderedIds.addAll(headingIdsIn(part, result.headingIds)));
		parsed.put(lesson.id(), result);
	}

	/**
	 * Pairs each exercise found in the lesson with the manifest exercise of the same title, in manifest order. A heading
	 * without manifest entry, a manifest entry without heading or a repeated heading title is an import conflict.
	 */
	private void associate(ParsedLesson result) {
		String lessonId = result.lesson.id();
		Map<String, FoundExercise> byTitle = new HashMap<>();
		Set<String> repeated = new LinkedHashSet<>();
		for (FoundExercise found : result.found) {
			if (byTitle.putIfAbsent(found.title(), found) != null) repeated.add(found.title());
		}
		repeated.forEach(title -> problems.add(lessonId + ": more than one exercise heading titled «" + title + "»"));
		Set<String> manifestTitles = new HashSet<>();
		result.lesson.exercises().forEach(exercise -> manifestTitles.add(exercise.title()));
		for (FoundExercise found : result.found) {
			if (!manifestTitles.contains(found.title())) {
				problems.add(lessonId + ": exercise heading «" + found.title()
						+ "» has no exercise with that title in the manifest");
			}
		}
		for (Exercise exercise : result.lesson.exercises()) {
			FoundExercise found = byTitle.get(exercise.title());
			if (found == null) {
				problems.add(lessonId + ": exercise " + exercise.id() + " «" + exercise.title()
						+ "» has no heading with that title in the lesson");
			}
			else if (!repeated.contains(found.title())) {
				result.exerciseHeadings.put(found.headingId(), exercise.id());
				if (found.parts() != null) result.exercises.add(new MatchedExercise(exercise, found.parts()));
			}
		}
	}

	/** Removes the exercises section from the document and records every exercise found in it. */
	private void extractExercises(ParsedLesson result, Document document) {
		String lessonId = result.lesson.id();
		List<Heading> sections = new ArrayList<>();
		for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
			if (node instanceof Heading heading && HeadingIds.plainText(heading).contains(EXERCISES_SECTION)) {
				sections.add(heading);
			}
		}
		if (sections.isEmpty()) return;
		if (sections.size() > 1) {
			problems.add(lessonId + ": more than one «" + EXERCISES_SECTION + "» section");
			return;
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
		List<List<Node>> exerciseNodes = new ArrayList<>();
		List<String> titles = new ArrayList<>();
		List<String> headingIds = new ArrayList<>();
		for (Node node : nodes) {
			if (node instanceof Heading heading) {
				// CommonMark already trimmed the heading text; the title is compared as is, with no further trimming.
				Matcher matcher = EXERCISE_HEADING.matcher(HeadingIds.plainText(heading));
				if (matcher.matches()) {
					current = new ArrayList<>();
					exerciseNodes.add(current);
					titles.add(matcher.group(1));
					headingIds.add(result.headingIds.get(heading));
				}
				else {
					String text = HeadingIds.plainText(heading);
					if (text.startsWith("Ejercicio")) {
						problems.add(lessonId + ": exercise heading «" + text + "» does not match «Ejercicio N · Título»");
					}
					current = null; // level headings (🟢 Básico...) belong to no exercise and leave with the section
				}
			}
			else if (current != null) current.add(node);
			else if (!(node instanceof ThematicBreak)) {
				problems.add(lessonId + ": content outside any exercise in «" + EXERCISES_SECTION + "»");
			}
		}
		for (int i = 0; i < exerciseNodes.size(); i++) {
			result.found.add(new FoundExercise(titles.get(i), headingIds.get(i),
					split(lessonId, titles.get(i), exerciseNodes.get(i))));
		}
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
			Set<String> ids = new HashSet<>();
			String body = render(lesson, new Part(lessonId, lesson.body), used, ids);
			List<ExerciseContent> exercises = new ArrayList<>();
			for (MatchedExercise matched : lesson.exercises) {
				String exerciseId = matched.exercise().id();
				Document[] parts = matched.parts();
				String statement = render(lesson, new Part(lessonId + " " + exerciseId + " statement", parts[0]), used, ids);
				String solution = render(lesson, new Part(lessonId + " " + exerciseId + " solution", parts[1]), used, ids);
				// The manifest title is exactly the heading title, so the catalog and the API return the same string.
				exercises.add(new ExerciseContent(exerciseId, lessonId, manifest.course().id(), matched.exercise().title(),
						statement, solution));
			}
			rendered.put(lessonId, new LessonContent(lessonId, body, List.copyOf(exercises), List.copyOf(used)));
		}
		return Collections.unmodifiableMap(rendered);
	}

	/** ids collects the element IDs already rendered in the lesson: one repeated in any field is a validation error. */
	private String render(ParsedLesson lesson, Part part, Set<String> usedResources, Set<String> ids) {
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
		Matcher id = ID_ATTRIBUTE.matcher(clean);
		while (id.find()) {
			if (!ids.add(id.group(1))) problems.add(part.label() + ": repeated id " + id.group(1));
		}
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
