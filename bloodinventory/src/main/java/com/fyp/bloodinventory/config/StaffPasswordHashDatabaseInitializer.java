package com.fyp.bloodinventory.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class StaffPasswordHashDatabaseInitializer {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    CommandLineRunner initializeStaffPasswordHashDatabaseObjects(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");

            jdbcTemplate.execute("""
                    CREATE OR REPLACE FUNCTION fn_normalize_staff_password(
                        IN p_password VARCHAR
                    )
                    RETURNS VARCHAR
                    LANGUAGE plpgsql
                    AS $$
                    DECLARE
                        v_password VARCHAR := NULLIF(TRIM(p_password), '');
                    BEGIN
                        IF v_password IS NULL THEN
                            RETURN NULL;
                        END IF;

                        IF LOWER(LEFT(v_password, 8)) = '{bcrypt}' THEN
                            v_password := SUBSTRING(v_password FROM 9);
                        END IF;

                        RETURN NULLIF(TRIM(v_password), '');
                    END;
                    $$;
                    """);

            jdbcTemplate.execute("""
                    CREATE OR REPLACE FUNCTION fn_is_bcrypt_password(
                        IN p_password VARCHAR
                    )
                    RETURNS BOOLEAN
                    LANGUAGE plpgsql
                    AS $$
                    DECLARE
                        v_password VARCHAR := fn_normalize_staff_password(p_password);
                    BEGIN
                        RETURN v_password IS NOT NULL
                           AND v_password ~ '^\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}$';
                    END;
                    $$;
                    """);

            jdbcTemplate.execute("""
                    CREATE OR REPLACE FUNCTION fn_hash_staff_password(
                        IN p_password VARCHAR
                    )
                    RETURNS VARCHAR
                    LANGUAGE plpgsql
                    AS $$
                    DECLARE
                        v_password VARCHAR := fn_normalize_staff_password(p_password);
                    BEGIN
                        IF v_password IS NULL THEN
                            RETURN NULL;
                        END IF;

                        IF fn_is_bcrypt_password(v_password) THEN
                            RETURN v_password;
                        END IF;

                        RETURN crypt(v_password, gen_salt('bf', 10));
                    END;
                    $$;
                    """);

            jdbcTemplate.execute("""
                    CREATE OR REPLACE FUNCTION fn_hash_staff_password_before_save()
                    RETURNS TRIGGER
                    LANGUAGE plpgsql
                    AS $$
                    BEGIN
                        IF TG_OP = 'INSERT'
                           OR NEW.password IS DISTINCT FROM OLD.password THEN
                            NEW.password := fn_hash_staff_password(NEW.password);
                        END IF;

                        RETURN NEW;
                    END;
                    $$;
                    """);

            jdbcTemplate.execute("""
                    DROP TRIGGER IF EXISTS trg_hash_staff_password_before_save ON staff
                    """);

            jdbcTemplate.execute("""
                    CREATE TRIGGER trg_hash_staff_password_before_save
                    BEFORE INSERT OR UPDATE OF password ON staff
                    FOR EACH ROW
                    EXECUTE FUNCTION fn_hash_staff_password_before_save()
                    """);

            jdbcTemplate.execute("""
                    UPDATE staff
                    SET password = fn_hash_staff_password(password)
                    WHERE password IS NOT NULL
                    """);
        };
    }
}
