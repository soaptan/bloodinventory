package com.fyp.bloodinventory.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DashboardDatabaseInitializer {

    @Bean
    CommandLineRunner initializeDashboardDatabaseObjects(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("""
                    CREATE OR REPLACE FUNCTION fn_dashboard_summary_metrics()
                    RETURNS TABLE (
                        metric_key VARCHAR,
                        metric_label VARCHAR,
                        metric_value BIGINT,
                        metric_note VARCHAR,
                        metric_color VARCHAR,
                        display_order INTEGER
                    )
                    LANGUAGE plpgsql
                    AS $$
                    BEGIN
                        RETURN QUERY
                        SELECT
                            metrics.metric_key,
                            metrics.metric_label,
                            metrics.metric_value,
                            metrics.metric_note,
                            metrics.metric_color,
                            metrics.display_order
                        FROM (
                            SELECT
                                'total_staff'::VARCHAR AS metric_key,
                                'Total Staff'::VARCHAR AS metric_label,
                                (SELECT COUNT(*) FROM staff)::BIGINT AS metric_value,
                                'Authorized personnel registered in the system.'::VARCHAR AS metric_note,
                                '#3e8cff'::VARCHAR AS metric_color,
                                1 AS display_order
                            UNION ALL
                            SELECT
                                'total_donors'::VARCHAR,
                                'Total Donors'::VARCHAR,
                                (SELECT COUNT(*) FROM donor)::BIGINT,
                                'Donor records available for review and processing.'::VARCHAR,
                                '#14b8d5'::VARCHAR,
                                2
                            UNION ALL
                            SELECT
                                'available_components'::VARCHAR,
                                'Available Components'::VARCHAR,
                                (SELECT COUNT(*) FROM blood_component WHERE UPPER(status) = 'AVAILABLE')::BIGINT,
                                'Blood components ready for controlled use.'::VARCHAR,
                                '#5bc784'::VARCHAR,
                                3
                            UNION ALL
                            SELECT
                                'total_donations'::VARCHAR,
                                'Total Donations'::VARCHAR,
                                (SELECT COUNT(*) FROM donation)::BIGINT,
                                'Donation events captured in the current inventory system.'::VARCHAR,
                                '#f4ae3f'::VARCHAR,
                                4
                            UNION ALL
                            SELECT
                                'near_expiry'::VARCHAR,
                                'Near Expiry'::VARCHAR,
                                (
                                    SELECT COUNT(*)
                                    FROM blood_component
                                    WHERE expiry_timestamp <= CURRENT_TIMESTAMP + INTERVAL '3 days'
                                )::BIGINT,
                                'Components reaching expiry within the next 3 days.'::VARCHAR,
                                '#ff667d'::VARCHAR,
                                5
                            UNION ALL
                            SELECT
                                'total_components'::VARCHAR,
                                'Total Components'::VARCHAR,
                                (SELECT COUNT(*) FROM blood_component)::BIGINT,
                                'Total blood components under system supervision.'::VARCHAR,
                                '#386bbc'::VARCHAR,
                                6
                        ) metrics
                        ORDER BY metrics.display_order;
                    END;
                    $$;
                    """);

            jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION fn_dashboard_system_overview(
                    IN p_days INTEGER DEFAULT 14,
                    IN p_metric VARCHAR DEFAULT 'all',
                    IN p_sort VARCHAR DEFAULT 'date_asc'
                )
                RETURNS TABLE (
                    activity_date DATE,
                    donation_count BIGINT,
                    component_count BIGINT,
                    available_count BIGINT,
                    near_expiry_count BIGINT,
                    selected_total BIGINT
                )
                LANGUAGE plpgsql
                AS $$
                DECLARE
                    v_days INTEGER := LEAST(GREATEST(COALESCE(p_days, 14), 7), 90);
                    v_metric TEXT := LOWER(COALESCE(p_metric, 'all'));
                    v_sort TEXT := LOWER(COALESCE(p_sort, 'date_asc'));
                BEGIN
                    RETURN QUERY
                    WITH timeline AS (
                        SELECT generate_series(
                            CURRENT_DATE - ((v_days - 1) * INTERVAL '1 day'),
                            CURRENT_DATE,
                            INTERVAL '1 day'
                        )::DATE AS activity_date
                    ),
                    day_counts AS (
                        SELECT
                            t.activity_date,
                            (
                                SELECT COUNT(*)
                                FROM donation d
                                WHERE d.collection_timestamp::DATE = t.activity_date
                            ) AS donation_count,
                            (
                                SELECT COUNT(*)
                                FROM blood_component bc
                                JOIN donation d ON d.donation_id = bc.donation_id
                                WHERE d.collection_timestamp::DATE = t.activity_date
                            ) AS component_count,
                            (
                                SELECT COUNT(*)
                                FROM blood_component bc
                                JOIN donation d ON d.donation_id = bc.donation_id
                                WHERE d.collection_timestamp::DATE = t.activity_date
                                  AND UPPER(bc.status) = 'AVAILABLE'
                            ) AS available_count,
                            (
                                SELECT COUNT(*)
                                FROM blood_component bc
                                WHERE bc.expiry_timestamp::DATE = t.activity_date
                            ) AS near_expiry_count
                        FROM timeline t
                    ),
                    scored AS (
                        SELECT
                            dc.activity_date,
                            dc.donation_count,
                            dc.component_count,
                            dc.available_count,
                            dc.near_expiry_count,
                            CASE v_metric
                                WHEN 'donations' THEN dc.donation_count
                                WHEN 'components' THEN dc.component_count
                                WHEN 'available' THEN dc.available_count
                                WHEN 'near_expiry' THEN dc.near_expiry_count
                                ELSE dc.donation_count + dc.component_count + dc.available_count + dc.near_expiry_count
                            END AS selected_total
                        FROM day_counts dc
                    )
                    SELECT
                        s.activity_date,
                        s.donation_count,
                        s.component_count,
                        s.available_count,
                        s.near_expiry_count,
                        s.selected_total
                    FROM scored s
                    ORDER BY
                        CASE WHEN v_sort = 'date_desc' THEN s.activity_date END DESC,
                        CASE WHEN v_sort = 'value_desc' THEN s.selected_total END DESC,
                        CASE WHEN v_sort = 'value_asc' THEN s.selected_total END ASC,
                        s.activity_date ASC;
                END;
                $$;
                """);
        };
    }
}
