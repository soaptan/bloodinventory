-- Database-level audit trail for donor, lab, inventory, transfusion, storage, and staff workflows.

CREATE TABLE IF NOT EXISTS public.audit_trail (
    audit_id BIGSERIAL PRIMARY KEY,
    event_timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT,
    username VARCHAR(80),
    role VARCHAR(50),
    component_id BIGINT,
    donation_id BIGINT,
    action_type VARCHAR(50) NOT NULL,
    table_name VARCHAR(80) NOT NULL,
    row_pk VARCHAR(140),
    old_value JSONB,
    new_value JSONB,
    device_id VARCHAR(120),
    source_ip VARCHAR(80),
    location VARCHAR(180)
);

CREATE INDEX IF NOT EXISTS idx_audit_trail_event_timestamp ON public.audit_trail (event_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_trail_component_id ON public.audit_trail (component_id);
CREATE INDEX IF NOT EXISTS idx_audit_trail_donation_id ON public.audit_trail (donation_id);
CREATE INDEX IF NOT EXISTS idx_audit_trail_user_id ON public.audit_trail (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_trail_table_action ON public.audit_trail (table_name, action_type);

CREATE OR REPLACE FUNCTION public.fn_audit_setting(p_key TEXT)
RETURNS TEXT
LANGUAGE sql
AS $$
    SELECT NULLIF(current_setting(p_key, TRUE), '');
$$;

CREATE OR REPLACE FUNCTION public.fn_audit_bigint(p_value TEXT)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_value IS NULL OR p_value !~ '^\d+$' THEN
        RETURN NULL;
    END IF;

    RETURN p_value::BIGINT;
END;
$$;

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

    IF p_table_name = 'staff' THEN
        v_row := v_row - 'profile_photo';
    END IF;

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
        p_row ->> 'notification_id'
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.fn_audit_row_location(p_table_name TEXT, p_row JSONB)
RETURNS TEXT
LANGUAGE plpgsql
AS $$
DECLARE
    v_location_id BIGINT;
    v_location TEXT;
BEGIN
    IF p_row IS NULL THEN
        RETURN public.fn_audit_setting('bloodinventory.current_location');
    END IF;

    IF p_table_name = 'storage_location' THEN
        RETURN p_row ->> 'description';
    END IF;

    v_location_id := public.fn_audit_bigint(p_row ->> 'location_id');
    IF v_location_id IS NOT NULL THEN
        SELECT sl.description
        INTO v_location
        FROM public.storage_location sl
        WHERE sl.location_id = v_location_id;
    END IF;

    RETURN COALESCE(v_location, public.fn_audit_setting('bloodinventory.current_location'));
END;
$$;

CREATE OR REPLACE FUNCTION public.fn_audit_resolve_action(p_operation TEXT, p_table_name TEXT, p_old JSONB, p_new JSONB)
RETURNS VARCHAR
LANGUAGE plpgsql
AS $$
DECLARE
    v_old_status TEXT := UPPER(COALESCE(p_old ->> 'status', ''));
    v_new_status TEXT := UPPER(COALESCE(p_new ->> 'status', ''));
BEGIN
    IF p_operation = 'INSERT' THEN
        IF p_table_name = 'transfusion_record' THEN
            RETURN 'TRANSFUSED';
        END IF;

        RETURN 'CREATED';
    END IF;

    IF p_operation = 'DELETE' THEN
        RETURN 'DELETED';
    END IF;

    IF p_table_name = 'blood_component' AND v_old_status IS DISTINCT FROM v_new_status THEN
        RETURN CASE v_new_status
            WHEN 'USED' THEN 'TRANSFUSED'
            WHEN 'DISCARDED' THEN 'DISCARDED'
            WHEN 'RESERVED' THEN 'RESERVED'
            WHEN 'AVAILABLE' THEN 'RELEASED'
            WHEN 'QUARANTINED' THEN 'QUARANTINED'
            WHEN 'EXPIRED' THEN 'EXPIRED'
            ELSE 'MODIFIED'
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

    RETURN 'MODIFIED';
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
BEGIN
    v_user_id := public.fn_audit_bigint(public.fn_audit_setting('bloodinventory.current_user_id'));
    v_username := public.fn_audit_setting('bloodinventory.current_username');
    v_role := public.fn_audit_setting('bloodinventory.current_user_role');

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
        public.fn_audit_resolve_action(TG_OP, TG_TABLE_NAME, v_raw_old, v_raw_new),
        TG_TABLE_NAME,
        public.fn_audit_row_pk(TG_TABLE_NAME, v_row),
        public.fn_audit_scrub_row(TG_TABLE_NAME, v_raw_old),
        public.fn_audit_scrub_row(TG_TABLE_NAME, v_raw_new),
        public.fn_audit_setting('bloodinventory.current_device_id'),
        public.fn_audit_setting('bloodinventory.current_source_ip'),
        public.fn_audit_row_location(TG_TABLE_NAME, v_row)
    );

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.fn_prevent_audit_trail_changes()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_trail is append-only and cannot be updated or deleted';
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_trail_immutable ON public.audit_trail;
CREATE TRIGGER trg_audit_trail_immutable
BEFORE UPDATE OR DELETE ON public.audit_trail
FOR EACH ROW
EXECUTE FUNCTION public.fn_prevent_audit_trail_changes();

DROP TRIGGER IF EXISTS trg_audit_staff ON public.staff;
CREATE TRIGGER trg_audit_staff
AFTER INSERT OR UPDATE OR DELETE ON public.staff
FOR EACH ROW
EXECUTE FUNCTION public.fn_log_audit();

DROP TRIGGER IF EXISTS trg_audit_blood_administrator ON public.blood_administrator;
CREATE TRIGGER trg_audit_blood_administrator
AFTER INSERT OR UPDATE OR DELETE ON public.blood_administrator
FOR EACH ROW
EXECUTE FUNCTION public.fn_log_audit();

DROP TRIGGER IF EXISTS trg_audit_medical_staff ON public.medical_staff;
CREATE TRIGGER trg_audit_medical_staff
AFTER INSERT OR UPDATE OR DELETE ON public.medical_staff
FOR EACH ROW
EXECUTE FUNCTION public.fn_log_audit();

DROP TRIGGER IF EXISTS trg_audit_lab_technician ON public.lab_technician;
CREATE TRIGGER trg_audit_lab_technician
AFTER INSERT OR UPDATE OR DELETE ON public.lab_technician
FOR EACH ROW
EXECUTE FUNCTION public.fn_log_audit();

DROP TRIGGER IF EXISTS trg_audit_donor ON public.donor;
CREATE TRIGGER trg_audit_donor
AFTER INSERT OR UPDATE OR DELETE ON public.donor
FOR EACH ROW
EXECUTE FUNCTION public.fn_log_audit();

DROP TRIGGER IF EXISTS trg_audit_donor_deferral_history ON public.donor_deferral_history;
CREATE TRIGGER trg_audit_donor_deferral_history
AFTER INSERT OR UPDATE OR DELETE ON public.donor_deferral_history
FOR EACH ROW
EXECUTE FUNCTION public.fn_log_audit();

DROP TRIGGER IF EXISTS trg_audit_donation ON public.donation;
CREATE TRIGGER trg_audit_donation
AFTER INSERT OR UPDATE OR DELETE ON public.donation
FOR EACH ROW
EXECUTE FUNCTION public.fn_log_audit();

DROP TRIGGER IF EXISTS trg_audit_blood_component ON public.blood_component;
CREATE TRIGGER trg_audit_blood_component
AFTER INSERT OR UPDATE OR DELETE ON public.blood_component
FOR EACH ROW
EXECUTE FUNCTION public.fn_log_audit();

DROP TRIGGER IF EXISTS trg_audit_lab_test ON public.lab_test;
CREATE TRIGGER trg_audit_lab_test
AFTER INSERT OR UPDATE OR DELETE ON public.lab_test
FOR EACH ROW
EXECUTE FUNCTION public.fn_log_audit();

DROP TRIGGER IF EXISTS trg_audit_transfusion_record ON public.transfusion_record;
CREATE TRIGGER trg_audit_transfusion_record
AFTER INSERT OR UPDATE OR DELETE ON public.transfusion_record
FOR EACH ROW
EXECUTE FUNCTION public.fn_log_audit();

DROP TRIGGER IF EXISTS trg_audit_patient ON public.patient;
CREATE TRIGGER trg_audit_patient
AFTER INSERT OR UPDATE OR DELETE ON public.patient
FOR EACH ROW
EXECUTE FUNCTION public.fn_log_audit();

DROP TRIGGER IF EXISTS trg_audit_storage_location ON public.storage_location;
CREATE TRIGGER trg_audit_storage_location
AFTER INSERT OR UPDATE OR DELETE ON public.storage_location
FOR EACH ROW
EXECUTE FUNCTION public.fn_log_audit();

DROP TRIGGER IF EXISTS trg_audit_deferral_reason ON public.deferral_reason;
CREATE TRIGGER trg_audit_deferral_reason
AFTER INSERT OR UPDATE OR DELETE ON public.deferral_reason
FOR EACH ROW
EXECUTE FUNCTION public.fn_log_audit();

INSERT INTO public.staff_module_access (
    staff_type,
    module_key,
    module_name,
    url_pattern,
    is_enabled,
    sort_order,
    created_by,
    created_at,
    updated_at
)
VALUES (
    'BLOOD_ADMINISTRATOR',
    'audit_trail',
    'Audit Trail',
    '/admin/audit/**',
    TRUE,
    65,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (staff_type, module_key) DO UPDATE
SET module_name = EXCLUDED.module_name,
    url_pattern = EXCLUDED.url_pattern,
    is_enabled = EXCLUDED.is_enabled,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP,
    last_modified_by = 'SYSTEM';

REVOKE UPDATE, DELETE ON public.audit_trail FROM PUBLIC;
