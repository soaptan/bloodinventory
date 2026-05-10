--
-- PostgreSQL database dump
--

-- Dumped from database version 18.3
-- Dumped by pg_dump version 18.3

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: fn_available_stock_by_type(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_available_stock_by_type() RETURNS TABLE(component_type character varying, total_available bigint)
    LANGUAGE sql
    AS $$
    SELECT
        bc.component_type,
        COUNT(*) AS total_available
    FROM blood_component bc
    WHERE UPPER(bc.status) = 'AVAILABLE'
    GROUP BY bc.component_type
    ORDER BY bc.component_type;
$$;


--
-- Name: fn_count_deferral_rules(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_count_deferral_rules() RETURNS bigint
    LANGUAGE sql
    AS $$
    SELECT COUNT(*) FROM deferral_reason;
$$;


--
-- Name: fn_count_storage_locations(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_count_storage_locations() RETURNS bigint
    LANGUAGE sql
    AS $$
    SELECT COUNT(*) FROM storage_location;
$$;


--
-- Name: fn_dashboard_summary_metrics(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_dashboard_summary_metrics() RETURNS TABLE(metric_key character varying, metric_label character varying, metric_value bigint, metric_note character varying, metric_color character varying, display_order integer)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY
    SELECT
        metrics.metric_key,
        metrics.metric_label,
        metrics.metric_value,
        metrics.metric_note,
        metrics.metric_color,
        metrics.display_order
    FROM (
        SELECT
            'total_staff'::VARCHAR AS metric_key,
            'Total Staff'::VARCHAR AS metric_label,
            (SELECT COUNT(*) FROM staff)::BIGINT AS metric_value,
            'Authorized personnel registered in the system.'::VARCHAR AS metric_note,
            '#3e8cff'::VARCHAR AS metric_color,
            1 AS display_order
        UNION ALL
        SELECT
            'total_donors'::VARCHAR,
            'Total Donors'::VARCHAR,
            (SELECT COUNT(*) FROM donor)::BIGINT,
            'Donor records available for review and processing.'::VARCHAR,
            '#14b8d5'::VARCHAR,
            2
        UNION ALL
        SELECT
            'available_components'::VARCHAR,
            'Available Components'::VARCHAR,
            (SELECT COUNT(*) FROM blood_component WHERE UPPER(status) = 'AVAILABLE')::BIGINT,
            'Blood components ready for controlled use.'::VARCHAR,
            '#5bc784'::VARCHAR,
            3
        UNION ALL
        SELECT
            'total_donations'::VARCHAR,
            'Total Donations'::VARCHAR,
            (SELECT COUNT(*) FROM donation)::BIGINT,
            'Donation events captured in the current inventory system.'::VARCHAR,
            '#f4ae3f'::VARCHAR,
            4
        UNION ALL
        SELECT
            'near_expiry'::VARCHAR,
            'Near Expiry'::VARCHAR,
            (
                SELECT COUNT(*)
                FROM blood_component
                WHERE expiry_timestamp <= CURRENT_TIMESTAMP + INTERVAL '3 days'
            )::BIGINT,
            'Components reaching expiry within the next 3 days.'::VARCHAR,
            '#ff667d'::VARCHAR,
            5
        UNION ALL
        SELECT
            'total_components'::VARCHAR,
            'Total Components'::VARCHAR,
            (SELECT COUNT(*) FROM blood_component)::BIGINT,
            'Total blood components under system supervision.'::VARCHAR,
            '#386bbc'::VARCHAR,
            6
    ) metrics
    ORDER BY metrics.display_order;
END;
$$;


--
-- Name: fn_dashboard_system_overview(integer, character varying, character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_dashboard_system_overview(p_days integer DEFAULT 14, p_metric character varying DEFAULT 'all'::character varying, p_sort character varying DEFAULT 'date_asc'::character varying) RETURNS TABLE(activity_date date, donation_count bigint, component_count bigint, available_count bigint, near_expiry_count bigint, selected_total bigint)
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_days INTEGER := LEAST(GREATEST(COALESCE(p_days, 14), 7), 90);
    v_metric TEXT := LOWER(COALESCE(p_metric, 'all'));
    v_sort TEXT := LOWER(COALESCE(p_sort, 'date_asc'));
BEGIN
    RETURN QUERY
    WITH timeline AS (
        SELECT generate_series(
            CURRENT_DATE - ((v_days - 1) * INTERVAL '1 day'),
            CURRENT_DATE,
            INTERVAL '1 day'
        )::DATE AS activity_date
    ),
    day_counts AS (
        SELECT
            t.activity_date,
            (
                SELECT COUNT(*)
                FROM donation d
                WHERE d.collection_timestamp::DATE = t.activity_date
            ) AS donation_count,
            (
                SELECT COUNT(*)
                FROM blood_component bc
                JOIN donation d ON d.donation_id = bc.donation_id
                WHERE d.collection_timestamp::DATE = t.activity_date
            ) AS component_count,
            (
                SELECT COUNT(*)
                FROM blood_component bc
                JOIN donation d ON d.donation_id = bc.donation_id
                WHERE d.collection_timestamp::DATE = t.activity_date
                  AND UPPER(bc.status) = 'AVAILABLE'
            ) AS available_count,
            (
                SELECT COUNT(*)
                FROM blood_component bc
                WHERE bc.expiry_timestamp::DATE = t.activity_date
            ) AS near_expiry_count
        FROM timeline t
    ),
    scored AS (
        SELECT
            dc.activity_date,
            dc.donation_count,
            dc.component_count,
            dc.available_count,
            dc.near_expiry_count,
            CASE v_metric
                WHEN 'donations' THEN dc.donation_count
                WHEN 'components' THEN dc.component_count
                WHEN 'available' THEN dc.available_count
                WHEN 'near_expiry' THEN dc.near_expiry_count
                ELSE dc.donation_count + dc.component_count + dc.available_count + dc.near_expiry_count
            END AS selected_total
        FROM day_counts dc
    )
    SELECT
        s.activity_date,
        s.donation_count,
        s.component_count,
        s.available_count,
        s.near_expiry_count,
        s.selected_total
    FROM scored s
    ORDER BY
        CASE WHEN v_sort = 'date_desc' THEN s.activity_date END DESC,
        CASE WHEN v_sort = 'value_desc' THEN s.selected_total END DESC,
        CASE WHEN v_sort = 'value_asc' THEN s.selected_total END ASC,
        s.activity_date ASC;
END;
$$;


--
-- Name: fn_expire_stale_staff_sessions(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_expire_stale_staff_sessions() RETURNS void
    LANGUAGE plpgsql
    AS $$
BEGIN
    UPDATE staff_login_session s
    SET status = 'EXPIRED',
        ended_at = CURRENT_TIMESTAMP,
        end_reason = 'TIMEOUT'
    FROM system_security_policy p
    WHERE p.policy_key = 'default'
      AND p.session_control_enabled = TRUE
      AND s.status = 'ACTIVE'
      AND (
          s.last_seen_at
          + ((GREATEST(COALESCE(p.session_timeout_minutes, 15), 1) || ' minutes')::INTERVAL)
      ) < CURRENT_TIMESTAMP;
END;
$$;


--
-- Name: fn_get_deferral_rules(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_get_deferral_rules() RETURNS TABLE(reason_id bigint, description character varying, default_cooling_period_days integer, staff_id bigint)
    LANGUAGE sql
    AS $$
    SELECT
        dr.reason_id,
        dr.description,
        dr.default_cooling_period_days,
        dr.staff_id
    FROM deferral_reason dr
    ORDER BY dr.reason_id ASC;
$$;


--
-- Name: fn_get_storage_locations(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_get_storage_locations() RETURNS TABLE(location_id bigint, description character varying, staff_id bigint)
    LANGUAGE sql
    AS $$
    SELECT
        sl.location_id,
        sl.description,
        sl.staff_id
    FROM storage_location sl
    ORDER BY sl.location_id ASC;
$$;


--
-- Name: fn_hash_staff_password(character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_hash_staff_password(p_password character varying) RETURNS character varying
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_password VARCHAR := fn_normalize_staff_password(p_password);
BEGIN
    IF v_password IS NULL THEN
        RETURN NULL;
    END IF;

    IF fn_is_bcrypt_password(v_password) THEN
        RETURN v_password;
    END IF;

    RETURN crypt(v_password, gen_salt('bf', 10));
END;
$$;


--
-- Name: fn_hash_staff_password_before_save(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_hash_staff_password_before_save() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT'
       OR NEW.password IS DISTINCT FROM OLD.password THEN
        NEW.password := fn_hash_staff_password(NEW.password);
    END IF;

    RETURN NEW;
END;
$$;


--
-- Name: fn_inventory_component_status(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_inventory_component_status() RETURNS TABLE(component_type character varying, status character varying, total_units bigint)
    LANGUAGE sql
    AS $$
    SELECT
        bc.component_type,
        bc.status,
        COUNT(*) AS total_units
    FROM blood_component bc
    GROUP BY bc.component_type, bc.status
    ORDER BY bc.component_type, bc.status;
$$;


--
-- Name: fn_inventory_summary(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_inventory_summary() RETURNS TABLE(total_components bigint, available_components bigint, near_expiry_components bigint, total_donations bigint)
    LANGUAGE sql
    AS $$
    SELECT
        (SELECT COUNT(*) FROM blood_component) AS total_components,
        (SELECT COUNT(*) FROM blood_component WHERE UPPER(status) = 'AVAILABLE') AS available_components,
        (
            SELECT COUNT(*)
            FROM blood_component
            WHERE expiry_timestamp <= CURRENT_TIMESTAMP + INTERVAL '3 days'
              AND UPPER(status) = 'AVAILABLE'
        ) AS near_expiry_components,
        (SELECT COUNT(*) FROM donation) AS total_donations;
$$;


--
-- Name: fn_is_bcrypt_password(character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_is_bcrypt_password(p_password character varying) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$
DECLARE
    v_password VARCHAR := fn_normalize_staff_password(p_password);
BEGIN
    RETURN v_password IS NOT NULL
       AND v_password ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$';
END;
$_$;


--
-- Name: fn_near_expiry_components(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_near_expiry_components() RETURNS TABLE(component_id bigint, component_type character varying, expiry_timestamp timestamp without time zone, status character varying, donation_id bigint, location_id bigint)
    LANGUAGE sql
    AS $$
    SELECT
        bc.component_id,
        bc.component_type,
        bc.expiry_timestamp,
        bc.status,
        bc.donation_id,
        bc.location_id
    FROM blood_component bc
    WHERE bc.expiry_timestamp <= CURRENT_TIMESTAMP + INTERVAL '3 days'
    ORDER BY bc.expiry_timestamp ASC;
$$;


--
-- Name: fn_normalize_staff_password(character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_normalize_staff_password(p_password character varying) RETURNS character varying
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_password VARCHAR := NULLIF(TRIM(p_password), '');
BEGIN
    IF v_password IS NULL THEN
        RETURN NULL;
    END IF;

    IF LOWER(LEFT(v_password, 8)) = '{bcrypt}' THEN
        v_password := SUBSTRING(v_password FROM 9);
    END IF;

    RETURN NULLIF(TRIM(v_password), '');
END;
$$;


--
-- Name: fn_recent_system_notifications(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_recent_system_notifications(p_limit integer DEFAULT 8) RETURNS TABLE(notification_id bigint, module_name character varying, action_type character varying, message character varying, actor_username character varying, actor_full_name character varying, source_ip character varying, created_at timestamp without time zone, is_read boolean)
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_limit INTEGER := LEAST(GREATEST(COALESCE(p_limit, 8), 1), 30);
BEGIN
    RETURN QUERY
    SELECT
        n.notification_id,
        n.module_name,
        n.action_type,
        n.message,
        n.actor_username,
        n.actor_full_name,
        n.source_ip,
        n.created_at,
        n.is_read
    FROM system_activity_notification n
    ORDER BY n.created_at DESC, n.notification_id DESC
    LIMIT v_limit;
END;
$$;


--
-- Name: fn_register_staff_session(character varying, character varying, character varying, character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_register_staff_session(p_username character varying, p_session_id character varying, p_source_ip character varying, p_user_agent character varying) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_session_control_enabled BOOLEAN := TRUE;
    v_max_sessions INTEGER := 1;
    v_timeout_minutes INTEGER := 15;
    v_prevent_new_login BOOLEAN := FALSE;
    v_active_count INTEGER := 0;
    v_expire_count INTEGER := 0;
BEGIN
    SELECT
        session_control_enabled,
        GREATEST(COALESCE(max_concurrent_sessions, 1), 0),
        GREATEST(COALESCE(session_timeout_minutes, 15), 1),
        prevent_new_login
    INTO
        v_session_control_enabled,
        v_max_sessions,
        v_timeout_minutes,
        v_prevent_new_login
    FROM system_security_policy
    WHERE policy_key = 'default';

    PERFORM fn_expire_stale_staff_sessions();

    IF COALESCE(v_session_control_enabled, TRUE) = TRUE AND v_max_sessions > 0 THEN
        SELECT COUNT(*)
        INTO v_active_count
        FROM staff_login_session
        WHERE username = p_username
          AND session_id <> p_session_id
          AND status = 'ACTIVE';

        IF v_active_count >= v_max_sessions THEN
            IF COALESCE(v_prevent_new_login, FALSE) = TRUE THEN
                INSERT INTO staff_login_session (
                    session_id,
                    username,
                    source_ip,
                    user_agent,
                    status,
                    created_at,
                    last_seen_at,
                    expires_at,
                    ended_at,
                    end_reason
                )
                VALUES (
                    p_session_id,
                    p_username,
                    p_source_ip,
                    LEFT(COALESCE(p_user_agent, ''), 255),
                    'BLOCKED',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP,
                    'MAX_SESSIONS'
                )
                ON CONFLICT (session_id) DO UPDATE
                SET username = EXCLUDED.username,
                    source_ip = EXCLUDED.source_ip,
                    user_agent = EXCLUDED.user_agent,
                    status = 'BLOCKED',
                    last_seen_at = CURRENT_TIMESTAMP,
                    expires_at = CURRENT_TIMESTAMP,
                    ended_at = CURRENT_TIMESTAMP,
                    end_reason = 'MAX_SESSIONS';

                RETURN FALSE;
            END IF;

            v_expire_count := v_active_count - v_max_sessions + 1;

            UPDATE staff_login_session
            SET status = 'SUPERSEDED',
                ended_at = CURRENT_TIMESTAMP,
                end_reason = 'MAX_SESSIONS'
            WHERE session_id IN (
                SELECT session_id
                FROM staff_login_session
                WHERE username = p_username
                  AND session_id <> p_session_id
                  AND status = 'ACTIVE'
                ORDER BY last_seen_at ASC, created_at ASC
                LIMIT v_expire_count
            );
        END IF;
    END IF;

    INSERT INTO staff_login_session (
        session_id,
        username,
        source_ip,
        user_agent,
        status,
        created_at,
        last_seen_at,
        expires_at,
        ended_at,
        end_reason
    )
    VALUES (
        p_session_id,
        p_username,
        p_source_ip,
        LEFT(COALESCE(p_user_agent, ''), 255),
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP + ((v_timeout_minutes || ' minutes')::INTERVAL),
        NULL,
        NULL
    )
    ON CONFLICT (session_id) DO UPDATE
    SET username = EXCLUDED.username,
        source_ip = EXCLUDED.source_ip,
        user_agent = EXCLUDED.user_agent,
        status = 'ACTIVE',
        last_seen_at = CURRENT_TIMESTAMP,
        expires_at = CURRENT_TIMESTAMP + ((v_timeout_minutes || ' minutes')::INTERVAL),
        ended_at = NULL,
        end_reason = NULL;

    RETURN TRUE;
END;
$$;


--
-- Name: fn_report_near_expiry_alerts(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_report_near_expiry_alerts() RETURNS TABLE(component_id bigint, component_type character varying, status character varying, expiry_timestamp timestamp without time zone, location_id bigint)
    LANGUAGE sql
    AS $$
    SELECT
        bc.component_id,
        bc.component_type,
        bc.status,
        bc.expiry_timestamp,
        bc.location_id
    FROM blood_component bc
    WHERE bc.expiry_timestamp <= CURRENT_TIMESTAMP + INTERVAL '3 days'
    ORDER BY bc.expiry_timestamp ASC;
$$;


--
-- Name: fn_reports_summary(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_reports_summary() RETURNS TABLE(total_staff bigint, total_donors bigint, total_donations bigint, total_components bigint, available_components bigint, near_expiry_components bigint)
    LANGUAGE sql
    AS $$
    SELECT
        (SELECT COUNT(*) FROM staff) AS total_staff,
        (SELECT COUNT(*) FROM donor) AS total_donors,
        (SELECT COUNT(*) FROM donation) AS total_donations,
        (SELECT COUNT(*) FROM blood_component) AS total_components,
        (SELECT COUNT(*) FROM blood_component WHERE UPPER(status) = 'AVAILABLE') AS available_components,
        (
            SELECT COUNT(*)
            FROM blood_component
            WHERE expiry_timestamp <= CURRENT_TIMESTAMP + INTERVAL '3 days'
        ) AS near_expiry_components;
$$;


--
-- Name: fn_staff_accessible_module_keys(character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_staff_accessible_module_keys(p_staff_type character varying) RETURNS TABLE(module_key character varying)
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_row_level_security_enabled BOOLEAN := TRUE;
BEGIN
    SELECT row_level_security_enabled
    INTO v_row_level_security_enabled
    FROM system_security_policy
    WHERE policy_key = 'default';

    IF COALESCE(v_row_level_security_enabled, TRUE) = FALSE THEN
        RETURN QUERY
        SELECT ranked.module_key
        FROM (
            SELECT
                access.module_key,
                MIN(access.sort_order) AS sort_order
            FROM staff_module_access access
            WHERE access.is_enabled = TRUE
            GROUP BY access.module_key
        ) ranked
        ORDER BY ranked.sort_order ASC, ranked.module_key ASC;
        RETURN;
    END IF;

    RETURN QUERY
    SELECT access.module_key
    FROM staff_module_access access
    WHERE access.staff_type = p_staff_type
      AND access.is_enabled = TRUE
    ORDER BY access.sort_order ASC, access.module_key ASC;
END;
$$;


--
-- Name: fn_staff_can_access_path(character varying, character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_staff_can_access_path(p_staff_type character varying, p_request_path character varying) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_row_level_security_enabled BOOLEAN := TRUE;
BEGIN
    SELECT row_level_security_enabled
    INTO v_row_level_security_enabled
    FROM system_security_policy
    WHERE policy_key = 'default';

    IF COALESCE(v_row_level_security_enabled, TRUE) = FALSE THEN
        RETURN TRUE;
    END IF;

    RETURN EXISTS (
        SELECT 1
        FROM staff_module_access access
        WHERE access.staff_type = p_staff_type
          AND access.is_enabled = TRUE
          AND (
              p_request_path = access.url_pattern
              OR (
                  RIGHT(access.url_pattern, 3) = '/**'
                  AND (
                      p_request_path = LEFT(access.url_pattern, LENGTH(access.url_pattern) - 3)
                      OR p_request_path LIKE (LEFT(access.url_pattern, LENGTH(access.url_pattern) - 2) || '%')
                  )
              )
              OR p_request_path LIKE REPLACE(REPLACE(access.url_pattern, '**', '%'), '*', '%')
          )
    );
END;
$$;


--
-- Name: fn_staff_totals_by_role(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_staff_totals_by_role() RETURNS TABLE(staff_type character varying, total_staff bigint)
    LANGUAGE sql
    AS $$
    SELECT
        s.staff_type,
        COUNT(*) AS total_staff
    FROM staff s
    GROUP BY s.staff_type
    ORDER BY s.staff_type;
$$;


--
-- Name: fn_sync_staff_lock_status(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_sync_staff_lock_status() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF COALESCE(NEW.is_active, TRUE) = FALSE THEN
        NEW.is_locked := TRUE;

        IF TG_OP = 'INSERT'
           OR COALESCE(OLD.is_active, TRUE) <> COALESCE(NEW.is_active, TRUE)
           OR NEW.locked_at IS NULL THEN
            NEW.locked_at := CURRENT_TIMESTAMP;
        END IF;
    ELSE
        NEW.is_locked := FALSE;
        NEW.locked_at := NULL;
    END IF;

    RETURN NEW;
END;
$$;


--
-- Name: fn_touch_staff_session(character varying, character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_touch_staff_session(p_username character varying, p_session_id character varying) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_session_control_enabled BOOLEAN := TRUE;
    v_timeout_minutes INTEGER := 15;
BEGIN
    SELECT
        session_control_enabled,
        GREATEST(COALESCE(session_timeout_minutes, 15), 1)
    INTO
        v_session_control_enabled,
        v_timeout_minutes
    FROM system_security_policy
    WHERE policy_key = 'default';

    IF COALESCE(v_session_control_enabled, TRUE) = FALSE THEN
        RETURN TRUE;
    END IF;

    PERFORM fn_expire_stale_staff_sessions();

    UPDATE staff_login_session
    SET last_seen_at = CURRENT_TIMESTAMP,
        expires_at = CURRENT_TIMESTAMP + ((v_timeout_minutes || ' minutes')::INTERVAL)
    WHERE username = p_username
      AND session_id = p_session_id
      AND status = 'ACTIVE';

    RETURN FOUND;
END;
$$;


--
-- Name: fn_unread_system_notification_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_unread_system_notification_count() RETURNS bigint
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN (
        SELECT COUNT(*)
        FROM system_activity_notification
        WHERE is_read = FALSE
    );
END;
$$;


--
-- Name: sp_add_deferral_rule(character varying, integer, bigint); Type: PROCEDURE; Schema: public; Owner: -
--

CREATE PROCEDURE public.sp_add_deferral_rule(IN p_description character varying, IN p_default_cooling_period_days integer, IN p_staff_id bigint)
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF p_description IS NULL OR TRIM(p_description) = '' THEN
        RAISE EXCEPTION 'Description cannot be empty';
    END IF;

    IF p_default_cooling_period_days IS NULL OR p_default_cooling_period_days < 0 THEN
        RAISE EXCEPTION 'Cooling period must be 0 or greater';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM blood_administrator ba WHERE ba.staff_id = p_staff_id
    ) THEN
        RAISE EXCEPTION 'Only Blood Administrator can create deferral rules';
    END IF;

    INSERT INTO deferral_reason (description, default_cooling_period_days, staff_id)
    VALUES (p_description, p_default_cooling_period_days, p_staff_id);
END;
$$;


--
-- Name: sp_add_storage_location(character varying, bigint); Type: PROCEDURE; Schema: public; Owner: -
--

CREATE PROCEDURE public.sp_add_storage_location(IN p_description character varying, IN p_staff_id bigint)
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF p_description IS NULL OR TRIM(p_description) = '' THEN
        RAISE EXCEPTION 'Storage description cannot be empty';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM blood_administrator ba
        WHERE ba.staff_id = p_staff_id
    ) THEN
        RAISE EXCEPTION 'Only Blood Administrator can create storage locations';
    END IF;

    INSERT INTO storage_location (description, staff_id)
    VALUES (p_description, p_staff_id);
END;
$$;


--
-- Name: sp_add_system_notification(character varying, character varying, character varying, character varying, character varying); Type: PROCEDURE; Schema: public; Owner: -
--

CREATE PROCEDURE public.sp_add_system_notification(IN p_module_name character varying, IN p_action_type character varying, IN p_message character varying, IN p_actor_username character varying, IN p_source_ip character varying DEFAULT NULL::character varying)
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_actor_username VARCHAR := NULLIF(TRIM(p_actor_username), '');
    v_actor_full_name VARCHAR;
BEGIN
    SELECT s.full_name
    INTO v_actor_full_name
    FROM staff s
    WHERE s.username = v_actor_username
    LIMIT 1;

    INSERT INTO system_activity_notification (
        module_name,
        action_type,
        message,
        actor_username,
        actor_full_name,
        source_ip
    )
    VALUES (
        COALESCE(NULLIF(TRIM(p_module_name), ''), 'System'),
        UPPER(COALESCE(NULLIF(TRIM(p_action_type), ''), 'UPDATE')),
        COALESCE(NULLIF(TRIM(p_message), ''), 'System activity recorded.'),
        v_actor_username,
        NULLIF(TRIM(COALESCE(v_actor_full_name, '')), ''),
        NULLIF(TRIM(p_source_ip), '')
    );
END;
$$;


--
-- Name: sp_end_staff_session(character varying, character varying); Type: PROCEDURE; Schema: public; Owner: -
--

CREATE PROCEDURE public.sp_end_staff_session(IN p_session_id character varying, IN p_reason character varying)
    LANGUAGE plpgsql
    AS $$
BEGIN
    UPDATE staff_login_session
    SET status = CASE
            WHEN status = 'ACTIVE' THEN 'ENDED'
            ELSE status
        END,
        ended_at = COALESCE(ended_at, CURRENT_TIMESTAMP),
        end_reason = CASE
            WHEN status = 'ACTIVE' THEN COALESCE(NULLIF(TRIM(p_reason), ''), 'ENDED')
            ELSE COALESCE(end_reason, NULLIF(TRIM(p_reason), ''), 'ENDED')
        END
    WHERE session_id = p_session_id;
END;
$$;


--
-- Name: sp_mark_all_system_notifications_read(); Type: PROCEDURE; Schema: public; Owner: -
--

CREATE PROCEDURE public.sp_mark_all_system_notifications_read()
    LANGUAGE plpgsql
    AS $$
BEGIN
    UPDATE system_activity_notification
    SET is_read = TRUE
    WHERE is_read = FALSE;
END;
$$;


--
-- Name: sp_set_staff_account_status(bigint, boolean); Type: PROCEDURE; Schema: public; Owner: -
--

CREATE PROCEDURE public.sp_set_staff_account_status(IN p_staff_id bigint, IN p_is_active boolean)
    LANGUAGE plpgsql
    AS $$
BEGIN
    UPDATE staff
    SET is_active = COALESCE(p_is_active, FALSE)
    WHERE staff_id = p_staff_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Staff account with ID % was not found.', p_staff_id;
    END IF;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: blood_component; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.blood_component (
    component_id bigint NOT NULL,
    component_type character varying(20) NOT NULL,
    expiry_timestamp timestamp without time zone NOT NULL,
    status character varying(20) NOT NULL,
    donation_id bigint NOT NULL,
    location_id bigint NOT NULL,
    CONSTRAINT chk_component_status CHECK (((status)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'RESERVED'::character varying, 'USED'::character varying, 'EXPIRED'::character varying, 'QUARANTINED'::character varying, 'DISCARDED'::character varying])::text[]))),
    CONSTRAINT chk_component_type CHECK (((component_type)::text = ANY ((ARRAY['RBC'::character varying, 'PLASMA'::character varying, 'PLATELET'::character varying])::text[])))
);


--
-- Name: donation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.donation (
    donation_id bigint NOT NULL,
    collection_timestamp timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    donor_id bigint NOT NULL,
    staff_id bigint NOT NULL
);


--
-- Name: donor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.donor (
    donor_id bigint NOT NULL,
    ic_number character varying(20) NOT NULL,
    full_name character varying(100) NOT NULL,
    blood_group character varying(5) NOT NULL,
    deferral_expiry_date date,
    CONSTRAINT chk_donor_blood_group CHECK (((blood_group)::text = ANY ((ARRAY['A+'::character varying, 'A-'::character varying, 'B+'::character varying, 'B-'::character varying, 'AB+'::character varying, 'AB-'::character varying, 'O+'::character varying, 'O-'::character varying])::text[])))
);


--
-- Name: storage_location; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.storage_location (
    location_id bigint NOT NULL,
    description character varying(255) NOT NULL,
    staff_id bigint NOT NULL
);


--
-- Name: available_components_fifo; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.available_components_fifo AS
 SELECT bc.component_id,
    bc.component_type,
    bc.expiry_timestamp,
    bc.status,
    d.donation_id,
    dn.full_name AS donor_name,
    dn.blood_group,
    sl.description AS storage_location
   FROM (((public.blood_component bc
     JOIN public.donation d ON ((bc.donation_id = d.donation_id)))
     JOIN public.donor dn ON ((d.donor_id = dn.donor_id)))
     JOIN public.storage_location sl ON ((bc.location_id = sl.location_id)))
  WHERE ((bc.status)::text = 'AVAILABLE'::text)
  ORDER BY bc.expiry_timestamp;


--
-- Name: blood_administrator; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.blood_administrator (
    staff_id bigint NOT NULL,
    department character varying(100) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(100),
    last_modified_by character varying(100),
    updated_at timestamp(6) without time zone
);


--
-- Name: blood_component_component_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.blood_component ALTER COLUMN component_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.blood_component_component_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: deferral_reason; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.deferral_reason (
    reason_id bigint NOT NULL,
    description character varying(255) NOT NULL,
    default_cooling_period_days integer NOT NULL,
    staff_id bigint NOT NULL,
    CONSTRAINT chk_cooling_period CHECK ((default_cooling_period_days >= 0))
);


--
-- Name: deferral_reason_reason_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.deferral_reason ALTER COLUMN reason_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.deferral_reason_reason_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: donation_donation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.donation ALTER COLUMN donation_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.donation_donation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: donor_deferral_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.donor_deferral_history (
    donor_id bigint NOT NULL,
    staff_id bigint NOT NULL,
    reason_id bigint NOT NULL,
    date_recorded date DEFAULT CURRENT_DATE NOT NULL
);


--
-- Name: donor_donor_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.donor ALTER COLUMN donor_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.donor_donor_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: lab_technician; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lab_technician (
    staff_id bigint NOT NULL,
    certification_no character varying(50) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(100),
    last_modified_by character varying(100),
    updated_at timestamp(6) without time zone
);


--
-- Name: lab_test; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lab_test (
    test_id bigint NOT NULL,
    tti_screening character varying(30) NOT NULL,
    blood_type_match character varying(20) NOT NULL,
    final_status character varying(20) NOT NULL,
    test_date date NOT NULL,
    staff_id bigint NOT NULL,
    donation_id bigint NOT NULL,
    CONSTRAINT chk_labtest_blood_type_match CHECK (((blood_type_match)::text = ANY ((ARRAY['MATCHED'::character varying, 'NOT_MATCHED'::character varying, 'PENDING'::character varying])::text[]))),
    CONSTRAINT chk_labtest_final_status CHECK (((final_status)::text = ANY ((ARRAY['PASSED'::character varying, 'FAILED'::character varying, 'QUARANTINED'::character varying])::text[])))
);


--
-- Name: lab_test_test_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.lab_test ALTER COLUMN test_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.lab_test_test_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: medical_staff; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.medical_staff (
    staff_id bigint NOT NULL,
    license_no character varying(50) NOT NULL,
    "position" character varying(50) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(100),
    last_modified_by character varying(100),
    updated_at timestamp(6) without time zone
);


--
-- Name: patient; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.patient (
    patient_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    condition character varying(255)
);


--
-- Name: patient_patient_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.patient ALTER COLUMN patient_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.patient_patient_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: staff; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.staff (
    staff_id bigint NOT NULL,
    staff_type character varying(31) NOT NULL,
    full_name character varying(100) NOT NULL,
    username character varying(50) NOT NULL,
    password character varying(255) NOT NULL,
    phone_no character varying(20),
    ic_number character varying(20) NOT NULL,
    gender character varying(10),
    email character varying(100),
    profile_photo character varying(255) DEFAULT 'default.png'::character varying,
    is_active boolean DEFAULT true NOT NULL,
    is_locked boolean DEFAULT false NOT NULL,
    locked_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone,
    created_by character varying(100),
    last_modified_by character varying(100),
    updated_at timestamp(6) without time zone,
    CONSTRAINT chk_staff_gender CHECK ((((gender)::text = ANY ((ARRAY['MALE'::character varying, 'FEMALE'::character varying])::text[])) OR (gender IS NULL))),
    CONSTRAINT chk_staff_type CHECK (((staff_type)::text = ANY (ARRAY[('LAB_TECHNICIAN'::character varying)::text, ('MEDICAL_STAFF'::character varying)::text, ('BLOOD_ADMINISTRATOR'::character varying)::text])))
);


--
-- Name: staff_login_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.staff_login_session (
    session_id character varying(128) NOT NULL,
    username character varying(80) NOT NULL,
    source_ip character varying(80),
    user_agent character varying(255),
    status character varying(24) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_seen_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at timestamp without time zone,
    ended_at timestamp without time zone,
    end_reason character varying(80)
);


--
-- Name: staff_module_access; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.staff_module_access (
    access_id bigint NOT NULL,
    created_at timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    is_enabled boolean NOT NULL,
    module_key character varying(80) NOT NULL,
    module_name character varying(120) NOT NULL,
    sort_order integer NOT NULL,
    staff_type character varying(40) NOT NULL,
    updated_at timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    url_pattern character varying(180) NOT NULL,
    created_by character varying(100),
    last_modified_by character varying(100),
    CONSTRAINT staff_module_access_staff_type_check CHECK (((staff_type)::text = ANY ((ARRAY['BLOOD_ADMINISTRATOR'::character varying, 'MEDICAL_STAFF'::character varying, 'LAB_TECHNICIAN'::character varying])::text[])))
);


--
-- Name: staff_module_access_access_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.staff_module_access ALTER COLUMN access_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.staff_module_access_access_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: staff_staff_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.staff ALTER COLUMN staff_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.staff_staff_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: storage_location_location_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.storage_location ALTER COLUMN location_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.storage_location_location_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: system_activity_notification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_activity_notification (
    notification_id bigint NOT NULL,
    module_name character varying(80) NOT NULL,
    action_type character varying(20) NOT NULL,
    message character varying(255) NOT NULL,
    actor_username character varying(80),
    actor_full_name character varying(160),
    source_ip character varying(60),
    is_read boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: system_activity_notification_notification_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_activity_notification_notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_activity_notification_notification_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_activity_notification_notification_id_seq OWNED BY public.system_activity_notification.notification_id;


--
-- Name: system_backup_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_backup_config (
    config_key character varying(80) NOT NULL,
    backup_directory character varying(500) DEFAULT 'backups'::character varying NOT NULL,
    auto_backup_enabled boolean DEFAULT false NOT NULL,
    schedule_frequency character varying(20) DEFAULT 'DAILY'::character varying NOT NULL,
    schedule_time character varying(5) DEFAULT '23:00'::character varying NOT NULL,
    retention_days integer DEFAULT 30 NOT NULL,
    last_backup_at timestamp without time zone,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: system_backup_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_backup_history (
    backup_id bigint NOT NULL,
    trigger_type character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    file_name character varying(255),
    file_path character varying(700),
    file_size_bytes bigint,
    triggered_by character varying(80),
    started_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at timestamp without time zone,
    message character varying(700)
);


--
-- Name: system_backup_history_backup_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_backup_history_backup_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_backup_history_backup_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_backup_history_backup_id_seq OWNED BY public.system_backup_history.backup_id;


--
-- Name: system_notification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_notification (
    notification_id bigint NOT NULL,
    module_name character varying(80) NOT NULL,
    action_type character varying(20) NOT NULL,
    message character varying(255) NOT NULL,
    actor_username character varying(80),
    is_read boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: system_notification_notification_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_notification_notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_notification_notification_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_notification_notification_id_seq OWNED BY public.system_notification.notification_id;


--
-- Name: system_security_policy; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_security_policy (
    policy_key character varying(80) NOT NULL,
    session_control_enabled boolean DEFAULT true NOT NULL,
    max_concurrent_sessions integer DEFAULT 1 NOT NULL,
    session_timeout_minutes integer DEFAULT 15 NOT NULL,
    prevent_new_login boolean DEFAULT false NOT NULL,
    row_level_security_enabled boolean DEFAULT true NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: system_setting; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_setting (
    setting_key character varying(80) NOT NULL,
    setting_value character varying(500) NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: transfusion_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transfusion_record (
    component_id bigint NOT NULL,
    staff_id bigint NOT NULL,
    patient_id bigint NOT NULL,
    transfusion_timestamp timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: system_activity_notification notification_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_activity_notification ALTER COLUMN notification_id SET DEFAULT nextval('public.system_activity_notification_notification_id_seq'::regclass);


--
-- Name: system_backup_history backup_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_backup_history ALTER COLUMN backup_id SET DEFAULT nextval('public.system_backup_history_backup_id_seq'::regclass);


--
-- Name: system_notification notification_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_notification ALTER COLUMN notification_id SET DEFAULT nextval('public.system_notification_notification_id_seq'::regclass);


--
-- Name: blood_administrator blood_administrator_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blood_administrator
    ADD CONSTRAINT blood_administrator_pkey PRIMARY KEY (staff_id);


--
-- Name: blood_component blood_component_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blood_component
    ADD CONSTRAINT blood_component_pkey PRIMARY KEY (component_id);


--
-- Name: deferral_reason deferral_reason_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deferral_reason
    ADD CONSTRAINT deferral_reason_pkey PRIMARY KEY (reason_id);


--
-- Name: donation donation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.donation
    ADD CONSTRAINT donation_pkey PRIMARY KEY (donation_id);


--
-- Name: donor_deferral_history donor_deferral_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.donor_deferral_history
    ADD CONSTRAINT donor_deferral_history_pkey PRIMARY KEY (donor_id, staff_id, reason_id);


--
-- Name: donor donor_ic_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.donor
    ADD CONSTRAINT donor_ic_number_key UNIQUE (ic_number);


--
-- Name: donor donor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.donor
    ADD CONSTRAINT donor_pkey PRIMARY KEY (donor_id);


--
-- Name: lab_technician lab_technician_certification_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lab_technician
    ADD CONSTRAINT lab_technician_certification_no_key UNIQUE (certification_no);


--
-- Name: lab_technician lab_technician_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lab_technician
    ADD CONSTRAINT lab_technician_pkey PRIMARY KEY (staff_id);


--
-- Name: lab_test lab_test_donation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lab_test
    ADD CONSTRAINT lab_test_donation_id_key UNIQUE (donation_id);


--
-- Name: lab_test lab_test_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lab_test
    ADD CONSTRAINT lab_test_pkey PRIMARY KEY (test_id);


--
-- Name: medical_staff medical_staff_license_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medical_staff
    ADD CONSTRAINT medical_staff_license_no_key UNIQUE (license_no);


--
-- Name: medical_staff medical_staff_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medical_staff
    ADD CONSTRAINT medical_staff_pkey PRIMARY KEY (staff_id);


--
-- Name: patient patient_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient
    ADD CONSTRAINT patient_pkey PRIMARY KEY (patient_id);


--
-- Name: staff staff_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff
    ADD CONSTRAINT staff_email_key UNIQUE (email);


--
-- Name: staff staff_ic_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff
    ADD CONSTRAINT staff_ic_number_key UNIQUE (ic_number);


--
-- Name: staff_login_session staff_login_session_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_login_session
    ADD CONSTRAINT staff_login_session_pkey PRIMARY KEY (session_id);


--
-- Name: staff_module_access staff_module_access_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_module_access
    ADD CONSTRAINT staff_module_access_pkey PRIMARY KEY (access_id);


--
-- Name: staff staff_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff
    ADD CONSTRAINT staff_pkey PRIMARY KEY (staff_id);


--
-- Name: staff staff_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff
    ADD CONSTRAINT staff_username_key UNIQUE (username);


--
-- Name: storage_location storage_location_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_location
    ADD CONSTRAINT storage_location_pkey PRIMARY KEY (location_id);


--
-- Name: system_activity_notification system_activity_notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_activity_notification
    ADD CONSTRAINT system_activity_notification_pkey PRIMARY KEY (notification_id);


--
-- Name: system_backup_config system_backup_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_backup_config
    ADD CONSTRAINT system_backup_config_pkey PRIMARY KEY (config_key);


--
-- Name: system_backup_history system_backup_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_backup_history
    ADD CONSTRAINT system_backup_history_pkey PRIMARY KEY (backup_id);


--
-- Name: system_notification system_notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_notification
    ADD CONSTRAINT system_notification_pkey PRIMARY KEY (notification_id);


--
-- Name: system_security_policy system_security_policy_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_security_policy
    ADD CONSTRAINT system_security_policy_pkey PRIMARY KEY (policy_key);


--
-- Name: system_setting system_setting_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_setting
    ADD CONSTRAINT system_setting_pkey PRIMARY KEY (setting_key);


--
-- Name: transfusion_record transfusion_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfusion_record
    ADD CONSTRAINT transfusion_record_pkey PRIMARY KEY (component_id, staff_id, patient_id);


--
-- Name: staff_module_access uk_staff_module_access_role_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_module_access
    ADD CONSTRAINT uk_staff_module_access_role_key UNIQUE (staff_type, module_key);


--
-- Name: idx_blood_component_donation_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_blood_component_donation_id ON public.blood_component USING btree (donation_id);


--
-- Name: idx_blood_component_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_blood_component_expiry ON public.blood_component USING btree (expiry_timestamp);


--
-- Name: idx_blood_component_location_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_blood_component_location_id ON public.blood_component USING btree (location_id);


--
-- Name: idx_blood_component_type_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_blood_component_type_status ON public.blood_component USING btree (component_type, status);


--
-- Name: idx_donation_donor_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_donation_donor_id ON public.donation USING btree (donor_id);


--
-- Name: idx_donation_staff_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_donation_staff_id ON public.donation USING btree (staff_id);


--
-- Name: idx_donor_blood_group; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_donor_blood_group ON public.donor USING btree (blood_group);


--
-- Name: idx_donor_deferral_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_donor_deferral_expiry ON public.donor USING btree (deferral_expiry_date);


--
-- Name: idx_lab_test_staff_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lab_test_staff_id ON public.lab_test USING btree (staff_id);


--
-- Name: idx_transfusion_patient_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transfusion_patient_id ON public.transfusion_record USING btree (patient_id);


--
-- Name: staff trg_hash_staff_password_before_save; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_hash_staff_password_before_save BEFORE INSERT OR UPDATE OF password ON public.staff FOR EACH ROW EXECUTE FUNCTION public.fn_hash_staff_password_before_save();


--
-- Name: staff trg_sync_staff_lock_status; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_sync_staff_lock_status BEFORE INSERT OR UPDATE OF is_active ON public.staff FOR EACH ROW EXECUTE FUNCTION public.fn_sync_staff_lock_status();


--
-- Name: blood_administrator fk_blood_admin_staff; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blood_administrator
    ADD CONSTRAINT fk_blood_admin_staff FOREIGN KEY (staff_id) REFERENCES public.staff(staff_id) ON DELETE CASCADE;


--
-- Name: blood_component fk_blood_component_donation; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blood_component
    ADD CONSTRAINT fk_blood_component_donation FOREIGN KEY (donation_id) REFERENCES public.donation(donation_id) ON DELETE CASCADE;


--
-- Name: blood_component fk_blood_component_location; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blood_component
    ADD CONSTRAINT fk_blood_component_location FOREIGN KEY (location_id) REFERENCES public.storage_location(location_id) ON DELETE RESTRICT;


--
-- Name: donor_deferral_history fk_deferral_history_donor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.donor_deferral_history
    ADD CONSTRAINT fk_deferral_history_donor FOREIGN KEY (donor_id) REFERENCES public.donor(donor_id) ON DELETE CASCADE;


--
-- Name: donor_deferral_history fk_deferral_history_reason; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.donor_deferral_history
    ADD CONSTRAINT fk_deferral_history_reason FOREIGN KEY (reason_id) REFERENCES public.deferral_reason(reason_id) ON DELETE RESTRICT;


--
-- Name: donor_deferral_history fk_deferral_history_staff; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.donor_deferral_history
    ADD CONSTRAINT fk_deferral_history_staff FOREIGN KEY (staff_id) REFERENCES public.medical_staff(staff_id) ON DELETE RESTRICT;


--
-- Name: deferral_reason fk_deferral_reason_staff; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deferral_reason
    ADD CONSTRAINT fk_deferral_reason_staff FOREIGN KEY (staff_id) REFERENCES public.blood_administrator(staff_id) ON DELETE RESTRICT;


--
-- Name: donation fk_donation_donor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.donation
    ADD CONSTRAINT fk_donation_donor FOREIGN KEY (donor_id) REFERENCES public.donor(donor_id) ON DELETE RESTRICT;


--
-- Name: donation fk_donation_staff; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.donation
    ADD CONSTRAINT fk_donation_staff FOREIGN KEY (staff_id) REFERENCES public.medical_staff(staff_id) ON DELETE RESTRICT;


--
-- Name: lab_technician fk_lab_technician_staff; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lab_technician
    ADD CONSTRAINT fk_lab_technician_staff FOREIGN KEY (staff_id) REFERENCES public.staff(staff_id) ON DELETE CASCADE;


--
-- Name: lab_test fk_labtest_donation; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lab_test
    ADD CONSTRAINT fk_labtest_donation FOREIGN KEY (donation_id) REFERENCES public.donation(donation_id) ON DELETE CASCADE;


--
-- Name: lab_test fk_labtest_staff; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lab_test
    ADD CONSTRAINT fk_labtest_staff FOREIGN KEY (staff_id) REFERENCES public.lab_technician(staff_id) ON DELETE RESTRICT;


--
-- Name: medical_staff fk_medical_staff_staff; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medical_staff
    ADD CONSTRAINT fk_medical_staff_staff FOREIGN KEY (staff_id) REFERENCES public.staff(staff_id) ON DELETE CASCADE;


--
-- Name: storage_location fk_storage_location_staff; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_location
    ADD CONSTRAINT fk_storage_location_staff FOREIGN KEY (staff_id) REFERENCES public.blood_administrator(staff_id) ON DELETE RESTRICT;


--
-- Name: transfusion_record fk_transfusion_component; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfusion_record
    ADD CONSTRAINT fk_transfusion_component FOREIGN KEY (component_id) REFERENCES public.blood_component(component_id) ON DELETE RESTRICT;


--
-- Name: transfusion_record fk_transfusion_patient; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfusion_record
    ADD CONSTRAINT fk_transfusion_patient FOREIGN KEY (patient_id) REFERENCES public.patient(patient_id) ON DELETE RESTRICT;


--
-- Name: transfusion_record fk_transfusion_staff; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfusion_record
    ADD CONSTRAINT fk_transfusion_staff FOREIGN KEY (staff_id) REFERENCES public.medical_staff(staff_id) ON DELETE RESTRICT;


--
-- Name: staff_module_access; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.staff_module_access ENABLE ROW LEVEL SECURITY;

--
-- Name: staff_module_access staff_module_access_by_staff_type; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY staff_module_access_by_staff_type ON public.staff_module_access FOR SELECT USING (((current_setting('bloodinventory.staff_type'::text, true) IS NULL) OR ((staff_type)::text = current_setting('bloodinventory.staff_type'::text, true))));


--
-- PostgreSQL database dump complete
--
