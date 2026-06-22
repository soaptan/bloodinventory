-- Preserve role/context fallbacks when an audited username is not present in staff.

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
    v_resolved_user_id BIGINT;
    v_resolved_role TEXT;
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
        INTO v_resolved_user_id, v_resolved_role
        FROM public.staff s
        WHERE LOWER(s.username) = LOWER(v_username)
        LIMIT 1;

        v_user_id := COALESCE(v_user_id, v_resolved_user_id);
        v_role := COALESCE(v_role, v_resolved_role);
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
