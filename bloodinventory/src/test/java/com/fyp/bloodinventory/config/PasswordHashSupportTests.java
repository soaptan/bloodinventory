package com.fyp.bloodinventory.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHashSupportTests {

    private static final String BCRYPT_HASH =
            "$2a$10$Wgc1WeoVbNuWnAlKXGRPs.BtIBkcZupIXTeUEIlvxLmf332bIb/4e";

    @Test
    void detectsValidBcryptHash() {
        assertThat(PasswordHashSupport.isBcryptHash(BCRYPT_HASH)).isTrue();
    }

    @Test
    void normalizesWhitespaceAndDelegatingBcryptPrefix() {
        assertThat(PasswordHashSupport.normalizeStoredPassword("  {bcrypt}" + BCRYPT_HASH + "  "))
                .isEqualTo(BCRYPT_HASH);
    }

    @Test
    void rejectsPlainPasswordAsHash() {
        assertThat(PasswordHashSupport.isBcryptHash("medical123")).isFalse();
    }
}
