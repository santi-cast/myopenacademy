package dev.codeacademy.catalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.codeacademy.content.Manifest;
import dev.codeacademy.content.Manifest.Exercise;
import dev.codeacademy.content.Manifest.Lesson;
import dev.codeacademy.content.Manifest.Phase;

/**
 * Brings the catalog tables in line with the manifests in one transaction: inserts what is new and updates phase
 * titles and positions and lesson, exercise and stage titles, positions, phases and availability. Rows are never
 * deleted. A permanent ID that disappeared or changed course or lesson rejects the whole synchronization. Since H7 the
 * identity comes from the course's curso.json, not from a title: changing a title keeps the ID (FORMATO.md 11.2), so
 * it is an update, not a conflict. Moving a lesson to another phase is not a conflict: progress never refers to phases.
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
		jdbc.execute("lock table code_academy.courses, code_academy.phases, code_academy.lessons, "
				+ "code_academy.exercises, code_academy.project_stages in share row exclusive mode");
		List<String> problems = conflicts(manifests);
		if (!problems.isEmpty()) throw new CatalogConflictException(problems);

		jdbc.batchUpdate("""
				insert into code_academy.courses (id, title) values (?, ?)
				on conflict (id) do update set title = excluded.title
				where courses.title is distinct from excluded.title
				""", manifests.stream().map(m -> new Object[] { m.course().id(), m.course().title() }).toList());
		List<Object[]> phases = new ArrayList<>();
		List<Object[]> lessons = new ArrayList<>();
		List<Object[]> exercises = new ArrayList<>();
		List<Object[]> stages = new ArrayList<>();
		for (Manifest manifest : manifests) {
			String courseId = manifest.course().id();
			if (manifest.projectStages() != null) {
				for (var stage : manifest.projectStages()) {
					stages.add(new Object[] {stage.id(), courseId, stage.title(), stage.position(), stage.available(), stage.sourceAvailable()});
				}
			}
			for (Phase phase : manifest.phases()) {
				phases.add(new Object[] { phase.id(), courseId, phase.title(), phase.position() });
			}
			for (Lesson lesson : manifest.lessons()) {
				lessons.add(new Object[] { lesson.id(), courseId, lesson.title(), lesson.position(), lesson.phaseId(),
						lesson.available(), lesson.sourceAvailable() });
				for (int i = 0; i < lesson.exercises().size(); i++) {
					Exercise exercise = lesson.exercises().get(i);
					exercises.add(new Object[] { exercise.id(), courseId, lesson.id(), exercise.title(), i + 1 });
				}
			}
		}
		// Course and lesson of an existing ID were checked above, so only the descriptive columns are updated.
		jdbc.batchUpdate("""
				insert into code_academy.phases (id, course_id, title, position) values (?, ?, ?, ?)
				on conflict (id) do update set title = excluded.title, position = excluded.position
				where (phases.title, phases.position) is distinct from (excluded.title, excluded.position)
				""", phases);
		jdbc.batchUpdate("""
				insert into code_academy.lessons (id, course_id, title, position, phase_id, available, source_available)
				values (?, ?, ?, ?, ?, ?, ?)
				on conflict (id) do update set title = excluded.title, position = excluded.position,
				    phase_id = excluded.phase_id, available = excluded.available,
				    source_available = excluded.source_available
				where (lessons.title, lessons.position, lessons.phase_id, lessons.available, lessons.source_available)
				    is distinct from (excluded.title, excluded.position, excluded.phase_id, excluded.available,
				        excluded.source_available)
				""", lessons);
		jdbc.batchUpdate("""
				insert into code_academy.exercises (id, course_id, lesson_id, title, position) values (?, ?, ?, ?, ?)
				on conflict (id) do update set title = excluded.title, position = excluded.position
				where (exercises.title, exercises.position) is distinct from (excluded.title, excluded.position)
				""", exercises);
		jdbc.batchUpdate("""
				insert into code_academy.project_stages (id, course_id, title, position, available, source_available)
				values (?, ?, ?, ?, ?, ?)
				on conflict (id) do update set title = excluded.title, position = excluded.position,
				    available = excluded.available, source_available = excluded.source_available
				where (project_stages.title, project_stages.position, project_stages.available,
				    project_stages.source_available)
				    is distinct from (excluded.title, excluded.position, excluded.available, excluded.source_available)
				""", stages);
		// lessons.phase_id is nullable only so V5 could run on databases that already had lessons (see V5__phases.sql).
		List<String> withoutPhase = jdbc.queryForList(
				"select id from code_academy.lessons where phase_id is null order by id", String.class);
		if (!withoutPhase.isEmpty()) {
			throw new IllegalStateException("lessons without phase after synchronization: " + withoutPhase);
		}
	}

	private List<String> conflicts(List<Manifest> manifests) {
		List<String> problems = new ArrayList<>();
		Set<String> nonStageIds = new HashSet<>();
		for (Manifest manifest : manifests) {
			nonStageIds.add(manifest.course().id());
			manifest.phases().forEach(p -> nonStageIds.add(p.id()));
			manifest.lessons().forEach(l -> {
				nonStageIds.add(l.id());
				l.exercises().forEach(e -> nonStageIds.add(e.id()));
			});
			manifest.resources().forEach(r -> nonStageIds.add(r.resourceId()));
		}
		Map<String, String> phaseCourses = new HashMap<>();
		Map<String, String> lessonCourses = new HashMap<>();
		Map<String, Owner> exerciseOwners = new HashMap<>();
		Map<String, String> stageCourses = new HashMap<>();
		List<String> courseIds = new ArrayList<>();
		for (Manifest manifest : manifests) {
			courseIds.add(manifest.course().id());
			if (manifest.projectStages() != null) {
				Set<Integer> positions = new HashSet<>();
				int size = manifest.projectStages().size();
				manifest.projectStages().forEach(stage -> {
					if (stage.id() == null || stage.id().isBlank() || nonStageIds.contains(stage.id())
							|| stageCourses.putIfAbsent(stage.id(), manifest.course().id()) != null) {
						problems.add("invalid or duplicate stage id " + stage.id());
					}
					if (stage.position() == null || stage.position() < 1 || stage.position() > size
							|| !positions.add(stage.position())) problems.add("invalid stage position for " + stage.id());
					if (stage.available() == null || stage.sourceAvailable() == null
							|| (Boolean.TRUE.equals(stage.available()) && !Boolean.TRUE.equals(stage.sourceAvailable()))) {
						problems.add("invalid stage availability for " + stage.id());
					}
				});
			}
			manifest.phases().forEach(phase -> phaseCourses.put(phase.id(), manifest.course().id()));
			for (Lesson lesson : manifest.lessons()) {
				lessonCourses.put(lesson.id(), manifest.course().id());
				lesson.exercises().forEach(exercise -> {
					exerciseOwners.put(exercise.id(), new Owner(manifest.course().id(), lesson.id()));
				});
			}
		}
		jdbc.query("select id, course_id from code_academy.project_stages order by id", row -> {
			String id = row.getString("id");
			String stored = row.getString("course_id");
			String current = stageCourses.get(id);
			if (current == null) problems.add("stage " + id + " is no longer in the manifest");
			else if (!stored.equals(current)) {
				problems.add("stage " + id + " belongs to course " + stored + ", not " + current);
			}
		});
		jdbc.query("select id from code_academy.courses order by id", row -> {
			String id = row.getString("id");
			if (!courseIds.contains(id)) problems.add("course " + id + " is no longer in the content");
		});
		jdbc.query("select id, course_id from code_academy.phases order by id", row -> {
			String id = row.getString("id");
			String stored = row.getString("course_id");
			String current = phaseCourses.get(id);
			if (current == null) problems.add("phase " + id + " is no longer in the manifest");
			else if (!current.equals(stored)) {
				problems.add("phase " + id + " belongs to course " + stored + ", not " + current);
			}
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
