-- Phases group a course's lessons; their titles come verbatim from the source course index. Like every catalog row,
-- a phase is never deleted and nothing cascades.
CREATE TABLE code_academy.phases (
    id        text    PRIMARY KEY,
    course_id text    NOT NULL,
    title     text    NOT NULL,
    position  integer NOT NULL,
    CONSTRAINT phases_course_fkey FOREIGN KEY (course_id) REFERENCES code_academy.courses (id),
    -- Deferred so a reordering can swap positions inside the synchronization transaction.
    CONSTRAINT phases_course_position_key UNIQUE (course_id, position) DEFERRABLE INITIALLY DEFERRED,
    -- Target of the lessons' composite foreign key, which keeps a lesson's phase inside its course.
    CONSTRAINT phases_id_course_key UNIQUE (id, course_id),
    CONSTRAINT phases_position_positive CHECK (position > 0)
);

-- Nullable on purpose: databases upgraded from V4 already hold lessons and progress, and only the manifest knows each
-- lesson's phase. The startup synchronization fills it in the same transaction that checks it, and refuses to commit
-- while any lesson is left without a phase.
ALTER TABLE code_academy.lessons
    ADD COLUMN phase_id text,
    ADD CONSTRAINT lessons_phase_fkey FOREIGN KEY (phase_id, course_id)
        REFERENCES code_academy.phases (id, course_id);
