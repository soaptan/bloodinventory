-- Keep historical references intact by archiving storage locations and deferral rules
-- instead of physically deleting rows that may be used by inventory or donor history.

ALTER TABLE public.storage_location
    ADD COLUMN IF NOT EXISTS is_active boolean NOT NULL DEFAULT TRUE;

ALTER TABLE public.deferral_reason
    ADD COLUMN IF NOT EXISTS is_active boolean NOT NULL DEFAULT TRUE;

DROP FUNCTION IF EXISTS public.fn_count_storage_locations();
CREATE FUNCTION public.fn_count_storage_locations() RETURNS bigint
    LANGUAGE sql
    AS $$
    SELECT COUNT(*) FROM storage_location WHERE is_active = TRUE;
$$;

DROP FUNCTION IF EXISTS public.fn_count_deferral_rules();
CREATE FUNCTION public.fn_count_deferral_rules() RETURNS bigint
    LANGUAGE sql
    AS $$
    SELECT COUNT(*) FROM deferral_reason WHERE is_active = TRUE;
$$;

DROP FUNCTION IF EXISTS public.fn_get_storage_locations();
CREATE FUNCTION public.fn_get_storage_locations()
RETURNS TABLE(location_id bigint, description character varying, staff_id bigint, is_active boolean)
    LANGUAGE sql
    AS $$
    SELECT
        sl.location_id,
        sl.description,
        sl.staff_id,
        sl.is_active
    FROM storage_location sl
    ORDER BY sl.is_active DESC, sl.location_id ASC;
$$;

DROP FUNCTION IF EXISTS public.fn_get_deferral_rules();
CREATE FUNCTION public.fn_get_deferral_rules()
RETURNS TABLE(reason_id bigint, description character varying, default_cooling_period_days integer, staff_id bigint, is_active boolean)
    LANGUAGE sql
    AS $$
    SELECT
        dr.reason_id,
        dr.description,
        dr.default_cooling_period_days,
        dr.staff_id,
        dr.is_active
    FROM deferral_reason dr
    ORDER BY dr.is_active DESC, dr.reason_id ASC;
$$;
