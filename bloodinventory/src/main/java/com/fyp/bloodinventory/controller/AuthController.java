package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.dto.PasswordResetRequestResult;
import com.fyp.bloodinventory.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Controller
public class AuthController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");
    private static final Pattern IC_NUMBER_PATTERN = Pattern.compile("^\\d{6}-?\\d{2}-?\\d{4}$");

    private final JdbcTemplate jdbcTemplate;
    private final PasswordResetService passwordResetService;

    public AuthController(JdbcTemplate jdbcTemplate,
                          PasswordResetService passwordResetService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/login")
    public String loginPage(HttpServletRequest request) {
        // Ensure the session exists before Thymeleaf reaches the form tag.
        request.getSession();
        return "login";
    }

    @GetMapping("/forgot-username")
    public String forgotUsernamePage(HttpServletRequest request) {
        request.getSession();
        return "forgot-username";
    }

    @PostMapping("/forgot-username")
    public String recoverUsername(@RequestParam("email") String email,
                                  @RequestParam("icNumber") String icNumber,
                                  HttpServletRequest request,
                                  Model model) {
        request.getSession();

        String normalizedEmail = normalize(email);
        String normalizedIcNumber = normalize(icNumber);
        Map<String, String> fieldErrors = validateRecoveryIdentity(null, normalizedEmail, normalizedIcNumber, false);
        if (!fieldErrors.isEmpty()) {
            addValidationErrors(model, fieldErrors);
            model.addAttribute("email", normalizedEmail);
            model.addAttribute("icNumber", normalizedIcNumber);
            return "forgot-username";
        }

        try {
            var usernames = jdbcTemplate.queryForList("""
                    SELECT username
                    FROM staff
                    WHERE LOWER(email) = LOWER(?)
                      AND ic_number = ?
                      AND is_active = TRUE
                    ORDER BY staff_id ASC
                    """, String.class, normalizedEmail, normalizedIcNumber);

            if (usernames.isEmpty()) {
                model.addAttribute("errorMessage", "No active staff account matched those details.");
            } else {
                model.addAttribute("successMessage", "Your username is: " + usernames.get(0));
            }
        } catch (RuntimeException ex) {
            model.addAttribute("errorMessage", "We could not look up the account right now. Please try again later.");
        }

        model.addAttribute("email", normalizedEmail);
        model.addAttribute("icNumber", normalizedIcNumber);
        return "forgot-username";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(HttpServletRequest request) {
        request.getSession();
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestPasswordReset(@RequestParam("username") String username,
                                       @RequestParam("email") String email,
                                       @RequestParam("icNumber") String icNumber,
                                       HttpServletRequest request,
                                       Model model) {
        request.getSession();

        String normalizedUsername = normalize(username);
        String normalizedEmail = normalize(email);
        String normalizedIcNumber = normalize(icNumber);

        Map<String, String> fieldErrors = validateRecoveryIdentity(
                normalizedUsername, normalizedEmail, normalizedIcNumber, true);
        if (!fieldErrors.isEmpty()) {
            addValidationErrors(model, fieldErrors);
            preservePasswordResetInputs(model, normalizedUsername, normalizedEmail, normalizedIcNumber);
            return "forgot-password";
        }

        try {
            PasswordResetRequestResult result = passwordResetService.requestResetForIdentity(
                    normalizedUsername,
                    normalizedEmail,
                    normalizedIcNumber,
                    sourceIp(request)
            );
            model.addAttribute("successMessage", result.getMessage());
            model.addAttribute("codeRequested", true);
            model.addAttribute("maskedEmail", result.getMaskedEmail());
        } catch (RuntimeException e) {
            model.addAttribute("errorMessage", safeErrorMessage(e));
            preservePasswordResetInputs(model, normalizedUsername, normalizedEmail, normalizedIcNumber);
            return "forgot-password";
        }

        preservePasswordResetInputs(model, normalizedUsername, normalizedEmail, normalizedIcNumber);
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(HttpServletRequest request) {
        request.getSession();
        return "redirect:/forgot-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam("username") String username,
                                @RequestParam("email") String email,
                                @RequestParam("icNumber") String icNumber,
                                @RequestParam("verificationCode") String verificationCode,
                                @RequestParam("newPassword") String newPassword,
                                @RequestParam("confirmPassword") String confirmPassword,
                                HttpServletRequest request,
                                Model model) {
        request.getSession();
        String normalizedUsername = normalize(username);
        String normalizedEmail = normalize(email);
        String normalizedIcNumber = normalize(icNumber);

        Map<String, String> fieldErrors = validateRecoveryIdentity(
                normalizedUsername, normalizedEmail, normalizedIcNumber, true);
        validateResetFields(fieldErrors, verificationCode, newPassword, confirmPassword);
        if (!fieldErrors.isEmpty()) {
            addValidationErrors(model, fieldErrors);
            model.addAttribute("codeRequested", true);
            model.addAttribute("verificationCode", normalize(verificationCode));
            preservePasswordResetInputs(model, normalizedUsername, normalizedEmail, normalizedIcNumber);
            return "forgot-password";
        }

        try {
            passwordResetService.resetPasswordWithCode(
                    normalizedUsername,
                    normalizedEmail,
                    normalizedIcNumber,
                    verificationCode,
                    newPassword,
                    confirmPassword
            );
            model.addAttribute("successMessage", "Password reset successfully. You can sign in with the new password.");
        } catch (RuntimeException e) {
            model.addAttribute("errorMessage", safeErrorMessage(e));
            model.addAttribute("codeRequested", true);
            model.addAttribute("verificationCode", normalize(verificationCode));
            preservePasswordResetInputs(model, normalizedUsername, normalizedEmail, normalizedIcNumber);
        }

        return "forgot-password";
    }

    private void preservePasswordResetInputs(Model model, String username, String email, String icNumber) {
        model.addAttribute("username", username);
        model.addAttribute("email", email);
        model.addAttribute("icNumber", icNumber);
    }

    private Map<String, String> validateRecoveryIdentity(String username, String email,
                                                          String icNumber, boolean usernameRequired) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (usernameRequired) {
            if (username == null) {
                errors.put("username", "Username is required.");
            } else if (!USERNAME_PATTERN.matcher(username).matches()) {
                errors.put("username", "Use 3-50 letters, numbers, dots, underscores, or hyphens.");
            }
        }
        if (email == null) {
            errors.put("email", "Registered email is required.");
        } else if (email.length() > 100 || !EMAIL_PATTERN.matcher(email).matches()) {
            errors.put("email", "Enter a valid email address (maximum 100 characters).");
        }
        if (icNumber == null) {
            errors.put("icNumber", "IC number is required.");
        } else if (!IC_NUMBER_PATTERN.matcher(icNumber).matches()) {
            errors.put("icNumber", "Enter a 12-digit IC number, for example 850101-10-2001.");
        }
        return errors;
    }

    private void validateResetFields(Map<String, String> errors, String code,
                                     String password, String confirmation) {
        String normalizedCode = normalize(code);
        if (normalizedCode == null || !normalizedCode.matches("\\d{6}")) {
            errors.put("verificationCode", "Enter the 6-digit verification code.");
        }
        if (password == null || password.length() < 8 || password.length() > 72) {
            errors.put("newPassword", "Password must be between 8 and 72 characters.");
        }
        if (confirmation == null || confirmation.isEmpty()) {
            errors.put("confirmPassword", "Please confirm the new password.");
        } else if (password != null && !password.equals(confirmation)) {
            errors.put("confirmPassword", "Passwords do not match.");
        }
    }

    private void addValidationErrors(Model model, Map<String, String> errors) {
        model.addAttribute("fieldErrors", errors);
        model.addAttribute("errorMessage", "Please correct the highlighted fields.");
    }

    private String safeErrorMessage(RuntimeException exception) {
        String message = normalize(exception.getMessage());
        return message == null ? "The request could not be completed. Please try again." : message;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String sourceIp(HttpServletRequest request) {
        String forwardedFor = normalize(request.getHeader("X-Forwarded-For"));
        if (forwardedFor != null) {
            int commaIndex = forwardedFor.indexOf(',');
            return commaIndex >= 0 ? forwardedFor.substring(0, commaIndex).trim() : forwardedFor;
        }

        return request.getRemoteAddr();
    }
}
