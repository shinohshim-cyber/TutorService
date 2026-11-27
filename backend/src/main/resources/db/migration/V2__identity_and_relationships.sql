CREATE TABLE identity.users (
    id uuid PRIMARY KEY,
    normalized_email varchar(254) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    name varchar(80) NOT NULL,
    role varchar(16) NOT NULL CHECK (role IN ('STUDENT', 'TEACHER')),
    status varchar(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DELETED')),
    session_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE identity.teacher_signup_invitations (
    id uuid PRIMARY KEY,
    normalized_email varchar(254) NOT NULL,
    digest char(64) NOT NULL UNIQUE,
    verified_by varchar(120) NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE identity.relationship_invitations (
    id uuid PRIMARY KEY,
    sender_id uuid NOT NULL REFERENCES identity.users(id),
    recipient_id uuid NOT NULL REFERENCES identity.users(id),
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (sender_id <> recipient_id)
);
CREATE INDEX invitation_recipient ON identity.relationship_invitations(recipient_id, expires_at);
CREATE TABLE identity.teacher_student_relations (
    id uuid PRIMARY KEY,
    teacher_id uuid NOT NULL REFERENCES identity.users(id),
    student_id uuid NOT NULL REFERENCES identity.users(id),
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
    invitation_id uuid NOT NULL UNIQUE REFERENCES identity.relationship_invitations(id),
    accepted_at timestamptz NOT NULL DEFAULT now(),
    CHECK (teacher_id <> student_id)
);
CREATE UNIQUE INDEX active_teacher_student ON identity.teacher_student_relations(teacher_id, student_id) WHERE status='ACTIVE';
CREATE TABLE identity.account_deletions (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL UNIQUE REFERENCES identity.users(id),
    created_at timestamptz NOT NULL DEFAULT now()
);
