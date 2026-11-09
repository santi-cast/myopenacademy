package dev.codeacademy.progress;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import dev.codeacademy.api.ApiException;
import dev.codeacademy.progress.ProgressViews.Continuation;
import dev.codeacademy.progress.ProgressViews.Counts;
import dev.codeacademy.progress.ProgressViews.CourseView;
import dev.codeacademy.progress.ProgressViews.Enrollment;
import dev.codeacademy.progress.ProgressViews.ExerciseState;
import dev.codeacademy.progress.ProgressViews.ExerciseStateResult;
import dev.codeacademy.progress.ProgressViews.LessonState;
import dev.codeacademy.progress.ProgressViews.LessonStateResult;
import dev.codeacademy.progress.ProgressViews.MyCourse;
import dev.codeacademy.progress.ProgressViews.MyCourses;
import dev.codeacademy.progress.ProgressViews.Progress;
import dev.codeacademy.progress.ProgressViews.StageContinuation;
import dev.codeacademy.progress.ProgressViews.StageCounts;
import dev.codeacademy.progress.ProgressViews.StageProgress;
import dev.codeacademy.progress.ProgressViews.StageState;
import dev.codeacademy.progress.ProgressViews.StageStateResult;

/**
 * Enrollments, states and continuation of one user, always the session's: no method takes another owner. Catalog
 * facts (existence, course, availability) come from the stored catalog. Every check runs before the first write, so
 * a rejected request changes nothing; a mark on a course not yet added creates the enrollment, the state and the
 * initial continuation in the same transaction.
 */
@Service
class ProgressService {

	private record LessonRow(String id, String courseId, boolean available) {
	}

	private record ExerciseRow(String id, String courseId, String lessonId, boolean lessonAvailable) {
	}

	private record EnrollmentRow(long id, String lastOpenedLessonId, String lastOpenedStageId) {
	}

	private record StageRow(String id, String courseId, boolean available) {
	}

	/** The enrollment a mark writes into, and whether this very operation created it. */
	private record Enrolled(EnrollmentRow enrollment, boolean created) {
	}

	private record SavedState(String state, String updatedAt) {
	}

	private final JdbcTemplate jdbc;

	ProgressService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * One snapshot for the whole answer, so counts and continuation never mix two moments. Courses follow their IDs
	 * byte by byte (collate "C"), the same order with any database collation; which order learners should see is an
	 * open question for the author (T3b).
	 */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	MyCourses courses(UUID user) {
		List<MyCourse> courses = jdbc.query("""
				select c.id, c.title, e.id as enrollment_id, e.last_opened_lesson_id, e.last_opened_stage_id
				from code_academy.enrollments e join code_academy.courses c on c.id = e.course_id
				where e.user_id = ? order by c.id collate "C"
				""", (row, i) -> {
			var enrollment = new EnrollmentRow(row.getLong("enrollment_id"), row.getString("last_opened_lesson_id"),
					row.getString("last_opened_stage_id"));
			return new MyCourse(new CourseView(row.getString("id"), row.getString("title")),
					enrollment(row.getString("id"), enrollment));
		}, user);
		return new MyCourses(courses);
	}

	/** Explicit add: an enrollment with an empty continuation; repeating it changes nothing. */
	@Transactional
	Enrollment add(UUID user, String courseId) {
		requireCourse(courseId);
		return enrollment(courseId, enroll(user, courseId, null).enrollment());
	}

	/** Existing course without enrollment: added false, no states, no continuation, and nothing is created. */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	Progress progress(UUID user, String courseId) {
		requireCourse(courseId);
		Optional<EnrollmentRow> enrollment = findEnrollment(user, courseId);
		if (enrollment.isEmpty()) {
			return new Progress(courseId, false, List.of(), List.of(), new Continuation(null), counts(courseId, null));
		}
		long id = enrollment.get().id();
		List<LessonState> lessons = jdbc.query("""
				select s.lesson_id, s.state, s.updated_at
				from code_academy.lesson_states s join code_academy.lessons l on l.id = s.lesson_id
				where s.enrollment_id = ? order by l.position
				""", (row, i) -> new LessonState(row.getString("lesson_id"), row.getString("state"), utc(row)), id);
		List<ExerciseState> exercises = jdbc.query("""
				select s.exercise_id, s.state, s.updated_at
				from code_academy.exercise_states s
				join code_academy.exercises x on x.id = s.exercise_id
				join code_academy.lessons l on l.id = x.lesson_id
				where s.enrollment_id = ? order by l.position, x.position
				""", (row, i) -> new ExerciseState(row.getString("exercise_id"), row.getString("state"), utc(row)), id);
		return new Progress(courseId, true, lessons, exercises,
				new Continuation(enrollment.get().lastOpenedLessonId()), counts(courseId, id));
	}

	@Transactional
	LessonStateResult markLesson(UUID user, String lessonId, String state) {
		LessonRow lesson = findLesson(lessonId).orElseThrow(ProgressService::notFound);
		if (!lesson.available()) throw lessonUnavailable();
		Enrolled enrolled = enroll(user, lesson.courseId(), lesson.id());
		long enrollmentId = enrolled.enrollment().id();
		// Only a real change of state moves updated_at.
		jdbc.update("""
				insert into code_academy.lesson_states (enrollment_id, course_id, lesson_id, state, updated_at)
				values (?, ?, ?, ?, now())
				on conflict (enrollment_id, lesson_id) do update set state = excluded.state, updated_at = excluded.updated_at
				where lesson_states.state is distinct from excluded.state
				""", enrollmentId, lesson.courseId(), lesson.id(), state);
		SavedState saved = jdbc.queryForObject("""
				select state, updated_at from code_academy.lesson_states where enrollment_id = ? and lesson_id = ?
				""", (row, i) -> new SavedState(row.getString("state"), utc(row)), enrollmentId, lesson.id());
		return new LessonStateResult(lesson.courseId(), lesson.id(), saved.state(), saved.updatedAt(), enrolled.created(),
				new Continuation(enrolled.enrollment().lastOpenedLessonId()), counts(lesson.courseId(), enrollmentId));
	}

	/** An exercise follows its lesson: unavailable with it, and its lesson is the initial continuation. */
	@Transactional
	ExerciseStateResult markExercise(UUID user, String exerciseId, String state) {
		if (!queryable(exerciseId)) throw notFound();
		ExerciseRow exercise = jdbc.query("""
				select x.id, x.course_id, x.lesson_id, l.available
				from code_academy.exercises x join code_academy.lessons l on l.id = x.lesson_id
				where x.id = ?
				""", (row, i) -> new ExerciseRow(row.getString("id"), row.getString("course_id"),
				row.getString("lesson_id"), row.getBoolean("available")), exerciseId)
			.stream().findFirst().orElseThrow(ProgressService::notFound);
		if (!exercise.lessonAvailable()) throw lessonUnavailable();
		Enrolled enrolled = enroll(user, exercise.courseId(), exercise.lessonId());
		long enrollmentId = enrolled.enrollment().id();
		jdbc.update("""
				insert into code_academy.exercise_states (enrollment_id, course_id, exercise_id, state, updated_at)
				values (?, ?, ?, ?, now())
				on conflict (enrollment_id, exercise_id) do update set state = excluded.state, updated_at = excluded.updated_at
				where exercise_states.state is distinct from excluded.state
				""", enrollmentId, exercise.courseId(), exercise.id(), state);
		SavedState saved = jdbc.queryForObject("""
				select state, updated_at from code_academy.exercise_states where enrollment_id = ? and exercise_id = ?
				""", (row, i) -> new SavedState(row.getString("state"), utc(row)), enrollmentId, exercise.id());
		return new ExerciseStateResult(exercise.courseId(), exercise.id(), saved.state(), saved.updatedAt(),
				enrolled.created(), new Continuation(enrolled.enrollment().lastOpenedLessonId()),
				counts(exercise.courseId(), enrollmentId));
	}

	/** Opening a lesson of an added course; never adds the course. */
	@Transactional
	Enrollment continueAt(UUID user, String courseId, String lessonId) {
		requireCourse(courseId);
		LessonRow lesson = findLesson(lessonId).orElseThrow(ProgressService::notFound);
		if (!lesson.courseId().equals(courseId)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COURSE_MISMATCH", "Lesson belongs to another course.");
		}
		if (!lesson.available()) throw lessonUnavailable();
		EnrollmentRow enrollment = findEnrollment(user, courseId).orElseThrow(() ->
				new ApiException(HttpStatus.CONFLICT, "COURSE_NOT_ADDED", "Course is not added."));
		jdbc.update("""
				update code_academy.enrollments set last_opened_lesson_id = ?
				where id = ? and last_opened_lesson_id is distinct from ?
				""", lesson.id(), enrollment.id(), lesson.id());
		return enrollment(courseId, new EnrollmentRow(enrollment.id(), lesson.id(), enrollment.lastOpenedStageId()));
	}

	/**
	 * Finds or creates the user's enrollment. Concurrent first marks race on the (user_id, course_id) unique key:
	 * ON CONFLICT DO NOTHING waits for the other transaction and, once it commits, returns no row, so exactly one
	 * operation reports the creation and the others read the committed enrollment (READ COMMITTED sees it).
	 */
	private Enrolled enroll(UUID user, String courseId, String initialLessonId) {
		return enroll(user, courseId, initialLessonId, null);
	}

	/** One insertion path for explicit additions and all kinds of first mark, including concurrent races. */
	private Enrolled enroll(UUID user, String courseId, String initialLessonId, String initialStageId) {
		List<Long> created = jdbc.queryForList("""
				insert into code_academy.enrollments (user_id, course_id, last_opened_lesson_id, last_opened_stage_id, created_at)
				values (?, ?, ?, ?, now())
				on conflict (user_id, course_id) do nothing
				returning id
				""", Long.class, user, courseId, initialLessonId, initialStageId);
		if (!created.isEmpty()) return new Enrolled(new EnrollmentRow(created.getFirst(), initialLessonId, initialStageId), true);
		return new Enrolled(findEnrollment(user, courseId).orElseThrow(), false);
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	StageProgress stageProgress(UUID user, String courseId) {
		requireCourse(courseId);
		return stageProgress(courseId, findEnrollment(user, courseId).orElse(null));
	}

	private StageProgress stageProgress(String courseId, EnrollmentRow enrollment) {
		if (enrollment == null) {
			return new StageProgress(courseId, false, List.of(), new StageContinuation(null), stageCounts(courseId, null));
		}
		List<StageState> states = jdbc.query("""
				select s.stage_id, s.state, s.updated_at
				from code_academy.stage_states s join code_academy.project_stages p on p.id = s.stage_id
				where s.enrollment_id = ? order by p.position
				""", (row, i) -> new StageState(row.getString("stage_id"), row.getString("state"), utc(row)), enrollment.id());
		return new StageProgress(courseId, true, states, new StageContinuation(enrollment.lastOpenedStageId()),
			stageCounts(courseId, enrollment.id()));
	}

	@Transactional
	StageStateResult markStage(UUID user, String stageId, String state) {
		StageRow stage = requireStage(stageId);
		if (!stage.available()) throw stageUnavailable();
		Enrolled enrolled = enroll(user, stage.courseId(), null, stage.id());
		long id = enrolled.enrollment().id();
		jdbc.update("""
				insert into code_academy.stage_states (enrollment_id, course_id, stage_id, state, updated_at)
				values (?, ?, ?, ?, now())
				on conflict (enrollment_id, stage_id) do update set state = excluded.state, updated_at = excluded.updated_at
				where stage_states.state is distinct from excluded.state
				""", id, stage.courseId(), stage.id(), state);
		SavedState saved = jdbc.queryForObject("""
				select state, updated_at from code_academy.stage_states where enrollment_id = ? and stage_id = ?
				""", (row, i) -> new SavedState(row.getString("state"), utc(row)), id, stage.id());
		return new StageStateResult(stage.courseId(), stage.id(), saved.state(), saved.updatedAt(), enrolled.created(),
			new StageContinuation(enrolled.enrollment().lastOpenedStageId()), stageCounts(stage.courseId(), id));
	}

	@Transactional
	StageProgress continueStageAt(UUID user, String courseId, String stageId) {
		requireCourse(courseId);
		StageRow stage = requireStage(stageId);
		if (!stage.courseId().equals(courseId)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COURSE_MISMATCH", "Stage belongs to another course.");
		}
		if (!stage.available()) throw stageUnavailable();
		EnrollmentRow enrollment = findEnrollment(user, courseId).orElseThrow(() ->
			new ApiException(HttpStatus.CONFLICT, "COURSE_NOT_ADDED", "Course is not added."));
		jdbc.update("""
				update code_academy.enrollments set last_opened_stage_id = ?
				where id = ? and last_opened_stage_id is distinct from ?
				""", stage.id(), enrollment.id(), stage.id());
		return stageProgress(courseId, new EnrollmentRow(enrollment.id(), enrollment.lastOpenedLessonId(), stage.id()));
	}

	private StageRow requireStage(String stageId) {
		if (!queryable(stageId)) throw notFound();
		return jdbc.query("select id, course_id, available from code_academy.project_stages where id = ?",
			(row, i) -> new StageRow(row.getString("id"), row.getString("course_id"), row.getBoolean("available")), stageId)
			.stream().findFirst().orElseThrow(ProgressService::notFound);
	}

	private StageCounts stageCounts(String courseId, Long enrollmentId) {
		return jdbc.queryForObject("""
				select count(*) filter (where p.available) as available, count(*) as planned,
				       count(*) filter (where p.available and s.state = 'completed') as completed
				from code_academy.project_stages p
				left join code_academy.stage_states s
				       on s.stage_id = p.id and s.course_id = p.course_id and s.enrollment_id = ?
				where p.course_id = ?
				""", (row, i) -> StageCounts.of(row.getInt("completed"), row.getInt("available"), row.getInt("planned")), enrollmentId, courseId);
	}

	private static ApiException stageUnavailable() {
		return new ApiException(HttpStatus.CONFLICT, "STAGE_UNAVAILABLE", "Stage is not available.");
	}

	private Optional<LessonRow> findLesson(String lessonId) {
		if (!queryable(lessonId)) return Optional.empty();
		return jdbc.query("select id, course_id, available from code_academy.lessons where id = ?",
				(row, i) -> new LessonRow(row.getString("id"), row.getString("course_id"), row.getBoolean("available")),
				lessonId).stream().findFirst();
	}

	private Optional<EnrollmentRow> findEnrollment(UUID user, String courseId) {
		return jdbc.query("""
				select id, last_opened_lesson_id, last_opened_stage_id from code_academy.enrollments where user_id = ? and course_id = ?
				""", (row, i) -> new EnrollmentRow(row.getLong("id"), row.getString("last_opened_lesson_id"), row.getString("last_opened_stage_id")),
				user, courseId).stream().findFirst();
	}

	private Enrollment enrollment(String courseId, EnrollmentRow enrollment) {
		return new Enrollment(courseId, true, new Continuation(enrollment.lastOpenedLessonId()),
				counts(courseId, enrollment.id()));
	}

	/** Completed states count only for lessons available now; without an enrollment nothing is completed. */
	private Counts counts(String courseId, Long enrollmentId) {
		return jdbc.queryForObject("""
				select count(*) filter (where l.available) as available, count(*) as planned,
				       count(*) filter (where l.available and s.state = 'completed') as completed
				from code_academy.lessons l
				left join code_academy.lesson_states s
				       on s.lesson_id = l.id and s.course_id = l.course_id and s.enrollment_id = ?
				where l.course_id = ?
				""", (row, i) -> Counts.of(row.getInt("completed"), row.getInt("available"), row.getInt("planned")),
				enrollmentId, courseId);
	}

	private void requireCourse(String courseId) {
		if (!queryable(courseId)) throw notFound();
		Integer found = jdbc.queryForObject("select count(*) from code_academy.courses where id = ?", Integer.class,
				courseId);
		if (found == null || found == 0) throw notFound();
	}

	/**
	 * PostgreSQL rejects U+0000 in text and no catalog ID contains it, so such an ID simply does not exist. Paths with
	 * NUL are already refused by the server; this keeps every ID that reaches a query safe, whatever its origin.
	 */
	private static boolean queryable(String id) {
		return id.indexOf('\u0000') < 0;
	}

	/** UTC ISO 8601, for example 2026-01-01T12:00:00Z, with fractional seconds when the stored time has them. */
	private static String utc(ResultSet row) throws SQLException {
		return row.getObject("updated_at", OffsetDateTime.class).toInstant().toString();
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource was not found.");
	}

	private static ApiException lessonUnavailable() {
		return new ApiException(HttpStatus.CONFLICT, "LESSON_UNAVAILABLE", "Lesson is not available.");
	}
}
