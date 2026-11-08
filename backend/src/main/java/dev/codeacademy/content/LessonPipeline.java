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
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;

import dev.codeacademy.content.Manifest.Exercise;
import dev.codeacademy.content.Manifest.Lesson;
import dev.codeacademy.content.Manifest.Resource;
import dev.codeacademy.content.Manifest.SourceLink;

/**
 * Renders available lessons and stages using one Markdown, heading, link and sanitizer pipeline. Stages retain their
 * entire body; only lessons extract assessed exercises. Parsing all documents first lets links check final fragments
 * across lessons and stages. Every problem names the document (and exercise part) where it was found.
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
	private static final Pattern DETAILS_TAG = Pattern.compile("(?i)<(/?)details(?=[\\s>])");
	private static final Pattern SUMMARY = Pattern.compile("(?is)<summary[^>]*>(.*?)</summary>");
	private static final String SOLUTION_SUMMARY = "Ver solución";
	private static final Pattern ID_ATTRIBUTE = Pattern.compile("\\sid=\"([^\"]*)\"");

	private final Manifest manifest;
	private final Map<String, Lesson> lessons = new HashMap<>();
	private final Map<String, Resource> resources = new HashMap<>();
	private final Map<String, String> originalNames;
	private final Map<String, SourceLink> sourceToId = new HashMap<>();
	private final Map<String, SourceLink> lessonLinks = new HashMap<>();
	private final Map<String, ParsedLesson> parsed = new LinkedHashMap<>();
	private final List<String> problems = new ArrayList<>();
	private final List<String> warnings = new ArrayList<>();

	LessonPipeline(Manifest manifest, Map<String, String> originalNames) {
		this.manifest = manifest;
		this.originalNames = originalNames;
		manifest.lessons().forEach(lesson -> lessons.put(lesson.id(), lesson));
		manifest.resources().forEach(resource -> resources.put(resource.resourceId(), resource));
		for (SourceLink link : manifest.sourceToId()) {
			sourceToId.put(link.sourcePath(), link);
			if (link.lessonId() != null) lessonLinks.put(link.lessonId(), link);
			if (link.stageId() != null) lessonLinks.put(link.stageId(), link);
		}
	}

	List<String> problems() {
		return problems;
	}

	/** Non-blocking observations: only inert formatting the sanitizer removed, with its document path and exact tag. */
	List<String> warnings() {
		return warnings;
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

	/** Shared parsed document; stage=true disables lesson exercise extraction and keeps the separate output model. */
	private static final class ParsedLesson {
		final String id;
		final String sourcePath;
		final List<Exercise> manifestExercises;
		final boolean stage;
		final Map<Heading, String> headingIds = new IdentityHashMap<>();
		Document body;
		final List<FoundExercise> found = new ArrayList<>();
		final List<MatchedExercise> exercises = new ArrayList<>();
		final Set<String> renderedIds = new HashSet<>();
		final Set<String> extracted = new HashSet<>();
		final Map<String, String> exerciseHeadings = new HashMap<>();

		ParsedLesson(String id, String sourcePath, List<Exercise> exercises, boolean stage) {
			this.id = id;
			this.sourcePath = sourcePath;
			this.manifestExercises = exercises;
			this.stage = stage;
		}

		/** The final fragment for a generated heading ID, or null when no rendered target carries it. */
		String target(String headingId) {
			if (headingId == null) return null;
			if (exerciseHeadings.containsKey(headingId)) return exerciseHeadings.get(headingId);
			return renderedIds.contains(headingId) ? headingId : null;
		}
	}

	void parse(Lesson lesson, String markdown) {
		parse(new ParsedLesson(lesson.id(), lesson.sourcePath(), lesson.exercises(), false), markdown);
	}

	void parse(Manifest.Stage stage, String markdown) {
		parse(new ParsedLesson(stage.id(), stage.sourcePath(), List.of(), true), markdown);
	}

	private void parse(ParsedLesson result, String markdown) {
		Document document = (Document) Markdown.parse(markdown);
		HeadingIds ids = new HeadingIds();
		document.accept(new AbstractVisitor() {
			@Override
			public void visit(Heading heading) {
				result.headingIds.put(heading, ids.next(HeadingIds.plainText(heading)));
			}
		});
		if (!result.stage) {
			extractExercises(result, document);
			associate(result);
		}
		result.body = document;
		List<Document> rendered = new ArrayList<>(List.of(document));
		result.exercises.forEach(matched -> rendered.addAll(List.of(matched.parts())));
		rendered.forEach(part -> result.renderedIds.addAll(headingIdsIn(part, result.headingIds)));
		parsed.put(result.id, result);
	}

	/**
	 * Pairs each exercise found in the lesson with the manifest exercise of the same title, in manifest order. A heading
	 * without manifest entry, a manifest entry without heading or a repeated heading title is an import conflict.
	 */
	private void associate(ParsedLesson result) {
		String lessonId = result.id;
		Map<String, FoundExercise> byTitle = new HashMap<>();
		Set<String> repeated = new LinkedHashSet<>();
		for (FoundExercise found : result.found) {
			if (byTitle.putIfAbsent(found.title(), found) != null) repeated.add(found.title());
		}
		repeated.forEach(title -> problems.add(lessonId + ": more than one exercise heading titled «" + title + "»"));
		Set<String> manifestTitles = new HashSet<>();
		result.manifestExercises.forEach(exercise -> manifestTitles.add(exercise.title()));
		for (FoundExercise found : result.found) {
			if (!manifestTitles.contains(found.title())) {
				problems.add(lessonId + ": exercise heading «" + found.title()
						+ "» has no exercise with that title in the manifest");
			}
		}
		for (Exercise exercise : result.manifestExercises) {
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

	/**
	 * The exercise titles one lesson body declares, in document order, with exactly the runtime extraction rule. The
	 * offline generator uses it to keep the accepted exercises and to give every new heading a stable ID.
	 */
	static List<String> exerciseTitles(String markdown) {
		List<String> titles = new ArrayList<>();
		Heading section = section((Document) Markdown.parse(markdown));
		if (section == null) return titles;
		for (Node node : sectionNodes(section)) {
			if (!(node instanceof Heading heading)) continue;
			Matcher matcher = EXERCISE_HEADING.matcher(HeadingIds.plainText(heading));
			if (matcher.matches()) titles.add(matcher.group(1));
		}
		return titles;
	}

	/** The single «Ejercicios prácticos resueltos» heading, or null when the document has none or more than one. */
	private static Heading section(Document document) {
		Heading found = null;
		for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
			if (node instanceof Heading heading && HeadingIds.plainText(heading).contains(EXERCISES_SECTION)) {
				if (found != null) return null;
				found = heading;
			}
		}
		return found;
	}

	/** Every node below the section heading and above the next heading of its own level or a higher one. */
	private static List<Node> sectionNodes(Heading section) {
		List<Node> nodes = new ArrayList<>();
		for (Node node = section.getNext(); node != null; node = node.getNext()) {
			if (node instanceof Heading heading && heading.getLevel() <= section.getLevel()) break;
			nodes.add(node);
		}
		return nodes;
	}

	/**
	 * Removes the exercises section from the document and records every exercise found in it. Exercise and grade
	 * headings leave with the section; authored prose outside an exercise stays in the lesson body, never dropped.
	 */
	private void extractExercises(ParsedLesson result, Document document) {
		String lessonId = result.id;
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
		List<Node> nodes = sectionNodes(section);
		String sectionId = result.headingIds.get(section);
		section.unlink();
		// The section heading has no rendered target: a fragment that names it resolves to this document's entry.
		if (sectionId != null) result.extracted.add(sectionId);

		List<Node> current = null;
		List<List<Node>> exerciseNodes = new ArrayList<>();
		List<String> titles = new ArrayList<>();
		List<String> headingIds = new ArrayList<>();
		for (Node node : nodes) {
			if (node instanceof Heading heading) {
				// CommonMark already trimmed the heading text; the title is compared as is, with no further trimming.
				Matcher matcher = EXERCISE_HEADING.matcher(HeadingIds.plainText(heading));
				heading.unlink();
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
			else if (node instanceof ThematicBreak) node.unlink();
		}
		for (int i = 0; i < exerciseNodes.size(); i++) {
			result.found.add(new FoundExercise(titles.get(i), headingIds.get(i),
					split(lessonId, titles.get(i), exerciseNodes.get(i))));
		}
	}

	/**
	 * Statement up to the solution block and, from it on, the solution itself. The solution is the top-level
	 * &lt;details&gt; whose &lt;summary&gt; announces «Ver solución», matched with balanced open and close so a nested
	 * block cannot end it early; without one, the first open and the first close are used. Everything after the
	 * solution stays inside it: authored content is never dropped. The thematic breaks that close the exercise leave
	 * with the section, as in H4, so the lesson body never keeps them.
	 */
	private Document[] split(String lessonId, String title, List<Node> nodes) {
		int open = -1;
		int close = -1;
		for (Span span : spans(nodes)) {
			if (announcesSolution(nodes, span)) {
				open = span.open();
				close = span.close();
				break;
			}
		}
		if (open < 0) {
			for (int i = 0; i < nodes.size(); i++) {
				if (!(nodes.get(i) instanceof HtmlBlock html)) continue;
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
		int end = nodes.size();
		while (end > close + 1 && nodes.get(end - 1) instanceof ThematicBreak) end--;
		nodes.subList(end, nodes.size()).forEach(Node::unlink);
		return new Document[] { document(nodes.subList(0, open)), document(nodes.subList(open, end)) };
	}

	/** One top-level &lt;details&gt; block: the node that opens it and the node that closes it. */
	private record Span(int open, int close) {
	}

	/** Top-level details blocks of one exercise, from balanced open/close counting inside the raw HTML nodes. */
	private static List<Span> spans(List<Node> nodes) {
		List<Span> spans = new ArrayList<>();
		int depth = 0;
		int open = -1;
		for (int i = 0; i < nodes.size(); i++) {
			if (!(nodes.get(i) instanceof HtmlBlock html)) continue;
			Matcher tag = DETAILS_TAG.matcher(html.getLiteral());
			while (tag.find()) {
				if (tag.group(1).isEmpty()) {
					if (depth == 0) open = i;
					depth++;
				}
				else if (depth > 0) {
					depth--;
					if (depth == 0) spans.add(new Span(open, i));
				}
			}
		}
		return spans;
	}

	/** True when the block declares a &lt;summary&gt; whose visible text is exactly «Ver solución». */
	private static boolean announcesSolution(List<Node> nodes, Span span) {
		StringBuilder raw = new StringBuilder();
		for (int i = span.open(); i <= span.close(); i++) {
			if (nodes.get(i) instanceof HtmlBlock html) raw.append(html.getLiteral()).append('\n');
		}
		Matcher summary = SUMMARY.matcher(raw);
		while (summary.find()) {
			String text = summary.group(1).replaceAll("<[^>]*>", "").strip();
			if (text.equals(SOLUTION_SUMMARY)) return true;
		}
		return false;
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

	private final Map<String, StageContent> renderedStages = new LinkedHashMap<>();

	Map<String, StageContent> stages() {
		return Collections.unmodifiableMap(renderedStages);
	}

	Map<String, LessonContent> render() {
		Map<String, LessonContent> rendered = new LinkedHashMap<>();
		for (ParsedLesson lesson : parsed.values()) {
			String lessonId = lesson.id;
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
			if (lesson.stage) renderedStages.put(lessonId, new StageContent(lessonId, body, List.copyOf(used)));
			else rendered.put(lessonId, new LessonContent(lessonId, body, List.copyOf(exercises), List.copyOf(used)));
		}
		return Collections.unmodifiableMap(rendered);
	}

	/** ids collects the element IDs already rendered in the lesson: one repeated in any field is a validation error. */
	private String render(ParsedLesson lesson, Part part, Set<String> usedResources, Set<String> ids) {
		List<Link> links = new ArrayList<>();
		List<Image> images = new ArrayList<>();
		part.document().accept(new AbstractVisitor() {
			@Override
			public void visit(Image image) {
				images.add(image);
				visitChildren(image);
			}

			@Override
			public void visit(HtmlBlock html) {
				checkRawImages(part.label(), html.getLiteral());
			}

			@Override
			public void visit(HtmlInline html) {
				checkRawImages(part.label(), html.getLiteral());
			}

			@Override
			public void visit(Link link) {
				links.add(link);
				visitChildren(link);
			}
		});
		Set<String> hrefs = new HashSet<>();
		for (Link link : links) rewrite(lesson, part.label(), link, hrefs, usedResources);
		Set<String> imageSources = new HashSet<>();
		for (Image image : images) rewriteImage(lesson, part.label(), image, imageSources, usedResources);
		String html = Markdown.render(part.document(), lesson.headingIds);
		List<String> removed = new ArrayList<>();
		String clean = ContentSanitizer.sanitize(html, headingIdsIn(part.document(), lesson.headingIds), hrefs,
				imageSources, removed);
		for (String item : removed) {
			// Only an inert formatting tag is a reported finding; anything else the sanitizer would alter still fails.
			if (ContentSanitizer.inert(item)) warnings.add(lesson.sourcePath + ": sanitizer removed " + item);
			else problems.add(part.label() + ": sanitizer removed " + item);
		}
		Matcher id = ID_ATTRIBUTE.matcher(clean);
		while (id.find()) {
			if (!ids.add(id.group(1))) problems.add(part.label() + ": repeated id " + id.group(1));
		}
		return clean;
	}

	/** Parse raw HTML with the image-free policy, so even a URL also emitted by Markdown cannot grant provenance. */
	private void checkRawImages(String label, String html) {
		List<String> removed = new ArrayList<>();
		ContentSanitizer.sanitize(html, Set.of(), Set.of(), removed);
		if (removed.contains("<img>")) problems.add(label + ": raw HTML images are not allowed");
	}

	private void rewriteImage(ParsedLesson lesson, String label, Image image, Set<String> sources,
			Set<String> usedResources) {
		String destination = image.getDestination();
		String path = decode(destination);
		SourceLink target = null;
		if (path != null && !path.isEmpty() && !path.contains("?") && !path.contains("#")
				&& !SCHEME.matcher(path).find() && !path.startsWith("/") && !path.startsWith("\\")) {
			target = sourceTarget(lesson, path);
		}
		Resource resource = target == null ? null : resources.get(target.resourceId());
		if (resource == null || !Set.of("image/png", "image/jpeg", "image/svg+xml").contains(resource.mediaType())) {
			problems.add(label + ": unresolved or disallowed image " + destination);
			image.unlink();
			return;
		}
		String src = "/api/resources/" + resource.resourceId();
		image.setDestination(src);
		sources.add(src);
		usedResources.add(resource.resourceId());
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
			// A .git, .env or .env.* target is excluded by policy and reported by the importer, so it keeps only its
			// text; any other target that does not resolve is still a problem.
			if (!protectedTarget(lesson, destination)) problems.add(label + ": unresolved link " + destination);
			unwrap(link);
		}
		else if (!href.isEmpty()) {
			link.setDestination(href);
			hrefs.add(href);
		}
	}

	/** Whether the relative destination names a source path the protection policy never copies. */
	private static boolean protectedTarget(ParsedLesson lesson, String destination) {
		// A query is never a source path: resolve() and the importer refuse it, so it stays a problem here too.
		if (destination.contains("?")) return false;
		int hash = destination.indexOf('#');
		String path = decode(hash < 0 ? destination : destination.substring(0, hash));
		String resolved = path == null || path.isEmpty() ? null : resolvePath(lesson.sourcePath, path);
		return resolved != null && ContentUpdateComparison.protectedName(resolved);
	}

	/** The new href, "" when the link was replaced by folder file links, or null when it does not resolve. */
	private String resolve(ParsedLesson lesson, Link link, String destination, Set<String> hrefs, Set<String> usedResources) {
		if (destination.isEmpty() || destination.contains("?")) return null;
		int hash = destination.indexOf('#');
		String path = decode(hash < 0 ? destination : destination.substring(0, hash));
		String fragment = hash < 0 ? null : decode(destination.substring(hash + 1));
		if (path == null || (hash >= 0 && (fragment == null || fragment.isEmpty()))) return null;

		SourceLink target = sourceTarget(lesson, path);
		if (target == null) return null;

		if (target.lessonId() != null || target.stageId() != null) return lessonHref(lesson, target, fragment);
		// The course index is not imported: its own entry answers every fragment, as with a pending document.
		if (target.courseId() != null) return "/courses/" + target.courseId();
		if (fragment != null) return null;
		if (target.resourceId() != null) {
			usedResources.add(target.resourceId());
			return "/api/resources/" + target.resourceId();
		}
		replaceFolder(link, target, hrefs, usedResources);
		return "";
	}

	/** One source-document-relative lookup for both links and images, including the v1 folder fallback. */
	private SourceLink sourceTarget(ParsedLesson lesson, String path) {
		if (path.isEmpty()) return lessonLinks.get(lesson.id);
		String resolved = resolvePath(lesson.sourcePath, path);
		if (resolved == null) return null;
		SourceLink target = sourceToId.get(resolved);
		if (target == null && !resolved.endsWith("/")) target = sourceToId.get(resolved + "/");
		return target;
	}

	private String lessonHref(ParsedLesson current, SourceLink target, String fragment) {
		String lessonId = target.stageId() == null ? target.lessonId() : target.stageId();
		String route = target.stageId() == null ? "/lessons/" : "/stages/";
		ParsedLesson available = parsed.get(lessonId);
		if (fragment == null) return route + lessonId;
		// A pending document has no body to check: link to its entry, never a guessed fragment.
		if (available == null) return route + lessonId;
		Map<String, String> fragments = target.fragments() == null ? Map.of() : target.fragments();
		String mapped = fragments.get(fragment);
		String id = available.target(mapped);
		if (id == null) {
			// The extracted exercises section leaves no rendered heading: offer the document entry, not an anchor.
			return mapped != null && available.extracted.contains(mapped) ? route + lessonId : null;
		}
		return available == current ? "#" + id : route + lessonId + "#" + id;
	}

	/** V1 folder text remains plain; a v2 ZIP decoration links it before the same ordered individual files. */
	private void replaceFolder(Link link, SourceLink folder, Set<String> hrefs, Set<String> usedResources) {
		List<String> resourceIds = folder.resourceIds();
		if (folder.zipResourceId() == null) unwrapChildren(link);
		else {
			Link archive = new Link("/api/resources/" + folder.zipResourceId(), null);
			while (link.getFirstChild() != null) archive.appendChild(link.getFirstChild());
			link.insertBefore(archive);
			hrefs.add(archive.getDestination());
			usedResources.add(folder.zipResourceId());
		}
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

	/** Percent-decoded text, or null when the encoding is malformed. Shared with the offline generator. */
	static String decode(String text) {
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
