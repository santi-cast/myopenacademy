package dev.codeacademy.catalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.codeacademy.content.Manifest;
import dev.codeacademy.content.Manifest.Exercise;
import dev.codeacademy.content.Manifest.Lesson;

/**
 * Brings the catalog tables in line with the manifests in one transaction: inserts what is new and updates titles,
 * positions and availability. Rows are never deleted, and a permanent ID that disappeared or changed course or lesson
 * rejects the whole synchronization (future retirements need H5).
 */
@Component
public class CatalogSynchronizer {

	private record Owner(String courseId, String lessonId) {
	}

	private final JdbcTemplate jdbc;

	CatalogSynchronizer(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public void synchronize(List<Manifest> manifests) {
		// Concurrent starts would otherwise interleave their checks and writes.
		jdbc.execute("lock table code_academy.courses, code_academy.lessons, code_academy.exercises "
				+ "in share row exclusive mode");
		List<String> problems = conflicts(manifests);
		if (!problems.isEmpty()) throw new CatalogConflictException(problems);

		jdbc.batchUpdate("""
				insert into code_academy.courses (id, title) values (?, ?)
				on conflict (id) do update set title = excluded.title
				where courses.title is distinct from excluded.title
				""", manifests.stream().map(m -> new Object[] { m.course().id(), m.course().title() }).toList());
		List<Object[]> lessons = new ArrayList<>();
		List<Object[]> exercises = new ArrayList<>();
		for (Manifest manifest : manifests) {
			String courseId = manifest.course().id();
			for (Lesson lesson : manifest.lessons()) {
				lessons.add(new Object[] { lesson.id(), courseId, lesson.title(), lesson.position(), lesson.available(),
						lesson.sourceAvailable() });
				for (int i = 0; i < lesson.exercises().size(); i++) {
					Exercise exercise = lesson.exercises().get(i);
					exercises.add(new Object[] { exercise.id(), courseId, lesson.id(), exercise.title(), i + 1 });
				}
			}
		}
		// Course and lesson of an existing ID were checked above, so only the descriptive columns are updated.
		jdbc.batchUpdate("""
				insert into code_academy.lessons (id, course_id, title, position, available, source_available)
				values (?, ?, ?, ?, ?, ?)
				on conflict (id) do update set title = excluded.title, position = excluded.position,
				    available = excluded.available, source_available = excluded.source_available
				where (lessons.title, lessons.position, lessons.available, lessons.source_available)
				    is distinct from (excluded.title, excluded.position, excluded.available, excluded.source_available)
				""", lessons);
		jdbc.batchUpdate("""
				insert into code_academy.exercises (id, course_id, lesson_id, title, position) values (?, ?, ?, ?, ?)
				on conflict (id) do update set title = excluded.title, position = excluded.position
				where (exercises.title, exercises.position) is distinct from (excluded.title, excluded.position)
				""", exercises);
	}

	private List<String> conflicts(List<Manifest> manifests) {
		Map<String, String> lessonCourses = new HashMap<>();
		Map<String, Owner> exerciseOwners = new HashMap<>();
		List<String> courseIds = new ArrayList<>();
		for (Manifest manifest : manifests) {
			courseIds.add(manifest.course().id());
			for (Lesson lesson : manifest.lessons()) {
				lessonCourses.put(lesson.id(), manifest.course().id());
				lesson.exercises().forEach(exercise ->
						exerciseOwners.put(exercise.id(), new Owner(manifest.course().id(), lesson.id())));
			}
		}
		List<String> problems = new ArrayList<>();
		jdbc.query("select id from code_academy.courses order by id", row -> {
			String id = row.getString("id");
			if (!courseIds.contains(id)) problems.add("course " + id + " is no longer in the content");
		});
		jdbc.query("select id, course_id from code_academy.lessons order by id", row -> {
			String id = row.getString("id");
			String stored = row.getString("course_id");
			String current = lessonCourses.get(id);
			if (current == null) problems.add("lesson " + id + " is no longer in the manifest");
			else if (!current.equals(stored)) {
				problems.add("lesson " + id + " belongs to course " + stored + ", not " + current);
			}
		});
		jdbc.query("select id, course_id, lesson_id from code_academy.exercises order by id", row -> {
			String id = row.getString("id");
			Owner stored = new Owner(row.getString("course_id"), row.getString("lesson_id"));
			Owner current = exerciseOwners.get(id);
			if (current == null) problems.add("exercise " + id + " is no longer in the manifest");
			else if (!Objects.equals(current, stored)) {
				problems.add("exercise " + id + " belongs to lesson " + stored.lessonId() + " of course "
						+ stored.courseId() + ", not " + current.lessonId() + " of course " + current.courseId());
			}
		});
		return problems;
	}
}
