-- Private progress: one enrollment per user and course, and the learner's manual lesson and exercise states.
-- Composite foreign keys keep every reference inside the enrollment's course. Percentages are computed, never
-- stored. Nothing cascades: progress is never deleted with a user, course, lesson or exercise.
CREATE TABLE code_academy.enrollments (
    id                    bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id               uuid        NOT NULL,
    course_id             text        NOT NULL,
    -- Continuation: the last lesson opened in this course, empty until one is opened or a mark adds the course.
    last_opened_lesson_id text,
    created_at            timestamptz NOT NULL,
    CONSTRAINT enrollments_user_fkey FOREIGN KEY (user_id) REFERENCES code_academy.users (id),
    CONSTRAINT enrollments_course_fkey FOREIGN KEY (course_id) REFERENCES code_academy.courses (id),
    CONSTRAINT enrollments_user_course_key UNIQUE (user_id, course_id),
    -- Target of the states' composite foreign keys.
    CONSTRAINT enrollments_id_course_key UNIQUE (id, course_id),
    CONSTRAINT enrollments_last_opened_lesson_fkey FOREIGN KEY (last_opened_lesson_id, course_id)
        REFERENCES code_academy.lessons (id, course_id)
);

CREATE TABLE code_academy.lesson_states (
    enrollment_id bigint      NOT NULL,
    course_id     text        NOT NULL,
    lesson_id     text        NOT NULL,
    state         text        NOT NULL,
    -- Time of the last real change of state; repeating the same state keeps it.
    updated_at    timestamptz NOT NULL,
    CONSTRAINT lesson_states_enrollment_lesson_key UNIQUE (enrollment_id, lesson_id),
    CONSTRAINT lesson_states_enrollment_fkey FOREIGN KEY (enrollment_id, course_id)
        REFERENCES code_academy.enrollments (id, course_id),
    CONSTRAINT lesson_states_lesson_fkey FOREIGN KEY (lesson_id, course_id)
        REFERENCES code_academy.lessons (id, course_id),
    CONSTRAINT lesson_states_state_check CHECK (state IN ('not_completed', 'completed'))
);

CREATE TABLE code_academy.exercise_states (
    enrollment_id bigint      NOT NULL,
    course_id     text        NOT NULL,
    exercise_id   text        NOT NULL,
    state         text        NOT NULL,
    updated_at    timestamptz NOT NULL,
    CONSTRAINT exercise_states_enrollment_exercise_key UNIQUE (enrollment_id, exercise_id),
    CONSTRAINT exercise_states_enrollment_fkey FOREIGN KEY (enrollment_id, course_id)
        REFERENCES code_academy.enrollments (id, course_id),
    CONSTRAINT exercise_states_exercise_fkey FOREIGN KEY (exercise_id, course_id)
        REFERENCES code_academy.exercises (id, course_id),
    CONSTRAINT exercise_states_state_check CHECK (state IN ('pending', 'in_progress', 'solved'))
);
