SELECT setval(
    pg_get_serial_sequence('public.blood_component', 'component_id'),
    GREATEST((SELECT COALESCE(MAX(component_id), 0) + 1 FROM public.blood_component), 1),
    false
);

SELECT setval(
    pg_get_serial_sequence('public.deferral_reason', 'reason_id'),
    GREATEST((SELECT COALESCE(MAX(reason_id), 0) + 1 FROM public.deferral_reason), 1),
    false
);

SELECT setval(
    pg_get_serial_sequence('public.donation', 'donation_id'),
    GREATEST((SELECT COALESCE(MAX(donation_id), 0) + 1 FROM public.donation), 1),
    false
);

SELECT setval(
    pg_get_serial_sequence('public.donor', 'donor_id'),
    GREATEST((SELECT COALESCE(MAX(donor_id), 0) + 1 FROM public.donor), 1),
    false
);

SELECT setval(
    pg_get_serial_sequence('public.lab_test', 'test_id'),
    GREATEST((SELECT COALESCE(MAX(test_id), 0) + 1 FROM public.lab_test), 1),
    false
);

SELECT setval(
    pg_get_serial_sequence('public.patient', 'patient_id'),
    GREATEST((SELECT COALESCE(MAX(patient_id), 0) + 1 FROM public.patient), 1),
    false
);

SELECT setval(
    pg_get_serial_sequence('public.staff', 'staff_id'),
    GREATEST((SELECT COALESCE(MAX(staff_id), 0) + 1 FROM public.staff), 1),
    false
);

SELECT setval(
    pg_get_serial_sequence('public.staff_module_access', 'access_id'),
    GREATEST((SELECT COALESCE(MAX(access_id), 0) + 1 FROM public.staff_module_access), 1),
    false
);

SELECT setval(
    pg_get_serial_sequence('public.storage_location', 'location_id'),
    GREATEST((SELECT COALESCE(MAX(location_id), 0) + 1 FROM public.storage_location), 1),
    false
);
