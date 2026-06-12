-- Normalize legacy staff photo placeholders so they render as the empty avatar.

UPDATE public.staff
SET profile_photo = 'staff/default.png'
WHERE profile_photo = 'default.png';

ALTER TABLE public.staff
ALTER COLUMN profile_photo SET DEFAULT 'staff/default.png';
