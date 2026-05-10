-- Seed the minimum reference data required for a usable clean database.

INSERT INTO public.staff (
    staff_type,
    full_name,
    username,
    password,
    phone_no,
    ic_number,
    gender,
    email,
    profile_photo,
    is_active,
    is_locked,
    created_by,
    created_at,
    updated_at
)
VALUES (
    'BLOOD_ADMINISTRATOR',
    'System Administrator',
    'admin',
    '$2a$10$gvxGGehUpTk5u9.8/KXbGeW0bRpZ068TwcbZpSbiEvGhxuUlPhOd6',
    '0123456789',
    '800101-10-9999',
    'FEMALE',
    'admin@bloodbank.my',
    'staff/default.png',
    TRUE,
    FALSE,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (username) DO NOTHING;

INSERT INTO public.blood_administrator (staff_id, department, created_by, created_at, updated_at)
SELECT staff_id, 'System Administration', 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM public.staff
WHERE username = 'admin'
ON CONFLICT (staff_id) DO NOTHING;

INSERT INTO public.system_security_policy (
    policy_key,
    session_control_enabled,
    max_concurrent_sessions,
    session_timeout_minutes,
    prevent_new_login,
    row_level_security_enabled,
    updated_at
)
VALUES ('default', TRUE, 1, 15, FALSE, TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (policy_key) DO NOTHING;

INSERT INTO public.system_setting (setting_key, setting_value, updated_at)
VALUES
    ('ui_font_scale', '1.0', CURRENT_TIMESTAMP),
    ('ui_accent_color', '#2f80ed', CURRENT_TIMESTAMP),
    ('language_code', 'en', CURRENT_TIMESTAMP)
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO public.system_backup_config (
    config_key,
    backup_directory,
    auto_backup_enabled,
    schedule_frequency,
    schedule_time,
    retention_days,
    updated_at
)
VALUES ('default', 'backups', FALSE, 'DAILY', '23:00', 30, CURRENT_TIMESTAMP)
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO public.staff_module_access (
    staff_type,
    module_key,
    module_name,
    url_pattern,
    is_enabled,
    sort_order,
    created_by,
    created_at,
    updated_at
)
VALUES
    ('BLOOD_ADMINISTRATOR', 'admin_dashboard', 'Administrator Dashboard', '/admin/dashboard', TRUE, 10, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('BLOOD_ADMINISTRATOR', 'staff_management', 'Staff Management', '/admin/staff/**', TRUE, 20, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('BLOOD_ADMINISTRATOR', 'storage_config', 'Storage Configuration', '/admin/storage/**', TRUE, 30, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('BLOOD_ADMINISTRATOR', 'deferral_rules', 'Deferral Rules', '/admin/deferral-rules/**', TRUE, 40, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('BLOOD_ADMINISTRATOR', 'inventory_monitoring', 'Inventory Monitoring', '/admin/inventory/**', TRUE, 50, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('BLOOD_ADMINISTRATOR', 'reports_alerts', 'Reports and Alerts', '/admin/reports/**', TRUE, 60, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('BLOOD_ADMINISTRATOR', 'system_settings', 'System Settings', '/admin/settings/**', TRUE, 70, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MEDICAL_STAFF', 'medical_dashboard', 'Medical Dashboard', '/medical/dashboard', TRUE, 10, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MEDICAL_STAFF', 'donor_eligibility', 'Donor Eligibility', '/medical/donor-eligibility/**', TRUE, 20, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MEDICAL_STAFF', 'blood_collection', 'Blood Collection', '/medical/donations/**', TRUE, 30, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MEDICAL_STAFF', 'transfusion_request', 'Transfusion Request', '/medical/transfusion/**', TRUE, 40, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MEDICAL_STAFF', 'safe_blood_match', 'Safe Blood Match', '/medical/components/**', TRUE, 50, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('LAB_TECHNICIAN', 'lab_dashboard', 'Lab Dashboard', '/lab/dashboard', TRUE, 10, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('LAB_TECHNICIAN', 'pending_tests', 'Pending Test Queue', '/lab/pending-tests/**', TRUE, 20, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('LAB_TECHNICIAN', 'tti_screening', 'TTI Screening', '/lab/tti-screening/**', TRUE, 30, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('LAB_TECHNICIAN', 'component_status', 'Component Status', '/lab/component-status/**', TRUE, 40, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('LAB_TECHNICIAN', 'traceability', 'Traceability', '/lab/traceability/**', TRUE, 50, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (staff_type, module_key) DO NOTHING;
