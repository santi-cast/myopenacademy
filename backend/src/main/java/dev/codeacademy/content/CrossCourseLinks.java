package dev.codeacademy.content;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.codeacademy.content.Manifest.SourceLink;

/**
 * Links between courses (FORMATO.md 10.2; H8, T5). A course links to another with the full GitHub address of the file,
 * {@code <repositorio>/blob/main/<fichero>#ancla}. Once every course is loaded, such a link to a course that is installed
 * here becomes the link to its lesson, stage or document, with the anchor when the target has it; a link to a course that
 * is not installed, or to a file that is not one of those documents, keeps its GitHub address. Decided at load time, so
 * installing the target course later converts the link without installing the first one again.
 */
final class CrossCourseLinks {

	/** The anchor the sanitizer writes for an absolute link: always this exact form (ContentSanitizer). */
	private static final Pattern EXTERNAL = Pattern.compile("<a href=\"(https://[^\"]+)\" target=\"" + ContentSanitizer.EXTERNAL_TARGET
		+ "\" rel=\"" + ContentSanitizer.EXTERNAL_REL + "\">");
	private static final Pattern ID = Pattern.compile("\\sid=\"([^\"]+)\"");

	private record Target(String route, String id, Set<String> ids, Map<String, String> fragments) {
	}

	private final Map<String, Target> targets = new HashMap<>();

	private CrossCourseLinks(List<CourseContent> courses) {
		for (CourseContent course : courses) {
			String repository = normalize(course.manifest().source() == null ? null : course.manifest().source().repository());
			if (repository == null) continue;
			Map<String, Set<String>> rendered = renderedIds(course);
			for (SourceLink link : course.manifest().sourceToId()) {
				String id = link.lessonId() != null ? link.lessonId() : link.stageId() != null ? link.stageId() : link.documentId();
				if (id == null) continue;
				String route = link.lessonId() != null ? "/lessons/" : link.stageId() != null ? "/stages/" : "/documents/";
				targets.put(repository + "/blob/main/" + link.sourcePath(), new Target(route, id,
					rendered.getOrDefault(id, Set.of()), link.fragments() == null ? Map.of() : link.fragments()));
			}
		}
	}

	/** The courses with their links to the other installed courses converted; unchanged when there is nothing to convert. */
	static List<CourseContent> resolve(List<CourseContent> courses) {
		CrossCourseLinks links = new CrossCourseLinks(courses);
		if (links.targets.isEmpty()) return courses;
		List<CourseContent> result = new ArrayList<>();
		for (CourseContent course : courses) {
			Map<String, LessonContent> lessons = new LinkedHashMap<>();
			course.lessons().forEach((id, lesson) -> lessons.put(id, new LessonContent(lesson.lessonId(),
				links.rewrite(lesson.bodyHtml()), lesson.exercises().stream().map(exercise -> new ExerciseContent(exercise.id(),
					exercise.lessonId(), exercise.courseId(), exercise.title(), links.rewrite(exercise.statementHtml()),
					links.rewrite(exercise.solutionHtml()))).toList(), lesson.resourceIds())));
			result.add(new CourseContent(course.manifest(), java.util.Collections.unmodifiableMap(lessons), course.resources(),
				links.rewrite(course.stages()), links.rewrite(course.documents())));
		}
		return List.copyOf(result);
	}

	/** One HTML fragment rewritten against these courses (for the tests). */
	static String rewriteFor(List<CourseContent> courses, String html) {
		return new CrossCourseLinks(courses).rewrite(html);
	}

	private Map<String, StageContent> rewrite(Map<String, StageContent> contents) {
		Map<String, StageContent> result = new LinkedHashMap<>();
		contents.forEach((id, content) -> result.put(id, new StageContent(content.stageId(), rewrite(content.bodyHtml()),
			content.resourceIds())));
		return java.util.Collections.unmodifiableMap(result);
	}

	String rewrite(String html) {
		if (html == null || !html.contains("/blob/main/")) return html;
		Matcher anchor = EXTERNAL.matcher(html);
		StringBuilder result = new StringBuilder();
		while (anchor.find()) {
			String href = internal(anchor.group(1).replace("&amp;", "&"));
			anchor.appendReplacement(result, Matcher.quoteReplacement(href == null ? anchor.group() : "<a href=\"" + href + "\">"));
		}
		anchor.appendTail(result);
		return result.toString();
	}

	/** The internal address of a GitHub link to an installed course, or null to keep it. */
	private String internal(String url) {
		int hash = url.indexOf('#');
		String address = hash < 0 ? url : url.substring(0, hash);
		String path;
		try {
			path = URLDecoder.decode(address.replace("+", "%2B"), StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
		int blob = path.indexOf("/blob/main/");
		if (blob < 0) return null;
		Target target = targets.get(path.substring(0, blob).toLowerCase(Locale.ROOT) + path.substring(blob));
		if (target == null) return null;
		String href = target.route() + target.id();
		if (hash < 0) return href;
		String fragment;
		try {
			fragment = URLDecoder.decode(url.substring(hash + 1).replace("+", "%2B"), StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException ex) {
			return href;
		}
		// The anchor as GitHub computes it, mapped to the rendered heading or exercise; one the target lacks is dropped.
		String mapped = target.fragments().getOrDefault(fragment, fragment);
		if (target.ids().contains(mapped)) return href + "#" + mapped;
		return target.ids().contains(fragment) ? href + "#" + fragment : href;
	}

	/** The element IDs each rendered lesson, stage or document carries (headings and exercises). */
	private static Map<String, Set<String>> renderedIds(CourseContent course) {
		Map<String, Set<String>> ids = new HashMap<>();
		course.lessons().forEach((id, lesson) -> {
			Set<String> found = ids(lesson.bodyHtml());
			lesson.exercises().forEach(exercise -> {
				found.add(exercise.id());
				found.addAll(ids(exercise.statementHtml()));
				found.addAll(ids(exercise.solutionHtml()));
			});
			ids.put(id, found);
		});
		course.stages().forEach((id, stage) -> ids.put(id, ids(stage.bodyHtml())));
		course.documents().forEach((id, document) -> ids.put(id, ids(document.bodyHtml())));
		return ids;
	}

	private static Set<String> ids(String html) {
		Set<String> ids = new HashSet<>();
		if (html == null) return ids;
		Matcher id = ID.matcher(html);
		while (id.find()) ids.add(id.group(1));
		return ids;
	}

	/** {@code https://github.com/owner/repo}: lower case, without a trailing slash or .git; null if not https. */
	static String normalize(String repository) {
		if (repository == null || !repository.startsWith("https://")) return null;
		return repository.replaceAll("/+$", "").replaceAll("\\.git$", "").toLowerCase(Locale.ROOT);
	}
}
