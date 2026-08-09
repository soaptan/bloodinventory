-- Persist only hashed login-attempt keys so abuse controls survive application restarts
-- without storing submitted usernames or source addresses in this table.

CREATE TABLE IF NOT EXISTS public.authentication_throttle (
    attempt_key VARCHAR(64) PRIMARY KEY,
    failure_count INTEGER NOT NULL DEFAULT 0,
    window_started_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_failed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    blocked_until TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT chk_authentication_throttle_failure_count CHECK (failure_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_authentication_throttle_blocked_until
    ON public.authentication_throttle (blocked_until)
    WHERE blocked_until IS NOT NULL;

ALTER TABLE public.staff_password_reset_token
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE public.staff_password_reset_token
    DROP CONSTRAINT IF EXISTS chk_staff_password_reset_attempt_count;

ALTER TABLE public.staff_password_reset_token
    ADD CONSTRAINT chk_staff_password_reset_attempt_count
    CHECK (attempt_count BETWEEN 0 AND 5);
