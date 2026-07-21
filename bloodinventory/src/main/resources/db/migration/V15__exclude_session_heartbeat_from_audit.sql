-- Session keep-alive updates are operational heartbeat data, not user data changes.
-- Keep staff_login_session for session control, but do not audit every last_seen_at touch.

CREATE OR REPLACE FUNCTION public.fn_install_audit_trigger(p_table_name TEXT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_trigger_name TEXT := LEFT('trg_audit_' || p_table_name, 63);
BEGIN
    IF p_table_name IN (
        'audit_trail',
        'flyway_schema_history',
        'staff_login_session',
        'authentication_event'
    ) THEN
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
    v_trigger RECORD;
BEGIN
    IF to_regclass('public.staff_login_session') IS NULL THEN
        RETURN;
    END IF;

    FOR v_trigger IN
        SELECT trigger_info.tgname
        FROM pg_trigger trigger_info
        JOIN pg_class table_info ON table_info.oid = trigger_info.tgrelid
        JOIN pg_namespace namespace_info ON namespace_info.oid = table_info.relnamespace
        JOIN pg_proc function_info ON function_info.oid = trigger_info.tgfoid
        WHERE namespace_info.nspname = 'public'
          AND table_info.relname = 'staff_login_session'
          AND function_info.proname = 'fn_log_audit'
          AND trigger_info.tgisinternal = FALSE
    LOOP
        EXECUTE FORMAT('DROP TRIGGER IF EXISTS %I ON public.staff_login_session', v_trigger.tgname);
    END LOOP;
END;
$$;
