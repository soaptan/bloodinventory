package com.fyp.bloodinventory.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class StaffAccountDatabaseInitializer {

    @Bean
    CommandLineRunner initializeStaffAccountDatabaseObjects(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("""
                    ALTER TABLE staff
                    ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE staff
                    ADD COLUMN IF NOT EXISTS is_locked BOOLEAN DEFAULT FALSE
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE staff
                    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMP
                    """);

            jdbcTemplate.execute("""
                    UPDATE staff
                    SET is_active = TRUE
                    WHERE is_active IS NULL
                    """);

            jdbcTemplate.execute("""
                    UPDATE staff
                    SET is_locked = FALSE
                    WHERE is_locked IS NULL
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE staff
                    ALTER COLUMN is_active SET DEFAULT TRUE
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE staff
                    ALTER COLUMN is_active SET NOT NULL
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE staff
                    ALTER COLUMN is_locked SET DEFAULT FALSE
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE staff
                    ALTER COLUMN is_locked SET NOT NULL
                    """);

            jdbcTemplate.execute("""
                    CREATE OR REPLACE FUNCTION fn_sync_staff_lock_status()
                    RETURNS TRIGGER
                    LANGUAGE plpgsql
                    AS $$
                    BEGIN
                        IF COALESCE(NEW.is_active, TRUE) = FALSE THEN
                            NEW.is_locked := TRUE;

                            IF TG_OP = 'INSERT'
                               OR COALESCE(OLD.is_active, TRUE) <> COALESCE(NEW.is_active, TRUE)
                               OR NEW.locked_at IS NULL THEN
                                NEW.locked_at := CURRENT_TIMESTAMP;
                            END IF;
                        ELSE
                            NEW.is_locked := FALSE;
                            NEW.locked_at := NULL;
                        END IF;

                        RETURN NEW;
                    END;
                    $$;
                    """);

            jdbcTemplate.execute("""
                    DROP TRIGGER IF EXISTS trg_sync_staff_lock_status ON staff
                    """);

            jdbcTemplate.execute("""
                    CREATE TRIGGER trg_sync_staff_lock_status
                    BEFORE INSERT OR UPDATE OF is_active ON staff
                    FOR EACH ROW
                    EXECUTE FUNCTION fn_sync_staff_lock_status()
                    """);

            jdbcTemplate.execute("""
                    UPDATE staff
                    SET is_locked = TRUE,
                        locked_at = COALESCE(locked_at, CURRENT_TIMESTAMP)
                    WHERE is_active = FALSE
                    """);

            jdbcTemplate.execute("""
                    UPDATE staff
                    SET is_locked = FALSE,
                        locked_at = NULL
                    WHERE is_active = TRUE
                    """);

            jdbcTemplate.execute("""
                    CREATE OR REPLACE PROCEDURE sp_set_staff_account_status(
                        IN p_staff_id BIGINT,
                        IN p_is_active BOOLEAN
                    )
                    LANGUAGE plpgsql
                    AS $$
                    BEGIN
                        UPDATE staff
                        SET is_active = COALESCE(p_is_active, FALSE)
                        WHERE staff_id = p_staff_id;

                        IF NOT FOUND THEN
                            RAISE EXCEPTION 'Staff account with ID % was not found.', p_staff_id;
                        END IF;
                    END;
                    $$;
                    """);
        };
    }
}
