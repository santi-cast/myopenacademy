-- H9: a password set by an administrator is temporary. The user must change it on signing in, so the administrator
-- never knows the final one. Every existing account keeps the password it has.
ALTER TABLE code_academy.users ADD COLUMN must_change_password boolean NOT NULL DEFAULT false;
