CREATE UNIQUE INDEX IF NOT EXISTS ux_deferral_reason_description_normalized
    ON public.deferral_reason (LOWER(BTRIM(description)));

CREATE OR REPLACE PROCEDURE public.sp_add_deferral_rule(
    IN p_description character varying,
    IN p_default_cooling_period_days integer,
    IN p_staff_id bigint,
    IN p_lock_type character varying DEFAULT 'TEMPORARY'
)
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_lock_type VARCHAR := UPPER(TRIM(COALESCE(p_lock_type, 'TEMPORARY')));
BEGIN
    IF p_description IS NULL OR TRIM(p_description) = '' THEN
        RAISE EXCEPTION 'Description cannot be empty';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.deferral_reason
        WHERE LOWER(BTRIM(description)) = LOWER(BTRIM(p_description))
    ) THEN
        RAISE EXCEPTION 'Deferral rule name already exists.';
    END IF;

    IF v_lock_type NOT IN ('TEMPORARY', 'PERMANENT') THEN
        RAISE EXCEPTION 'Deferral lock type must be TEMPORARY or PERMANENT';
    END IF;

    IF p_default_cooling_period_days IS NULL OR p_default_cooling_period_days < 0 THEN
        RAISE EXCEPTION 'Cooling period must be 0 or greater';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.blood_administrator ba WHERE ba.staff_id = p_staff_id
    ) THEN
        RAISE EXCEPTION 'Only Blood Administrator can create deferral rules';
    END IF;

    INSERT INTO public.deferral_reason (description, default_cooling_period_days, lock_type, staff_id)
    VALUES (TRIM(p_description), p_default_cooling_period_days, v_lock_type, p_staff_id);
END;
$$;
