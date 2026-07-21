package com.fyp.bloodinventory.dto;

public class PasswordResetRequestResult {

    private final String message;
    private final boolean emailSent;
    private final String maskedEmail;

    public PasswordResetRequestResult(String message, boolean emailSent, String maskedEmail) {
        this.message = message;
        this.emailSent = emailSent;
        this.maskedEmail = maskedEmail;
    }

    public String getMessage() {
        return message;
    }

    public boolean isEmailSent() {
        return emailSent;
    }

    public String getMaskedEmail() {
        return maskedEmail;
    }
}
