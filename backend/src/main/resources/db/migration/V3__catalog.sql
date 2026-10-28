-- Shared catalog fed by the content manifests at startup. Bodies, exercises' text and resources stay in the
-- versioned content files: only identity, order and availability live here. Rows are never deleted.
CREATE TABLE code_academy.courses (
    id    text PRIMARY KEY,
    title text NOT NULL
);

CREATE TABLE code_academy.lessons (
    id               text    PRIMARY KEY,
    course_id        text    NOT NULL,
    title            text    NOT NULL,
    position         integer NOT NULL,
    available        boolean NOT NULL,
    source_available boolean NOT NULL,
    CONSTRAINT lessons_course_fkey FOREIGN KEY (course_id) REFERENCES code_academy.courses (id),
    -- Deferred so a reordering can swap positions inside the synchronization transaction.
    CONSTRAINT lessons_course_position_key UNIQUE (course_id, position) DEFERRABLE INITIALLY DEFERRED,
    -- Target of the composite foreign keys that keep exercises and progress inside the lesson's course.
    CONSTRAINT lessons_id_course_key UNIQUE (id, course_id),
    CONSTRAINT lessons_position_positive CHECK (position > 0),
    CONSTRAINT lessons_available_needs_source CHECK (source_available OR NOT available)
);

CREATE TABLE code_academy.exercises (
    id        text    PRIMARY KEY,
    course_id text    NOT NULL,
    lesson_id text    NOT NULL,
    title     text    NOT NULL,
    position  integer NOT NULL,
    CONSTRAINT exercises_lesson_fkey FOREIGN KEY (lesson_id, course_id)
        REFERENCES code_academy.lessons (id, course_id),
    CONSTRAINT exercises_lesson_position_key UNIQUE (lesson_id, position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT exercises_id_course_key UNIQUE (id, course_id),
    CONSTRAINT exercises_position_positive CHECK (position > 0)
);
