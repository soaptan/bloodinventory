-- Allow deferral reasons to drive either temporary or permanent donor locks.

ALTER TABLE public.deferral_reason
    ADD COLUMN IF NOT EXISTS lock_type character varying(20) NOT NULL DEFAULT 'TEMPORARY';

ALTER TABLE public.donor
    ADD COLUMN IF NOT EXISTS permanent_deferral boolean NOT NULL DEFAULT FALSE;

UPDATE public.deferral_reason
SET lock_type = 'TEMPORARY'
WHERE lock_type IS NULL
   OR UPPER(lock_type) NOT IN ('TEMPORARY', 'PERMANENT');

UPDATE public.deferral_reason
SET lock_type = 'PERMANENT'
WHERE description ~* '(permanent|hiv|aids|hepatitis[[:space:]]*b|hepatitis[[:space:]]*c|hbsag|anti-hcv|htlv|creutzfeldt|(^|[^a-z])cjd([^a-z]|$)|prion|injection[[:space:]]+drug|intravenous[[:space:]]+drug|iv[[:space:]]+drug|malignancy|cancer|leukaemia|leukemia|lymphoma|renal[[:space:]]+failure|chronic[[:space:]]+kidney|chronic[[:space:]]+liver|severe[[:space:]]+cardiac|serious[[:space:]]+heart)';

ALTER TABLE public.deferral_reason
    DROP CONSTRAINT IF EXISTS chk_deferral_lock_type;

ALTER TABLE public.deferral_reason
    ADD CONSTRAINT chk_deferral_lock_type
    CHECK (UPPER(lock_type) IN ('TEMPORARY', 'PERMANENT'));

WITH latest_deferral AS (
    SELECT DISTINCT ON (ddh.donor_id)
        ddh.donor_id,
        dr.lock_type
    FROM public.donor_deferral_history ddh
    JOIN public.deferral_reason dr ON dr.reason_id = ddh.reason_id
    ORDER BY ddh.donor_id, ddh.date_recorded DESC, ddh.reason_id DESC
)
UPDATE public.donor d
SET permanent_deferral = TRUE,
    deferral_expiry_date = NULL
FROM latest_deferral latest
WHERE latest.donor_id = d.donor_id
  AND UPPER(latest.lock_type) = 'PERMANENT';

CREATE INDEX IF NOT EXISTS idx_donor_permanent_deferral
    ON public.donor (permanent_deferral);

DROP FUNCTION IF EXISTS public.fn_get_deferral_rules();
CREATE FUNCTION public.fn_get_deferral_rules()
RETURNS TABLE(
    reason_id bigint,
    description character varying,
    default_cooling_period_days integer,
    lock_type character varying,
    staff_id bigint,
    is_active boolean
)
    LANGUAGE sql
    AS $$
    SELECT
        dr.reason_id,
        dr.description,
        dr.default_cooling_period_days,
        dr.lock_type,
        dr.staff_id,
        dr.is_active
    FROM deferral_reason dr
    ORDER BY dr.is_active DESC, dr.reason_id ASC;
$$;

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
BEGIN
    IF p_description IS NULL OR TRIM(p_description) = '' THEN
        RAISE EXCEPTION 'Description cannot be empty';
    END IF;

    IF v_lock_type NOT IN ('TEMPORARY', 'PERMANENT') THEN
        RAISE EXCEPTION 'Deferral lock type must be TEMPORARY or PERMANENT';
    END IF;

    IF p_default_cooling_period_days IS NULL OR p_default_cooling_period_days < 0 THEN
        RAISE EXCEPTION 'Cooling period must be 0 or greater';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM blood_administrator ba WHERE ba.staff_id = p_staff_id
    ) THEN
        RAISE EXCEPTION 'Only Blood Administrator can create deferral rules';
    END IF;

    INSERT INTO deferral_reason (description, default_cooling_period_days, lock_type, staff_id)
    VALUES (TRIM(p_description), p_default_cooling_period_days, v_lock_type, p_staff_id);
END;
$$;
