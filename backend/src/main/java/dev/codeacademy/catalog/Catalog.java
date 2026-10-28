package dev.codeacademy.catalog;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.codeacademy.content.ContentLoader;
import dev.codeacademy.content.CourseContent;
import dev.codeacademy.content.Manifest;
import dev.codeacademy.content.Manifest.Lesson;
import dev.codeacademy.content.ResourceFile;

/** The validated content kept in memory for the catalog routes: courses, lessons and resources by ID. */
public final class Catalog {

	/** A lesson of the syllabus with its course; only available lessons have rendered content. */
	record LessonRef(CourseContent course, Lesson lesson) {
	}

	/** A registered resource with the lesson it belongs to. */
	record ResourceRef(CourseContent course, Manifest.Resource resource, ResourceFile file) {
	}

	private final Path contentRoot;
	private final List<CourseContent> courses;
	private final Map<String, CourseContent> coursesById = new LinkedHashMap<>();
	private final Map<String, LessonRef> lessons = new LinkedHashMap<>();
	private final Map<String, ResourceRef> resources = new LinkedHashMap<>();

	private Catalog(Path contentRoot, List<CourseContent> courses) {
		this.contentRoot = contentRoot;
		this.courses = courses;
		for (CourseContent course : courses) {
			coursesById.put(course.manifest().course().id(), course);
			course.manifest().lessons().forEach(lesson -> lessons.put(lesson.id(), new LessonRef(course, lesson)));
			course.manifest().resources().forEach(resource -> resources.put(resource.resourceId(),
					new ResourceRef(course, resource, course.resources().get(resource.resourceId()))));
		}
	}

	/** Loads and validates every course of the content root; nothing is kept if any of it is invalid. */
	public static Catalog load(Path contentRoot) {
		return new Catalog(contentRoot, ContentLoader.loadAll(contentRoot));
	}

	public List<Manifest> manifests() {
		return courses.stream().map(CourseContent::manifest).toList();
	}

	List<CourseContent> courses() {
		return courses;
	}

	Optional<CourseContent> course(String courseId) {
		return Optional.ofNullable(coursesById.get(courseId));
	}

	Optional<LessonRef> lesson(String lessonId) {
		return Optional.ofNullable(lessons.get(lessonId));
	}

	Optional<ResourceRef> resource(String resourceId) {
		return Optional.ofNullable(resources.get(resourceId));
	}

	/** The course's phases in syllabus order. */
	static List<Manifest.Phase> phases(CourseContent course) {
		return course.manifest().phases().stream().sorted(Comparator.comparing(Manifest.Phase::position)).toList();
	}

	/** The course's lessons in syllabus order. */
	static List<Lesson> syllabus(CourseContent course) {
		return course.manifest().lessons().stream().sorted(Comparator.comparing(Lesson::position)).toList();
	}

	/** The registered file bytes, re-checked against the content root at the time of serving. */
	Optional<byte[]> read(ResourceRef resource) {
		return ContentLoader.readResource(contentRoot, resource.resource().localPath(), resource.file().localPath());
	}
}
