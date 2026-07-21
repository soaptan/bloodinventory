-- Permanent deferral reasons do not use cooling-off periods.

UPDATE public.deferral_reason
SET default_cooling_period_days = 0
WHERE UPPER(lock_type) = 'PERMANENT'
  AND default_cooling_period_days <> 0;

ALTER TABLE public.deferral_reason
    DROP CONSTRAINT IF EXISTS chk_deferral_permanent_zero_cooling;

ALTER TABLE public.deferral_reason
    ADD CONSTRAINT chk_deferral_permanent_zero_cooling
    CHECK (UPPER(lock_type) <> 'PERMANENT' OR default_cooling_period_days = 0);

DROP PROCEDURE IF EXISTS public.sp_add_deferral_rule(character varying, integer, bigint);
DROP PROCEDURE IF EXISTS public.sp_add_deferral_rule(character varying, integer, bigint, character varying);
CREATE PROCEDURE public.sp_add_deferral_rule(
    IN p_description character varying,
    IN p_default_cooling_period_days integer,
    IN p_staff_id bigint,
    IN p_lock_type character varying DEFAULT 'TEMPORARY'
)
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_lock_type VARCHAR := UPPER(TRIM(COALESCE(p_lock_type, 'TEMPORARY')));
    v_cooling_days INTEGER;
BEGIN
    IF p_description IS NULL OR TRIM(p_description) = '' THEN
        RAISE EXCEPTION 'Description cannot be empty';
    END IF;

    IF v_lock_type NOT IN ('TEMPORARY', 'PERMANENT') THEN
        RAISE EXCEPTION 'Deferral lock type must be TEMPORARY or PERMANENT';
    END IF;

    IF v_lock_type = 'PERMANENT' THEN
        v_cooling_days := 0;
    ELSE
        IF p_default_cooling_period_days IS NULL OR p_default_cooling_period_days < 0 THEN
            RAISE EXCEPTION 'Cooling period must be 0 or greater';
        END IF;
        v_cooling_days := p_default_cooling_period_days;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM blood_administrator ba WHERE ba.staff_id = p_staff_id
    ) THEN
        RAISE EXCEPTION 'Only Blood Administrator can create deferral rules';
    END IF;

    INSERT INTO deferral_reason (description, default_cooling_period_days, lock_type, staff_id)
    VALUES (TRIM(p_description), v_cooling_days, v_lock_type, p_staff_id);
END;
$$;
