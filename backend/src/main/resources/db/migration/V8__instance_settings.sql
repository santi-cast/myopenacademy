-- H9: the settings of this academy, one row. Registration is open until the administrator closes it; without a name,
-- the academy shows its own.
CREATE TABLE code_academy.instance_settings (
    id                smallint     PRIMARY KEY,
    registration_open boolean      NOT NULL,
    instance_name     varchar(80),
    updated_at        timestamptz  NOT NULL,
    CONSTRAINT instance_settings_single_row CHECK (id = 1),
    CONSTRAINT instance_settings_name_not_blank CHECK (instance_name IS NULL OR btrim(instance_name) <> '')
);
INSERT INTO code_academy.instance_settings (id, registration_open, instance_name, updated_at) VALUES (1, true, NULL, now());
