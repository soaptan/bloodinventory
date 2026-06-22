-- Add user-action audit records, process context, UTC-ready metadata, and hash-based tamper evidence.

DROP TRIGGER IF EXISTS trg_audit_trail_immutable ON public.audit_trail;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE public.audit_trail
    ADD COLUMN IF NOT EXISTS event_category VARCHAR(30) NOT NULL DEFAULT 'DATA_CHANGE',
    ADD COLUMN IF NOT EXISTS workflow_phase VARCHAR(80),
    ADD COLUMN IF NOT EXISTS request_path VARCHAR(255),
    ADD COLUMN IF NOT EXISTS http_method VARCHAR(12),
    ADD COLUMN IF NOT EXISTS session_id_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS process_context JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS previous_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS integrity_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_audit_trail_event_category ON public.audit_trail (event_category);
CREATE INDEX IF NOT EXISTS idx_audit_trail_workflow_phase ON public.audit_trail (workflow_phase);
CREATE INDEX IF NOT EXISTS idx_audit_trail_request_path ON public.audit_trail (request_path);
CREATE INDEX IF NOT EXISTS idx_audit_trail_integrity_hash ON public.audit_trail (integrity_hash);

CREATE OR REPLACE FUNCTION public.fn_audit_workflow_phase(p_table_name TEXT, p_action_type TEXT)
RETURNS TEXT
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_table_name IN ('authentication_event', 'staff_login_session') THEN
        RETURN 'Authentication';
    END IF;

    IF p_table_name IN ('system_backup_history', 'system_backup_config', 'backup_event') THEN
        RETURN 'Backup and Recovery';
    END IF;

    IF p_table_name IN ('system_security_policy', 'system_setting', 'settings_event') THEN
        RETURN 'System Settings';
    END IF;

    IF p_table_name IN ('staff', 'blood_administrator', 'medical_staff', 'lab_technician', 'staff_module_access') THEN
        RETURN 'Staff Administration';
    END IF;

    IF p_table_name IN ('donor', 'donation', 'donor_deferral_history', 'deferral_reason') THEN
        RETURN 'Donor Eligibility';
    END IF;

    IF p_table_name = 'lab_test' THEN
        RETURN 'Laboratory Screening';
    END IF;

    IF p_table_name = 'blood_component' THEN
        RETURN 'Inventory Control';
    END IF;

    IF p_table_name IN ('transfusion_record', 'patient') THEN
        RETURN 'Transfusion';
    END IF;

    IF p_table_name = 'storage_location' THEN
        RETURN 'Storage Configuration';
    END IF;

    IF p_action_type IN ('DOWNLOAD', 'SEARCH', 'VIEW') THEN
        RETURN 'Information Access';
    END IF;

    RETURN 'Application Activity';
END;
$$;

CREATE OR REPLACE FUNCTION public.fn_set_audit_integrity_hash()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_previous_hash TEXT;
BEGIN
    IF NEW.event_timestamp IS NULL THEN
        NEW.event_timestamp := CURRENT_TIMESTAMP;
    END IF;

    SELECT at.integrity_hash
    INTO v_previous_hash
    FROM public.audit_trail at
    WHERE at.audit_id < NEW.audit_id
      AND at.integrity_hash IS NOT NULL
    ORDER BY at.audit_id DESC
    LIMIT 1;

    NEW.previous_hash := COALESCE(NEW.previous_hash, v_previous_hash);
    NEW.integrity_hash := encode(digest(CONCAT_WS('|',
        COALESCE(NEW.previous_hash, ''),
        COALESCE(NEW.audit_id::TEXT, ''),
        COALESCE(TO_CHAR(NEW.event_timestamp AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US'), ''),
        COALESCE(NEW.user_id::TEXT, ''),
        COALESCE(NEW.username, ''),
        COALESCE(NEW.role, ''),
        COALESCE(NEW.event_category, ''),
        COALESCE(NEW.operation_type, ''),
        COALESCE(NEW.action_type, ''),
        COALESCE(NEW.table_name, ''),
        COALESCE(NEW.row_pk, ''),
        COALESCE(NEW.component_id::TEXT, ''),
        COALESCE(NEW.donation_id::TEXT, ''),
        COALESCE(NEW.old_value::TEXT, ''),
        COALESCE(NEW.new_value::TEXT, ''),
        COALESCE(NEW.device_id, ''),
        COALESCE(NEW.source_ip, ''),
        COALESCE(NEW.location, ''),
        COALESCE(NEW.workflow_phase, ''),
        COALESCE(NEW.request_path, ''),
        COALESCE(NEW.http_method, ''),
        COALESCE(NEW.session_id_hash, ''),
        COALESCE(NEW.process_context::TEXT, '')
    ), 'sha256'), 'hex');

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_trail_integrity_hash ON public.audit_trail;
CREATE TRIGGER trg_audit_trail_integrity_hash
BEFORE INSERT ON public.audit_trail
FOR EACH ROW
EXECUTE FUNCTION public.fn_set_audit_integrity_hash();

WITH base_hash AS (
    SELECT
        audit_id,
        encode(digest(CONCAT_WS('|',
            COALESCE(audit_id::TEXT, ''),
            COALESCE(TO_CHAR(event_timestamp AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US'), ''),
            COALESCE(user_id::TEXT, ''),
            COALESCE(username, ''),
            COALESCE(role, ''),
            COALESCE(event_category, ''),
            COALESCE(operation_type, ''),
            COALESCE(action_type, ''),
            COALESCE(table_name, ''),
            COALESCE(row_pk, ''),
            COALESCE(component_id::TEXT, ''),
            COALESCE(donation_id::TEXT, ''),
            COALESCE(old_value::TEXT, ''),
            COALESCE(new_value::TEXT, ''),
            COALESCE(device_id, ''),
            COALESCE(source_ip, ''),
            COALESCE(location, ''),
            COALESCE(workflow_phase, ''),
            COALESCE(request_path, ''),
            COALESCE(http_method, ''),
            COALESCE(session_id_hash, ''),
            COALESCE(process_context::TEXT, '')
        ), 'sha256'), 'hex') AS row_hash
    FROM public.audit_trail
),
chained_hash AS (
    SELECT
        audit_id,
        LAG(row_hash) OVER (ORDER BY audit_id ASC) AS previous_hash,
        encode(digest(CONCAT_WS('|',
            COALESCE(LAG(row_hash) OVER (ORDER BY audit_id ASC), ''),
            row_hash
        ), 'sha256'), 'hex') AS integrity_hash
    FROM base_hash
)
UPDATE public.audit_trail at
SET previous_hash = chained_hash.previous_hash,
    integrity_hash = chained_hash.integrity_hash
FROM chained_hash
WHERE at.audit_id = chained_hash.audit_id
  AND at.integrity_hash IS NULL;

CREATE OR REPLACE FUNCTION public.fn_record_audit_event(
    p_event_category TEXT,
    p_operation_type TEXT,
    p_action_type TEXT,
    p_table_name TEXT,
    p_row_pk TEXT,
    p_workflow_phase TEXT,
    p_request_path TEXT,
    p_http_method TEXT,
    p_process_context JSONB DEFAULT '{}'::jsonb
)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    v_audit_id BIGINT;
    v_user_id BIGINT;
    v_username TEXT;
    v_role TEXT;
    v_resolved_user_id BIGINT;
    v_resolved_role TEXT;
    v_table_name TEXT := LEFT(COALESCE(NULLIF(p_table_name, ''), 'application_event'), 80);
    v_action_type TEXT := LEFT(UPPER(COALESCE(NULLIF(p_action_type, ''), 'ACTION')), 50);
    v_context JSONB := COALESCE(p_process_context, '{}'::jsonb);
BEGIN
    v_user_id := public.fn_audit_bigint(public.fn_audit_setting('bloodinventory.current_user_id'));
    v_username := COALESCE(
        public.fn_audit_setting('bloodinventory.current_username'),
        NULLIF(v_context ->> 'username', ''),
        NULLIF(v_context ->> 'attempted_username', ''),
        'system'
    );
    v_role := COALESCE(public.fn_audit_setting('bloodinventory.current_user_role'), NULLIF(v_context ->> 'role', ''), 'SYSTEM');

    IF v_user_id IS NULL AND v_username IS NOT NULL AND LOWER(v_username) <> 'system' THEN
        SELECT s.staff_id, s.staff_type
        INTO v_resolved_user_id, v_resolved_role
        FROM public.staff s
        WHERE LOWER(s.username) = LOWER(v_username)
        LIMIT 1;

        v_user_id := COALESCE(v_user_id, v_resolved_user_id);
        v_role := COALESCE(NULLIF(v_role, 'SYSTEM'), v_resolved_role, v_role);
    END IF;

    INSERT INTO public.audit_trail (
        user_id,
        username,
        role,
        operation_type,
        action_type,
        table_name,
        row_pk,
        old_value,
        new_value,
        device_id,
        source_ip,
        location,
        event_category,
        workflow_phase,
        request_path,
        http_method,
        session_id_hash,
        process_context
    )
    VALUES (
        v_user_id,
        v_username,
        v_role,
        LEFT(UPPER(COALESCE(NULLIF(p_operation_type, ''), 'ACTION')), 20),
        v_action_type,
        v_table_name,
        LEFT(NULLIF(p_row_pk, ''), 140),
        NULL,
        jsonb_strip_nulls(jsonb_build_object(
            'event_category', LEFT(UPPER(COALESCE(NULLIF(p_event_category, ''), 'USER_ACTION')), 30),
            'operation', LEFT(UPPER(COALESCE(NULLIF(p_operation_type, ''), 'ACTION')), 20),
            'action', v_action_type,
            'object', v_table_name,
            'workflow_phase', COALESCE(NULLIF(p_workflow_phase, ''), public.fn_audit_workflow_phase(v_table_name, v_action_type)),
            'context', v_context
        )),
        public.fn_audit_setting('bloodinventory.current_device_id'),
        public.fn_audit_setting('bloodinventory.current_source_ip'),
        NULLIF(v_context ->> 'location', ''),
        LEFT(UPPER(COALESCE(NULLIF(p_event_category, ''), 'USER_ACTION')), 30),
        LEFT(COALESCE(NULLIF(p_workflow_phase, ''), public.fn_audit_workflow_phase(v_table_name, v_action_type)), 80),
        LEFT(COALESCE(NULLIF(p_request_path, ''), public.fn_audit_setting('bloodinventory.current_request_path')), 255),
        LEFT(UPPER(COALESCE(NULLIF(p_http_method, ''), public.fn_audit_setting('bloodinventory.current_http_method'))), 12),
        public.fn_audit_setting('bloodinventory.current_session_id_hash'),
        v_context
    )
    RETURNING audit_id INTO v_audit_id;

    RETURN v_audit_id;
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
    v_resolved_user_id BIGINT;
    v_resolved_role TEXT;
    v_action_type TEXT;
    v_row_pk TEXT;
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

    v_action_type := public.fn_audit_resolve_action(TG_OP, TG_TABLE_NAME, v_raw_old, v_raw_new);
    v_row_pk := public.fn_audit_row_pk(TG_TABLE_NAME, v_row);

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
        location,
        event_category,
        workflow_phase,
        request_path,
        http_method,
        session_id_hash,
        process_context
    )
    VALUES (
        v_user_id,
        v_username,
        v_role,
        v_component_id,
        v_donation_id,
        TG_OP,
        v_action_type,
        TG_TABLE_NAME,
        v_row_pk,
        public.fn_audit_scrub_row(TG_TABLE_NAME, v_raw_old),
        public.fn_audit_scrub_row(TG_TABLE_NAME, v_raw_new),
        v_device_id,
        v_source_ip,
        public.fn_audit_row_location(TG_TABLE_NAME, v_row),
        'DATA_CHANGE',
        public.fn_audit_workflow_phase(TG_TABLE_NAME, v_action_type),
        public.fn_audit_setting('bloodinventory.current_request_path'),
        public.fn_audit_setting('bloodinventory.current_http_method'),
        public.fn_audit_setting('bloodinventory.current_session_id_hash'),
        jsonb_strip_nulls(jsonb_build_object(
            'trigger', 'fn_log_audit',
            'operation', TG_OP,
            'table', TG_TABLE_NAME,
            'row_pk', v_row_pk,
            'request_path', public.fn_audit_setting('bloodinventory.current_request_path'),
            'http_method', public.fn_audit_setting('bloodinventory.current_http_method')
        ))
    );

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.fn_audit_schema_change()
RETURNS EVENT_TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_command RECORD;
BEGIN
    FOR v_command IN
        SELECT *
        FROM pg_event_trigger_ddl_commands()
    LOOP
        IF COALESCE(v_command.schema_name, 'public') = 'public' THEN
            PERFORM public.fn_record_audit_event(
                'SECURITY',
                'OVERRIDE',
                LEFT('DDL_' || REPLACE(UPPER(v_command.command_tag), ' ', '_'), 50),
                'schema_change',
                LEFT(v_command.object_identity, 140),
                'Database Administration',
                NULL,
                NULL,
                jsonb_strip_nulls(jsonb_build_object(
                    'command_tag', v_command.command_tag,
                    'object_type', v_command.object_type,
                    'object_identity', v_command.object_identity,
                    'schema_name', v_command.schema_name
                ))
            );
        END IF;
    END LOOP;
END;
$$;

CREATE TRIGGER trg_audit_trail_immutable
BEFORE UPDATE OR DELETE ON public.audit_trail
FOR EACH ROW
EXECUTE FUNCTION public.fn_prevent_audit_trail_changes();

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname = CURRENT_USER
          AND rolsuper = TRUE
    ) THEN
        EXECUTE 'DROP EVENT TRIGGER IF EXISTS trg_audit_schema_change';
        EXECUTE 'CREATE EVENT TRIGGER trg_audit_schema_change
            ON ddl_command_end
            WHEN TAG IN (''ALTER TABLE'', ''CREATE TRIGGER'', ''DROP TRIGGER'', ''CREATE FUNCTION'', ''ALTER FUNCTION'', ''DROP FUNCTION'', ''DROP TABLE'')
            EXECUTE FUNCTION public.fn_audit_schema_change()';
    ELSE
        PERFORM public.fn_record_audit_event(
            'SECURITY',
            'OVERRIDE',
            'DDL_MONITOR_UNAVAILABLE',
            'schema_change',
            CURRENT_USER,
            'Database Administration',
            NULL,
            NULL,
            jsonb_build_object(
                'reason', 'current database user is not superuser',
                'database_user', CURRENT_USER
            )
        );
    END IF;
END;
$$;

REVOKE UPDATE, DELETE ON public.audit_trail FROM PUBLIC;
