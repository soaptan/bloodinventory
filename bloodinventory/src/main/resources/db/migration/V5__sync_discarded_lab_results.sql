UPDATE public.lab_test lt
SET final_status = 'FAILED',
    test_date = CURRENT_TIMESTAMP
WHERE EXISTS (
    SELECT 1
    FROM public.blood_component bc
    WHERE bc.donation_id = lt.donation_id
      AND UPPER(bc.status) = 'DISCARDED'
)
AND UPPER(lt.final_status) <> 'FAILED';
