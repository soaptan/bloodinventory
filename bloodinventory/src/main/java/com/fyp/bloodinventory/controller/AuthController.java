package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.config.PasswordPolicy;
import com.fyp.bloodinventory.service.AuditEventService;
import com.fyp.bloodinventory.service.LoginAttemptService;
import com.fyp.bloodinventory.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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
    private static final String PASSWORD_RESET_STAFF_ID = "passwordResetStaffId";
    private static final String PASSWORD_RESET_VERIFIED_AT = "passwordResetVerifiedAt";
    private static final long PASSWORD_RESET_VERIFICATION_TTL_MILLIS = 600_000L;
    private static final String GENERIC_PASSWORD_RECOVERY_ERROR =
            "The request could not be completed. Please try again later.";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordResetService passwordResetService;
    private final LoginAttemptService loginAttemptService;
    private final AuditEventService auditEventService;

    public AuthController(JdbcTemplate jdbcTemplate,
                          PasswordResetService passwordResetService,
                          LoginAttemptService loginAttemptService,
                          AuditEventService auditEventService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordResetService = passwordResetService;
        this.loginAttemptService = loginAttemptService;
        this.auditEventService = auditEventService;
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
    public String forgotPasswordPage(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession();
        clearPasswordResetVerification(session);
        if (request.getParameterMap().containsKey("verificationRequired")) {
            model.addAttribute(
                    "errorMessage",
                    "Verify your account details before choosing a new password."
            );
        }
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestPasswordReset(@RequestParam("username") String username,
                                       @RequestParam("email") String email,
                                       @RequestParam("icNumber") String icNumber,
                                       HttpServletRequest request,
                                       HttpServletResponse response,
                                       Model model) {
        HttpSession session = request.getSession();
        clearPasswordResetVerification(session);

        String normalizedUsername = normalize(username);
        String normalizedEmail = normalize(email);
        String normalizedIcNumber = normalize(icNumber);
        String sourceAddress = sourceIp(request);

        if (loginAttemptService.isPasswordRecoveryBlocked(normalizedUsername, sourceAddress)) {
            markPasswordRecoveryThrottled(response);
            auditEventService.recordPasswordRecoveryAttempt(
                    request, normalizedUsername, "PASSWORD_RECOVERY_THROTTLED", false);
            model.addAttribute("errorMessage", GENERIC_PASSWORD_RECOVERY_ERROR);
            preservePasswordResetInputs(model, normalizedUsername, normalizedEmail, normalizedIcNumber);
            return "forgot-password";
        }

        Map<String, String> fieldErrors = validateRecoveryIdentity(
                normalizedUsername, normalizedEmail, normalizedIcNumber, true);
        if (!fieldErrors.isEmpty()) {
            boolean blocked = loginAttemptService.recordRejectedPasswordRecoveryInput(sourceAddress);
            auditEventService.recordPasswordRecoveryAttempt(
                    request, normalizedUsername, "INVALID_PASSWORD_RECOVERY_INPUT", false);
            if (blocked) {
                markPasswordRecoveryThrottled(response);
                model.addAttribute("errorMessage", GENERIC_PASSWORD_RECOVERY_ERROR);
                preservePasswordResetInputs(model, normalizedUsername, normalizedEmail, normalizedIcNumber);
                return "forgot-password";
            }
            addValidationErrors(model, fieldErrors);
            preservePasswordResetInputs(model, normalizedUsername, normalizedEmail, normalizedIcNumber);
            return "forgot-password";
        }

        try {
            Long staffId = passwordResetService.verifyIdentity(
                    normalizedUsername, normalizedEmail, normalizedIcNumber).orElse(null);
            if (staffId == null) {
                boolean blocked = loginAttemptService.recordPasswordRecoveryFailure(
                        normalizedUsername, sourceAddress);
                auditEventService.recordPasswordRecoveryAttempt(
                        request, normalizedUsername, "IDENTITY_MISMATCH", false);
                if (blocked) {
                    markPasswordRecoveryThrottled(response);
                }
                model.addAttribute("errorMessage", GENERIC_PASSWORD_RECOVERY_ERROR);
                preservePasswordResetInputs(model, normalizedUsername, normalizedEmail, normalizedIcNumber);
                return "forgot-password";
            }

            loginAttemptService.recordPasswordRecoverySuccess(normalizedUsername, sourceAddress);
            auditEventService.recordPasswordRecoveryAttempt(
                    request, normalizedUsername, "IDENTITY_VERIFIED", true);
            request.changeSessionId();
            storePasswordResetApproval(session, staffId, System.currentTimeMillis());
            return "redirect:/reset-password";
        } catch (RuntimeException e) {
            auditEventService.recordPasswordRecoveryAttempt(
                    request, normalizedUsername, "PASSWORD_RECOVERY_ERROR", false);
            model.addAttribute("errorMessage", GENERIC_PASSWORD_RECOVERY_ERROR);
            preservePasswordResetInputs(model, normalizedUsername, normalizedEmail, normalizedIcNumber);
            return "forgot-password";
        }
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(HttpServletRequest request) {
        if (verifiedPasswordResetStaffId(request.getSession(false)) == null) {
            return "redirect:/forgot-password?verificationRequired";
        }
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam(value = "newPassword", required = false) String newPassword,
                                @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
                                HttpServletRequest request,
                                Model model) {
        HttpSession session = request.getSession(false);
        if (verifiedPasswordResetApproval(session) == null) {
            return "redirect:/forgot-password?verificationRequired";
        }

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        validateResetFields(fieldErrors, newPassword, confirmPassword);
        if (!fieldErrors.isEmpty()) {
            addValidationErrors(model, fieldErrors);
            return "reset-password";
        }

        PasswordResetApproval approval = claimPasswordResetApproval(session);
        if (approval == null) {
            return "redirect:/forgot-password?verificationRequired";
        }

        try {
            passwordResetService.resetPasswordForVerifiedStaff(
                    approval.staffId(), newPassword, confirmPassword, sourceIp(request));
            model.addAttribute(
                    "successMessage",
                    "Password reset successfully. You can sign in with the new password."
            );
        } catch (RuntimeException exception) {
            restorePasswordResetApproval(session, approval);
            model.addAttribute("errorMessage", safeErrorMessage(exception));
        }
        return "reset-password";
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

    private void validateResetFields(Map<String, String> errors,
                                     String password,
                                     String confirmation) {
        try {
            PasswordPolicy.requireStrongPassword(password);
        } catch (RuntimeException exception) {
            errors.put("newPassword", exception.getMessage());
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

    private Long verifiedPasswordResetStaffId(HttpSession session) {
        PasswordResetApproval approval = verifiedPasswordResetApproval(session);
        return approval == null ? null : approval.staffId();
    }

    private PasswordResetApproval verifiedPasswordResetApproval(HttpSession session) {
        if (session == null) {
            return null;
        }

        synchronized (session) {
            return passwordResetApprovalWithinLock(session);
        }
    }

    private PasswordResetApproval claimPasswordResetApproval(HttpSession session) {
        if (session == null) {
            return null;
        }

        synchronized (session) {
            PasswordResetApproval approval = passwordResetApprovalWithinLock(session);
            if (approval != null) {
                clearPasswordResetVerificationWithinLock(session);
            }
            return approval;
        }
    }

    private void restorePasswordResetApproval(HttpSession session, PasswordResetApproval approval) {
        if (session == null || approval == null || !isFreshPasswordResetApproval(approval)) {
            return;
        }

        synchronized (session) {
            if (session.getAttribute(PASSWORD_RESET_STAFF_ID) == null
                    && session.getAttribute(PASSWORD_RESET_VERIFIED_AT) == null) {
                storePasswordResetApprovalWithinLock(
                        session, approval.staffId(), approval.verifiedAt());
            }
        }
    }

    private void storePasswordResetApproval(HttpSession session, Long staffId, long verifiedAt) {
        synchronized (session) {
            storePasswordResetApprovalWithinLock(session, staffId, verifiedAt);
        }
    }

    private void storePasswordResetApprovalWithinLock(HttpSession session, Long staffId, long verifiedAt) {
        session.setAttribute(PASSWORD_RESET_STAFF_ID, staffId);
        session.setAttribute(PASSWORD_RESET_VERIFIED_AT, verifiedAt);
    }

    private PasswordResetApproval passwordResetApprovalWithinLock(HttpSession session) {
        Object staffIdValue = session.getAttribute(PASSWORD_RESET_STAFF_ID);
        Object verifiedAtValue = session.getAttribute(PASSWORD_RESET_VERIFIED_AT);
        if (!(staffIdValue instanceof Number staffId)
                || !(verifiedAtValue instanceof Number verifiedAt)) {
            clearPasswordResetVerificationWithinLock(session);
            return null;
        }

        PasswordResetApproval approval = new PasswordResetApproval(
                staffId.longValue(), verifiedAt.longValue());
        if (!isFreshPasswordResetApproval(approval)) {
            clearPasswordResetVerificationWithinLock(session);
            return null;
        }
        return approval;
    }

    private boolean isFreshPasswordResetApproval(PasswordResetApproval approval) {
        long ageMillis = System.currentTimeMillis() - approval.verifiedAt();
        return approval.staffId() > 0
                && ageMillis >= 0
                && ageMillis <= PASSWORD_RESET_VERIFICATION_TTL_MILLIS;
    }

    private void clearPasswordResetVerification(HttpSession session) {
        if (session == null) {
            return;
        }
        synchronized (session) {
            clearPasswordResetVerificationWithinLock(session);
        }
    }

    private void clearPasswordResetVerificationWithinLock(HttpSession session) {
        session.removeAttribute(PASSWORD_RESET_STAFF_ID);
        session.removeAttribute(PASSWORD_RESET_VERIFIED_AT);
    }

    private String safeErrorMessage(RuntimeException exception) {
        String message = normalize(exception.getMessage());
        if (message != null && (message.startsWith("Password")
                || message.startsWith("New password"))) {
            return message;
        }
        return GENERIC_PASSWORD_RECOVERY_ERROR;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String sourceIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private void markPasswordRecoveryThrottled(HttpServletResponse response) {
        response.setHeader("Retry-After", String.valueOf(loginAttemptService.retryAfterSeconds()));
    }

    private record PasswordResetApproval(Long staffId, long verifiedAt) {
    }
}
