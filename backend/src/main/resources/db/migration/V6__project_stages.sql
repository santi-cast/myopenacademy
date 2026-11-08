-- Project stages are not lessons and never contribute to lesson counts.
-- Existing catalog, accounts and progress remain untouched; both continuations share one enrollment.
CREATE TABLE code_academy.project_stages (
    id               text    PRIMARY KEY,
    course_id        text    NOT NULL REFERENCES code_academy.courses (id),
    title            text    NOT NULL,
    position         integer NOT NULL CHECK (position > 0),
    available        boolean NOT NULL,
    source_available boolean NOT NULL,
    CONSTRAINT project_stages_id_course_key UNIQUE (id, course_id),
    CONSTRAINT project_stages_course_position_key UNIQUE (course_id, position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT project_stages_availability_check CHECK (NOT available OR source_available)
);

ALTER TABLE code_academy.enrollments ADD COLUMN last_opened_stage_id text;
ALTER TABLE code_academy.enrollments ADD CONSTRAINT enrollments_last_opened_stage_fkey
    FOREIGN KEY (last_opened_stage_id, course_id) REFERENCES code_academy.project_stages (id, course_id);

CREATE TABLE code_academy.stage_states (
    enrollment_id bigint      NOT NULL,
    course_id     text        NOT NULL,
    stage_id      text        NOT NULL,
    state         text        NOT NULL,
    updated_at    timestamptz NOT NULL,
    CONSTRAINT stage_states_enrollment_stage_key UNIQUE (enrollment_id, stage_id),
    CONSTRAINT stage_states_enrollment_fkey FOREIGN KEY (enrollment_id, course_id)
        REFERENCES code_academy.enrollments (id, course_id),
    CONSTRAINT stage_states_stage_fkey FOREIGN KEY (stage_id, course_id)
        REFERENCES code_academy.project_stages (id, course_id),
    CONSTRAINT stage_states_state_check CHECK (state IN ('not_completed', 'completed'))
);
