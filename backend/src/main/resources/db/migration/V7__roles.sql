-- H9: roles and deactivation. Every existing account is a learner and active; administrators are only ever created or
-- promoted from the console (--create-admin), never with a default user or password.
ALTER TABLE code_academy.users
    ADD COLUMN role   varchar(16) NOT NULL DEFAULT 'user',
    ADD COLUMN active boolean     NOT NULL DEFAULT true,
    ADD CONSTRAINT users_role_known CHECK (role IN ('user', 'admin'));
