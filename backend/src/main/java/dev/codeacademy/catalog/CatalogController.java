package dev.codeacademy.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import dev.codeacademy.api.ApiException;
import dev.codeacademy.catalog.Catalog.LessonRef;
import dev.codeacademy.catalog.Catalog.ResourceRef;
import dev.codeacademy.catalog.Catalog.StageRef;
import dev.codeacademy.content.CourseContent;
import dev.codeacademy.content.ExerciseContent;
import dev.codeacademy.content.LessonContent;
import dev.codeacademy.content.Manifest;
import dev.codeacademy.content.Manifest.Lesson;

/** Shared catalog and content for any authenticated user (docs/api.md); nothing here reads or writes progress. */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class CatalogController {

	/**
	 * A course of the catalog with what curso.json says about it (H7, T6). Each recommendation keeps its ID; its title
	 * comes only from an installed course, so a recommended course that is not installed has a null title.
	 */
	record CourseView(String id, String title, String description, List<RecommendedView> recommended) {
	}

	record RecommendedView(String id, String title) {
	}

	CourseView view(Manifest.Course course) {
		List<RecommendedView> recommended = course.recommended() == null ? List.of() : course.recommended().stream()
			.map(id -> new RecommendedView(id, catalog.course(id).map(other -> other.manifest().course().title()).orElse(null)))
			.toList();
		return new CourseView(course.id(), course.title(), course.description(), recommended);
	}

	record PhaseView(String id, String title, int position) {
		static PhaseView of(Manifest.Phase phase) {
			return new PhaseView(phase.id(), phase.title(), phase.position());
		}
	}

	record LessonEntry(String id, String courseId, String title, int position, String phaseId, boolean available) {
		static LessonEntry of(LessonRef ref) {
			Lesson lesson = ref.lesson();
			return new LessonEntry(lesson.id(), ref.course().manifest().course().id(), lesson.title(), lesson.position(),
					lesson.phaseId(), lesson.available());
		}
	}

	record ExerciseView(String id, String lessonId, String courseId, String title, String statementHtml,
			String solutionHtml) {
		static ExerciseView of(ExerciseContent exercise) {
			return new ExerciseView(exercise.id(), exercise.lessonId(), exercise.courseId(), exercise.title(),
					exercise.statementHtml(), exercise.solutionHtml());
		}
	}

	/** fileName is the original file name, the one to show and to save the download as (S14). */
	record ResourceView(String resourceId, String url, String mediaType, String fileName) {
	}

	record CoursesView(List<CourseView> courses) {
	}

	record SyllabusView(CourseView course, List<PhaseView> phases, List<LessonEntry> lessons) {
	}

	record LessonView(LessonEntry lesson, String bodyHtml, List<ExerciseView> exercises, List<ResourceView> resources,
			String previousLessonId, String nextLessonId) {
	}

	record StageEntry(String id, String courseId, String title, int position, boolean available) {
		static StageEntry of(StageRef ref) {
			var stage = ref.stage();
			return new StageEntry(stage.id(), ref.course().manifest().course().id(), stage.title(), stage.position(), stage.available());
		}
	}

	record StagesView(String courseId, List<StageEntry> stages) {
	}

	record StageView(StageEntry stage, String bodyHtml, List<ResourceView> resources,
			String previousStageId, String nextStageId) {
	}

	private static final Logger log = LoggerFactory.getLogger(CatalogController.class);

	private final Catalog catalog;
	private final String firstCourse;

	CatalogController(Catalog catalog, @Value("${code-academy.first-course:}") String firstCourse) {
		this.catalog = catalog;
		this.firstCourse = firstCourse;
	}

	@GetMapping(path = "/api/courses", produces = MediaType.APPLICATION_JSON_VALUE)
	CoursesView courses() {
		// The same order as «Mis cursos» (CourseOrder).
		return new CoursesView(catalog.courses().stream().map(course -> view(course.manifest().course()))
				.sorted(CourseOrder.of(firstCourse, CourseView::id, CourseView::title)).toList());
	}

	@GetMapping(path = "/api/courses/{courseId}", produces = MediaType.APPLICATION_JSON_VALUE)
	SyllabusView course(@PathVariable String courseId) {
		CourseContent course = catalog.course(courseId).orElseThrow(CatalogController::notFound);
		return new SyllabusView(view(course.manifest().course()),
				Catalog.phases(course).stream().map(PhaseView::of).toList(), Catalog.syllabus(course).stream()
					.map(lesson -> LessonEntry.of(new LessonRef(course, lesson))).toList());
	}

	/** Pending lessons keep their syllabus entry but never open; neighbours skip them. */
	@GetMapping(path = "/api/lessons/{lessonId}", produces = MediaType.APPLICATION_JSON_VALUE)
	LessonView lesson(@PathVariable String lessonId) {
		LessonRef ref = catalog.lesson(lessonId).orElseThrow(CatalogController::notFound);
		if (!ref.lesson().available()) {
			throw new ApiException(HttpStatus.CONFLICT, "LESSON_UNAVAILABLE", "Lesson is not available.");
		}
		LessonContent content = ref.course().lessons().get(lessonId);
		List<Lesson> available = Catalog.syllabus(ref.course()).stream().filter(Lesson::available).toList();
		int index = available.indexOf(ref.lesson());
		List<ResourceView> resources = ref.course().manifest().resources().stream()
				.filter(resource -> lessonId.equals(resource.lessonId()))
				.map(resource -> new ResourceView(resource.resourceId(), "/api/resources/" + resource.resourceId(),
						resource.mediaType(), ref.course().resources().get(resource.resourceId()).originalFileName()))
				.toList();
		return new LessonView(LessonEntry.of(ref), content.bodyHtml(),
				content.exercises().stream().map(ExerciseView::of).toList(), resources,
				index > 0 ? available.get(index - 1).id() : null,
				index < available.size() - 1 ? available.get(index + 1).id() : null);
	}

	@GetMapping(path = "/api/courses/{courseId}/stages", produces = MediaType.APPLICATION_JSON_VALUE)
	StagesView stages(@PathVariable String courseId) {
		CourseContent course = catalog.course(courseId).orElseThrow(CatalogController::notFound);
		return new StagesView(courseId, Catalog.project(course).stream()
			.map(stage -> StageEntry.of(new StageRef(course, stage))).toList());
	}

	@GetMapping(path = "/api/stages/{stageId}", produces = MediaType.APPLICATION_JSON_VALUE)
	StageView stage(@PathVariable String stageId) {
		StageRef ref = catalog.stage(stageId).orElseThrow(CatalogController::notFound);
		if (!ref.stage().available()) {
			throw new ApiException(HttpStatus.CONFLICT, "STAGE_UNAVAILABLE", "Stage is not available.");
		}
		var available = Catalog.project(ref.course()).stream().filter(Manifest.Stage::available).toList();
		int index = available.indexOf(ref.stage());
		var resources = ref.course().manifest().resources().stream()
			.filter(resource -> stageId.equals(resource.stageId()))
			.map(resource -> new ResourceView(resource.resourceId(), "/api/resources/" + resource.resourceId(),
				resource.mediaType(), ref.course().resources().get(resource.resourceId()).originalFileName())).toList();
		return new StageView(StageEntry.of(ref), ref.course().stages().get(stageId).bodyHtml(), resources,
			index > 0 ? available.get(index - 1).id() : null,
			index < available.size() - 1 ? available.get(index + 1).id() : null);
	}

	/**
	 * The bytes of a registered resource of an available lesson or stage, under its original name (PNG/JPEG inline). Not JSON and
	 * not negotiated: a browser's Accept header never blocks it. Unknown, unauthorized or unreadable IDs are 404.
	 */
	@GetMapping("/api/resources/{resourceId}")
	void resource(@PathVariable String resourceId, HttpServletResponse response) throws IOException {
		ResourceRef ref = catalog.resource(resourceId).orElseThrow(CatalogController::notFound);
		if (!Catalog.ownerAvailable(ref)) throw notFound();
		byte[] bytes = catalog.read(ref).orElseThrow(() -> {
			log.warn("Registered resource {} failed its serve-time file check", resourceId);
			return notFound();
		});
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType(ref.resource().mediaType());
		boolean raster = ref.resource().mediaType().equals("image/png") || ref.resource().mediaType().equals("image/jpeg");
		response.setHeader("Content-Disposition", disposition(raster ? "inline" : "attachment", ref.file().originalFileName()));
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader("Content-Security-Policy", "sandbox");
		response.setHeader("Cache-Control", "private, no-store");
		response.setContentLength(bytes.length);
		response.getOutputStream().write(bytes);
	}

	/** RFC 6266: an ASCII-safe quoted name for old clients and the exact UTF-8 name in filename*. */
	static String attachment(String fileName) {
		return disposition("attachment", fileName);
	}

	private static String disposition(String type, String fileName) {
		String fallback = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
		StringBuilder encoded = new StringBuilder();
		for (byte b : fileName.getBytes(StandardCharsets.UTF_8)) {
			char c = (char) (b & 0xff);
			if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || "!#$&+-.^_`|~".indexOf(c) >= 0) {
				encoded.append(c);
			}
			else {
				encoded.append('%').append(String.format("%02X", b & 0xff));
			}
		}
		return type + "; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource was not found.");
	}
}
