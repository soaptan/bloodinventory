-- Store one-use password reset links without exposing reset tokens on staff rows.

CREATE TABLE IF NOT EXISTS public.staff_password_reset_token (
    token_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    staff_id BIGINT NOT NULL REFERENCES public.staff(staff_id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    requested_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    used_at TIMESTAMP WITHOUT TIME ZONE,
    request_ip VARCHAR(80)
);

CREATE INDEX IF NOT EXISTS idx_staff_password_reset_token_staff
    ON public.staff_password_reset_token (staff_id, used_at, expires_at);

CREATE INDEX IF NOT EXISTS idx_staff_password_reset_token_expiry
    ON public.staff_password_reset_token (expires_at)
    WHERE used_at IS NULL;

CREATE OR REPLACE FUNCTION public.fn_audit_scrub_row(p_table_name TEXT, p_row JSONB)
RETURNS JSONB
LANGUAGE plpgsql
AS $$
DECLARE
    v_row JSONB := p_row;
BEGIN
    IF v_row IS NULL THEN
        RETURN NULL;
    END IF;

    v_row := v_row - 'password';
    v_row := v_row - 'profile_photo';
    v_row := v_row - 'reset_token';
    v_row := v_row - 'remember_token';
    v_row := v_row - 'token_hash';

    RETURN v_row;
END;
$$;

CREATE OR REPLACE FUNCTION public.fn_audit_row_pk(p_table_name TEXT, p_row JSONB)
RETURNS TEXT
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_row IS NULL THEN
        RETURN NULL;
    END IF;

    IF p_table_name = 'donor_deferral_history' THEN
        RETURN CONCAT_WS(':', p_row ->> 'donor_id', p_row ->> 'staff_id', p_row ->> 'reason_id');
    END IF;

    RETURN COALESCE(
        p_row ->> 'component_id',
        p_row ->> 'donation_id',
        p_row ->> 'donor_id',
        p_row ->> 'test_id',
        p_row ->> 'reason_id',
        p_row ->> 'location_id',
        p_row ->> 'staff_id',
        p_row ->> 'patient_id',
        p_row ->> 'notification_id',
        p_row ->> 'backup_id',
        p_row ->> 'config_key',
        p_row ->> 'policy_key',
        p_row ->> 'setting_key',
        p_row ->> 'session_id',
        p_row ->> 'access_id',
        p_row ->> 'token_id'
    );
END;
$$;

SELECT public.fn_install_audit_trigger('staff_password_reset_token');
