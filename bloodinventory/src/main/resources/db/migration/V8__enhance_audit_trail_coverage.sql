-- Broaden audit coverage to every application table and preserve the raw DML operation.

DROP TRIGGER IF EXISTS trg_audit_trail_immutable ON public.audit_trail;

ALTER TABLE public.audit_trail
    ADD COLUMN IF NOT EXISTS operation_type VARCHAR(20);

UPDATE public.audit_trail
SET operation_type = CASE
        WHEN old_value IS NULL AND new_value IS NOT NULL THEN 'INSERT'
        WHEN old_value IS NOT NULL AND new_value IS NULL THEN 'DELETE'
        ELSE 'UPDATE'
    END
WHERE operation_type IS NULL;

ALTER TABLE public.audit_trail
    ALTER COLUMN operation_type SET DEFAULT 'ACTION';

ALTER TABLE public.audit_trail
    ALTER COLUMN operation_type SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_audit_trail_operation_type ON public.audit_trail (operation_type);
CREATE INDEX IF NOT EXISTS idx_audit_trail_table_operation ON public.audit_trail (table_name, operation_type);

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
        p_row ->> 'access_id'
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.fn_audit_resolve_action(p_operation TEXT, p_table_name TEXT, p_old JSONB, p_new JSONB)
RETURNS VARCHAR
LANGUAGE plpgsql
AS $$
DECLARE
    v_old_status TEXT := UPPER(COALESCE(p_old ->> 'status', ''));
    v_new_status TEXT := UPPER(COALESCE(p_new ->> 'status', ''));
    v_row JSONB := COALESCE(p_new, p_old);
    v_action TEXT;
BEGIN
    IF p_table_name IN ('system_activity_notification', 'system_notification') THEN
        RETURN UPPER(COALESCE(NULLIF(v_row ->> 'action_type', ''), p_operation));
    END IF;

    IF p_table_name = 'system_backup_history' THEN
        v_action := UPPER(COALESCE(NULLIF(v_row ->> 'trigger_type', ''), NULLIF(v_row ->> 'status', ''), p_operation));
        RETURN CASE
            WHEN v_action IN ('RECOVERY', 'RESTORE', 'RECOVERY_POINT') THEN 'RECOVERY'
            WHEN v_action IN ('AUTO', 'MANUAL') THEN 'BACKUP'
            ELSE v_action
        END;
    END IF;

    IF p_operation = 'INSERT' THEN
        IF p_table_name = 'transfusion_record' THEN
            RETURN 'TRANSFUSED';
        END IF;

        RETURN 'INSERT';
    END IF;

    IF p_operation = 'DELETE' THEN
        RETURN 'DELETE';
    END IF;

    IF p_table_name = 'blood_component' AND v_old_status IS DISTINCT FROM v_new_status THEN
        RETURN CASE v_new_status
            WHEN 'USED' THEN 'TRANSFUSED'
            WHEN 'DISCARDED' THEN 'DISCARDED'
            WHEN 'RESERVED' THEN 'RESERVED'
            WHEN 'AVAILABLE' THEN 'RELEASED'
            WHEN 'QUARANTINED' THEN 'QUARANTINED'
            WHEN 'EXPIRED' THEN 'EXPIRED'
            ELSE 'UPDATE'
        END;
    END IF;

    IF p_table_name = 'blood_component'
       AND COALESCE(p_old ->> 'location_id', '') IS DISTINCT FROM COALESCE(p_new ->> 'location_id', '') THEN
        RETURN 'LOCATION_UPDATED';
    END IF;

    IF p_table_name IN ('storage_location', 'deferral_reason')
       AND COALESCE(p_old ->> 'is_active', '') IS DISTINCT FROM COALESCE(p_new ->> 'is_active', '') THEN
        IF COALESCE(p_new ->> 'is_active', 'true') = 'false' THEN
            RETURN 'ARCHIVED';
        END IF;

        RETURN 'RESTORED';
    END IF;

    IF p_table_name = 'donor'
       AND COALESCE(p_old ->> 'deferral_expiry_date', '') IS DISTINCT FROM COALESCE(p_new ->> 'deferral_expiry_date', '') THEN
        RETURN 'DEFERRAL_UPDATED';
    END IF;

    RETURN 'UPDATE';
END;
$$;

CREATE OR REPLACE FUNCTION public.fn_log_audit()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_raw_old JSONB := CASE WHEN TG_OP IN ('UPDATE', 'DELETE') THEN to_jsonb(OLD) ELSE NULL END;
    v_raw_new JSONB := CASE WHEN TG_OP IN ('INSERT', 'UPDATE') THEN to_jsonb(NEW) ELSE NULL END;
    v_row JSONB := COALESCE(v_raw_new, v_raw_old);
    v_user_id BIGINT;
    v_username TEXT;
    v_role TEXT;
    v_component_id BIGINT;
    v_donation_id BIGINT;
    v_device_id TEXT;
    v_source_ip TEXT;
BEGIN
    v_user_id := public.fn_audit_bigint(public.fn_audit_setting('bloodinventory.current_user_id'));
    v_username := COALESCE(
        public.fn_audit_setting('bloodinventory.current_username'),
        NULLIF(v_row ->> 'actor_username', ''),
        NULLIF(v_row ->> 'triggered_by', ''),
        NULLIF(v_row ->> 'username', ''),
        NULLIF(v_row ->> 'created_by', ''),
        NULLIF(v_row ->> 'last_modified_by', '')
    );
    v_role := public.fn_audit_setting('bloodinventory.current_user_role');
    v_device_id := COALESCE(
        public.fn_audit_setting('bloodinventory.current_device_id'),
        NULLIF(v_row ->> 'device_id', ''),
        NULLIF(v_row ->> 'user_agent', '')
    );
    v_source_ip := COALESCE(
        public.fn_audit_setting('bloodinventory.current_source_ip'),
        NULLIF(v_row ->> 'source_ip', '')
    );

    IF v_user_id IS NULL AND v_username IS NOT NULL THEN
        SELECT s.staff_id, s.staff_type
        INTO v_user_id, v_role
        FROM public.staff s
        WHERE LOWER(s.username) = LOWER(v_username)
        LIMIT 1;
    END IF;

    IF v_user_id IS NULL THEN
        v_user_id := public.fn_audit_bigint(v_row ->> 'staff_id');
    END IF;

    IF v_username IS NULL AND v_user_id IS NOT NULL THEN
        SELECT s.username
        INTO v_username
        FROM public.staff s
        WHERE s.staff_id = v_user_id;
    END IF;

    IF v_role IS NULL AND v_user_id IS NOT NULL THEN
        SELECT s.staff_type
        INTO v_role
        FROM public.staff s
        WHERE s.staff_id = v_user_id;
    END IF;

    v_component_id := public.fn_audit_bigint(v_row ->> 'component_id');
    v_donation_id := public.fn_audit_bigint(v_row ->> 'donation_id');

    IF v_donation_id IS NULL AND v_component_id IS NOT NULL THEN
        SELECT bc.donation_id
        INTO v_donation_id
        FROM public.blood_component bc
        WHERE bc.component_id = v_component_id;
    END IF;

    INSERT INTO public.audit_trail (
        user_id,
        username,
        role,
        component_id,
        donation_id,
        operation_type,
        action_type,
        table_name,
        row_pk,
        old_value,
        new_value,
        device_id,
        source_ip,
        location
    )
    VALUES (
        v_user_id,
        v_username,
        v_role,
        v_component_id,
        v_donation_id,
        TG_OP,
        public.fn_audit_resolve_action(TG_OP, TG_TABLE_NAME, v_raw_old, v_raw_new),
        TG_TABLE_NAME,
        public.fn_audit_row_pk(TG_TABLE_NAME, v_row),
        public.fn_audit_scrub_row(TG_TABLE_NAME, v_raw_old),
        public.fn_audit_scrub_row(TG_TABLE_NAME, v_raw_new),
        v_device_id,
        v_source_ip,
        public.fn_audit_row_location(TG_TABLE_NAME, v_row)
    );

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.fn_install_audit_trigger(p_table_name TEXT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_trigger_name TEXT := LEFT('trg_audit_' || p_table_name, 63);
BEGIN
    IF p_table_name IN ('audit_trail', 'flyway_schema_history') THEN
        RETURN;
    END IF;

    IF to_regclass(FORMAT('public.%I', p_table_name)) IS NULL THEN
        RETURN;
    END IF;

    EXECUTE FORMAT('DROP TRIGGER IF EXISTS %I ON public.%I', v_trigger_name, p_table_name);
    EXECUTE FORMAT(
        'CREATE TRIGGER %I AFTER INSERT OR UPDATE OR DELETE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.fn_log_audit()',
        v_trigger_name,
        p_table_name
    );
END;
$$;

DO $$
DECLARE
    v_table RECORD;
BEGIN
    FOR v_table IN
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_type = 'BASE TABLE'
          AND table_name NOT IN ('audit_trail', 'flyway_schema_history')
        ORDER BY table_name
    LOOP
        PERFORM public.fn_install_audit_trigger(v_table.table_name);
    END LOOP;
END;
$$;

CREATE TRIGGER trg_audit_trail_immutable
BEFORE UPDATE OR DELETE ON public.audit_trail
FOR EACH ROW
EXECUTE FUNCTION public.fn_prevent_audit_trail_changes();

REVOKE UPDATE, DELETE ON public.audit_trail FROM PUBLIC;
