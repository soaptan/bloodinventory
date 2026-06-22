CREATE TABLE IF NOT EXISTS public.lab_test (
    test_id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    tti_screening character varying(30) NOT NULL,
    blood_type_match character varying(20) NOT NULL,
    final_status character varying(20) NOT NULL,
    test_date date NOT NULL,
    staff_id bigint NOT NULL,
    donation_id bigint NOT NULL
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'lab_test'
          AND column_name = 'test_id'
          AND is_identity = 'YES'
    ) THEN
        ALTER TABLE public.lab_test
            ALTER COLUMN test_id ADD GENERATED ALWAYS AS IDENTITY;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace
          AND conname = 'chk_labtest_blood_type_match'
    ) THEN
        ALTER TABLE public.lab_test
            ADD CONSTRAINT chk_labtest_blood_type_match
            CHECK (((blood_type_match)::text = ANY ((ARRAY['MATCHED'::character varying, 'NOT_MATCHED'::character varying, 'PENDING'::character varying])::text[])));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace
          AND conname = 'chk_labtest_final_status'
    ) THEN
        ALTER TABLE public.lab_test
            ADD CONSTRAINT chk_labtest_final_status
            CHECK (((final_status)::text = ANY ((ARRAY['PASSED'::character varying, 'FAILED'::character varying, 'QUARANTINED'::character varying])::text[])));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace
          AND conname = 'lab_test_pkey'
    ) THEN
        ALTER TABLE ONLY public.lab_test
            ADD CONSTRAINT lab_test_pkey PRIMARY KEY (test_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace
          AND conname = 'lab_test_donation_id_key'
    ) THEN
        ALTER TABLE ONLY public.lab_test
            ADD CONSTRAINT lab_test_donation_id_key UNIQUE (donation_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace
          AND conname = 'fk_labtest_donation'
    ) THEN
        ALTER TABLE ONLY public.lab_test
            ADD CONSTRAINT fk_labtest_donation FOREIGN KEY (donation_id) REFERENCES public.donation(donation_id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace
          AND conname = 'fk_labtest_staff'
    ) THEN
        ALTER TABLE ONLY public.lab_test
            ADD CONSTRAINT fk_labtest_staff FOREIGN KEY (staff_id) REFERENCES public.lab_technician(staff_id) ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_lab_test_staff_id ON public.lab_test USING btree (staff_id);
