CREATE UNIQUE INDEX IF NOT EXISTS ux_storage_location_description_normalized
    ON public.storage_location (LOWER(BTRIM(description)));

CREATE OR REPLACE PROCEDURE public.sp_add_storage_location(IN p_description character varying, IN p_staff_id bigint)
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF p_description IS NULL OR TRIM(p_description) = '' THEN
        RAISE EXCEPTION 'Storage description cannot be empty';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.storage_location
        WHERE LOWER(BTRIM(description)) = LOWER(BTRIM(p_description))
    ) THEN
        RAISE EXCEPTION 'Storage location name already exists.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM public.blood_administrator ba
        WHERE ba.staff_id = p_staff_id
    ) THEN
        RAISE EXCEPTION 'Only Blood Administrator can create storage locations';
    END IF;

    INSERT INTO public.storage_location (description, staff_id)
    VALUES (TRIM(p_description), p_staff_id);
END;
$$;
