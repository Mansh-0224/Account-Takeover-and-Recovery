-- ============================================================================
-- ATO Containment Service: DESIGN DRAFT of the database schema (Phase 0)
--
-- NOT executed by the application yet. It documents the intended design and
-- will become JPA entities and/or migration scripts (e.g. Flyway) in Phase 2.
-- You can try it manually:  psql -U ato_user -d ato_db -f database/schema_draft.sql
-- Requires PostgreSQL 13+ (uses gen_random_uuid()).
-- ============================================================================

-- ---------- Tenants ----------
CREATE TABLE tenants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120) NOT NULL,
    slug        VARCHAR(60)  NOT NULL UNIQUE,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tenant_security_settings (
    tenant_id                    UUID PRIMARY KEY REFERENCES tenants (id),
    suspicious_threshold         INT NOT NULL DEFAULT 30 CHECK (suspicious_threshold BETWEEN 1 AND 100),
    high_risk_threshold          INT NOT NULL DEFAULT 70 CHECK (high_risk_threshold BETWEEN 1 AND 100),
    max_failed_logins            INT NOT NULL DEFAULT 5,
    failed_login_window_minutes  INT NOT NULL DEFAULT 10,
    recovery_token_ttl_minutes   INT NOT NULL DEFAULT 15,
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (suspicious_threshold < high_risk_threshold)
);

-- ---------- Users ----------
CREATE TABLE users (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES tenants (id),
    email                VARCHAR(255) NOT NULL,          -- store lower-case
    password_hash        VARCHAR(100) NOT NULL,          -- BCrypt
    full_name            VARCHAR(120),
    role                 VARCHAR(20) NOT NULL DEFAULT 'USER'
                         CHECK (role IN ('USER', 'TENANT_ADMIN', 'SECURITY_ADMIN')),
    status               VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
                         CHECK (status IN ('ACTIVE', 'CONTAINED', 'RECOVERY_IN_PROGRESS', 'DISABLED')),
    failed_login_count   INT NOT NULL DEFAULT 0,
    last_login_at        TIMESTAMPTZ,
    password_changed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT uq_users_tenant_id_id UNIQUE (tenant_id, id)   -- target for composite foreign keys
);

CREATE TABLE known_devices (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL,
    user_id       UUID NOT NULL,
    device_hash   VARCHAR(64) NOT NULL,
    user_agent    VARCHAR(300),
    trusted       BOOLEAN NOT NULL DEFAULT FALSE,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_known_devices_user FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id),
    CONSTRAINT uq_known_devices UNIQUE (user_id, device_hash)
);

-- ---------- Sessions and tokens ----------
CREATE TABLE sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    user_id         UUID NOT NULL,
    device_hash     VARCHAR(64),
    user_agent      VARCHAR(300),
    ip_address      VARCHAR(45),
    country         VARCHAR(2),
    status          VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    revoked_reason  VARCHAR(30),                          -- LOGOUT, CONTAINMENT, TOKEN_REPLAY, ...
    CONSTRAINT fk_sessions_user FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id)
);

CREATE TABLE refresh_tokens (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants (id),
    session_id       UUID NOT NULL REFERENCES sessions (id),
    token_hash       VARCHAR(64) NOT NULL UNIQUE,         -- SHA-256 of the token, never the token itself
    parent_token_id  UUID REFERENCES refresh_tokens (id), -- previous token in the rotation chain
    used_at          TIMESTAMPTZ,                         -- set when rotated; reuse after this = replay
    expires_at       TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- Login history and risk ----------
CREATE TABLE login_events (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants (id),
    user_id          UUID REFERENCES users (id),          -- NULL when the email does not exist
    email_attempted  VARCHAR(255) NOT NULL,
    success          BOOLEAN NOT NULL,
    failure_reason   VARCHAR(50),
    ip_address       VARCHAR(45),
    country          VARCHAR(2),
    device_hash      VARCHAR(64),
    user_agent       VARCHAR(300),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE risk_assessments (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL,
    user_id        UUID NOT NULL,
    login_event_id UUID REFERENCES login_events (id),
    session_id     UUID REFERENCES sessions (id),
    trigger_type   VARCHAR(20) NOT NULL
                   CHECK (trigger_type IN ('LOGIN', 'TOKEN_REFRESH', 'API_REQUEST')),
    score          INT NOT NULL CHECK (score BETWEEN 0 AND 100),
    risk_level     VARCHAR(12) NOT NULL
                   CHECK (risk_level IN ('NORMAL', 'SUSPICIOUS', 'HIGH_RISK')),
    signals        JSONB NOT NULL DEFAULT '[]'::jsonb,    -- e.g. [{"name":"NEW_DEVICE","points":20}]
    decision       VARCHAR(20) NOT NULL
                   CHECK (decision IN ('ALLOW', 'STEP_UP', 'DENY_AND_CONTAIN')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_risk_assessments_user FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id)
);

CREATE TABLE step_up_challenges (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    user_id             UUID NOT NULL,
    risk_assessment_id  UUID NOT NULL REFERENCES risk_assessments (id),
    login_event_id      UUID NOT NULL REFERENCES login_events (id),
    otp_hash            VARCHAR(64) NOT NULL,
    attempts            INT NOT NULL DEFAULT 0,
    status              VARCHAR(10) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'PASSED', 'FAILED', 'EXPIRED')),
    expires_at          TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_step_up_user FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id)
);

-- ---------- Incidents, containment, recovery ----------
CREATE TABLE incidents (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    user_id          UUID NOT NULL,
    incident_type    VARCHAR(30) NOT NULL
                     CHECK (incident_type IN ('HIGH_RISK_LOGIN', 'STEP_UP_FAILED', 'TOKEN_REPLAY', 'MANUAL')),
    severity         VARCHAR(10) NOT NULL
                     CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status           VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                     CHECK (status IN ('OPEN', 'CONTAINED', 'RECOVERING', 'RESOLVED', 'FALSE_POSITIVE')),
    risk_score       INT,
    summary          VARCHAR(500),
    opened_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    contained_at     TIMESTAMPTZ,
    resolved_at      TIMESTAMPTZ,
    resolved_by      UUID REFERENCES users (id),
    resolution_note  VARCHAR(500),
    CONSTRAINT fk_incidents_user FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id),
    CONSTRAINT uq_incidents_tenant_id_id UNIQUE (tenant_id, id)
);

CREATE TABLE containment_actions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL,
    incident_id        UUID NOT NULL,
    user_id            UUID NOT NULL,
    action_type        VARCHAR(30) NOT NULL
                       CHECK (action_type IN ('LOCK_ACCOUNT', 'REVOKE_SESSIONS', 'FORCE_PASSWORD_RESET')),
    performed_by_type  VARCHAR(10) NOT NULL CHECK (performed_by_type IN ('SYSTEM', 'USER')),
    performed_by       UUID REFERENCES users (id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    reverted_at        TIMESTAMPTZ,
    CONSTRAINT fk_containment_incident FOREIGN KEY (tenant_id, incident_id) REFERENCES incidents (tenant_id, id),
    CONSTRAINT fk_containment_user FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id)
);

CREATE TABLE recovery_requests (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    user_id          UUID NOT NULL,
    incident_id      UUID,
    method           VARCHAR(20) NOT NULL CHECK (method IN ('EMAIL_OTP', 'ADMIN_APPROVED')),
    token_hash       VARCHAR(64) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING', 'VERIFIED', 'COMPLETED', 'EXPIRED', 'FAILED', 'CANCELLED')),
    failed_attempts  INT NOT NULL DEFAULT 0,
    approved_by      UUID REFERENCES users (id),
    expires_at       TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    verified_at      TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    CONSTRAINT fk_recovery_user FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id),
    CONSTRAINT fk_recovery_incident FOREIGN KEY (tenant_id, incident_id) REFERENCES incidents (tenant_id, id)
);

-- ---------- Audit and notifications ----------
CREATE TABLE audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    UUID REFERENCES tenants (id),            -- NULL only for events before a tenant is known
    actor_type   VARCHAR(10) NOT NULL CHECK (actor_type IN ('USER', 'SYSTEM', 'ANONYMOUS')),
    actor_id     UUID,
    action       VARCHAR(50) NOT NULL,                    -- e.g. ACCOUNT_CONTAINED
    target_type  VARCHAR(30),
    target_id    UUID,
    incident_id  UUID REFERENCES incidents (id),
    ip_address   VARCHAR(45),
    details      JSONB NOT NULL DEFAULT '{}'::jsonb,      -- never put passwords, tokens or OTPs here
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notifications (   -- simulated email outbox (dev only; bodies may contain OTPs)
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    user_id     UUID NOT NULL,
    channel     VARCHAR(10) NOT NULL DEFAULT 'EMAIL' CHECK (channel IN ('EMAIL')),
    purpose     VARCHAR(30) NOT NULL,                     -- STEP_UP, RECOVERY, CONTAINMENT_NOTICE, ...
    subject     VARCHAR(200) NOT NULL,
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_notifications_user FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id)
);

-- ---------- Indexes ----------
CREATE INDEX idx_login_events_user_time      ON login_events (user_id, created_at DESC);
CREATE INDEX idx_login_events_email_time     ON login_events (tenant_id, email_attempted, created_at DESC);
CREATE INDEX idx_sessions_user_status        ON sessions (user_id, status);
CREATE INDEX idx_refresh_tokens_session      ON refresh_tokens (session_id);
CREATE INDEX idx_risk_assessments_user_time  ON risk_assessments (user_id, created_at DESC);
CREATE INDEX idx_incidents_tenant_status     ON incidents (tenant_id, status);
CREATE INDEX idx_incidents_user              ON incidents (user_id);
CREATE INDEX idx_recovery_user_status        ON recovery_requests (user_id, status);
CREATE INDEX idx_audit_logs_tenant_time      ON audit_logs (tenant_id, created_at DESC);
CREATE INDEX idx_audit_logs_incident         ON audit_logs (incident_id);
