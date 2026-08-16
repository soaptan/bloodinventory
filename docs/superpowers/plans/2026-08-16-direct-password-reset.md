# Direct Password Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the SMTP verification-code flow with a single-step password reset after username, registered email, and IC number match one active staff account.

**Architecture:** `AuthController` will validate and submit one public reset form to a transactional `PasswordResetService.resetPasswordForIdentity(...)` method. The service will match one active account, enforce the existing password policy, BCrypt-hash the new password, invalidate legacy tokens and active sessions, and write the existing reset-complete audit notification without invoking mail.

**Tech Stack:** Java 25, Spring Boot MVC/Security/JDBC, Thymeleaf, PostgreSQL, JUnit 5, MockMvc, AssertJ

## Global Constraints

- The public form contains username, registered email, IC number, new password, and password confirmation.
- No verification code is generated, requested, entered, or emailed.
- Identity mismatch responses do not reveal which field failed or whether an account exists.
- Passwords continue to use the existing strong-password policy and BCrypt encoder.
- A successful reset invalidates active login sessions and unused legacy reset tokens and records `RESET_COMPLETE`.
- Existing CSRF protection and browser security headers remain enabled.
- The existing reset-token database table remains intact; no destructive schema migration is added.

---

### Task 1: Specify the single-step public reset behavior

**Files:**
- Modify: `bloodinventory/src/test/java/com/fyp/bloodinventory/AuthenticationFlowTests.java`
- Test: `bloodinventory/src/test/java/com/fyp/bloodinventory/AuthenticationFlowTests.java`

**Interfaces:**
- Consumes: `GET /forgot-password`, `POST /forgot-password`, the `staff`, `staff_login_session`, `staff_password_reset_token`, and `system_notification` persistence already used by authentication tests.
- Produces: executable expectations for the direct-reset controller and `PasswordResetService.resetPasswordForIdentity(String username, String email, String icNumber, String newPassword, String confirmPassword, String sourceIp)`.

- [ ] **Step 1: Replace verification-code fixtures and assertions with a page-shape test**

Remove `JavaMailSender`, `SimpleMailMessage`, Mockito mail capture, `RESET_CODE_PATTERN`, and mail-related test properties. Add:

```java
@Test
void forgotPasswordPageUsesSingleStepResetForm() throws Exception {
    mockMvc.perform(get("/forgot-password"))
            .andExpect(expect(status().isOk()))
            .andExpect(expect(content().string(containsString("Reset Password"))))
            .andExpect(expect(content().string(containsString("name=\"newPassword\""))))
            .andExpect(expect(content().string(containsString("name=\"confirmPassword\""))))
            .andExpect(expect(content().string(not(containsString("verificationCode")))))
            .andExpect(expect(content().string(not(containsString("Send Verification Code")))));
}
```

Use static Hamcrest imports for `containsString` and `not`.

- [ ] **Step 2: Rewrite the reset success test around direct identity verification**

Create a unique active staff account with a BCrypt old password, add one active `staff_login_session`, add one unused legacy reset token, submit:

```java
mockMvc.perform(post("/forgot-password")
                .with(csrf())
                .param("username", username)
                .param("email", email)
                .param("icNumber", icNumber)
                .param("newPassword", "NewPassword123!")
                .param("confirmPassword", "NewPassword123!"))
        .andExpect(expect(status().isOk()))
        .andExpect(expect(content().string(containsString("Password reset successfully"))));
```

Assert the stored password starts with `$2`, matches `NewPassword123!` through the injected `PasswordEncoder`, the legacy token has `used_at IS NOT NULL`, the session has `status = 'ENDED'` and `end_reason = 'PASSWORD_RESET'`, and a `RESET_COMPLETE` notification exists for the username.

- [ ] **Step 3: Add mismatch and validation coverage**

Add a mismatch test which submits a wrong IC number, expects the generic text `The request could not be completed. Please try again later.`, and asserts the stored password is unchanged. Update the existing validation test to post the new fields to `/forgot-password` and expect the strong-password and confirmation errors without any database update.

- [ ] **Step 4: Run the focused tests and verify RED**

Run:

```powershell
$env:BLOODINVENTORY_JAVA_HOME='C:\Users\User\.jdk\jdk-25.0.2'
Set-Location bloodinventory
.\mvnw.cmd -Dtest=AuthenticationFlowTests test
```

Expected: FAIL because the current page still contains verification-code UI and `POST /forgot-password` does not accept or apply the new password fields.

---

### Task 2: Implement the transactional direct reset

**Files:**
- Modify: `bloodinventory/src/main/java/com/fyp/bloodinventory/service/PasswordResetService.java`
- Modify: `bloodinventory/src/main/java/com/fyp/bloodinventory/controller/AuthController.java`
- Delete: `bloodinventory/src/main/java/com/fyp/bloodinventory/dto/PasswordResetRequestResult.java`
- Test: `bloodinventory/src/test/java/com/fyp/bloodinventory/AuthenticationFlowTests.java`

**Interfaces:**
- Consumes: `PasswordPolicy.requireStrongPassword(String)`, `PasswordEncoder`, `DatabaseAuditContextService.applyCurrentContext()`, and `SystemNotificationService.record(String, String, String, String, String)`.
- Produces: `PasswordResetService.resetPasswordForIdentity(String username, String email, String icNumber, String newPassword, String confirmPassword, String sourceIp)` and one `POST /forgot-password` action using it.

- [ ] **Step 1: Replace the two-step service API**

Remove `requestResetForIdentity(...)`, `resetPasswordWithCode(...)`, all code generation/validation/mail helpers, reset-token lookup/attempt helpers, mail configuration fields, and related imports. Add this transactional public method:

```java
@Transactional
public void resetPasswordForIdentity(String username,
                                     String email,
                                     String icNumber,
                                     String newPassword,
                                     String confirmPassword,
                                     String sourceIp) {
    String normalizedUsername = requireText(username, "Please enter your username.");
    String normalizedEmail = requireText(email, "Please enter your registered email.");
    String normalizedIcNumber = requireText(icNumber, "Please enter your IC number.");
    String normalizedNewPassword = PasswordPolicy.requireStrongPassword(newPassword);
    String normalizedConfirmPassword = requireConfirmation(confirmPassword);
    if (!normalizedNewPassword.equals(normalizedConfirmPassword)) {
        throw new RuntimeException("New password and confirmation do not match.");
    }

    StaffAccount account = findActiveAccountByIdentity(
            normalizedUsername, normalizedEmail, normalizedIcNumber);
    if (account == null) {
        throw new RuntimeException("Account identity did not match.");
    }

    completeDirectReset(account, normalizedNewPassword, sourceIp);
}
```

- [ ] **Step 2: Implement the reset transaction**

Extract the current password-difference check and updates into:

```java
private void completeDirectReset(StaffAccount account,
                                 String normalizedNewPassword,
                                 String sourceIp) {
    auditContextService.applyCurrentContext();
    String storedPassword = PasswordHashSupport.normalizeStoredPassword(account.password());
    if (PasswordHashSupport.isBcryptHash(storedPassword)
            && passwordEncoder.matches(normalizedNewPassword, storedPassword)) {
        throw new RuntimeException("New password must be different from the current password.");
    }

    jdbcTemplate.update("""
            UPDATE staff
            SET password = ?, updated_at = CURRENT_TIMESTAMP, last_modified_by = ?
            WHERE staff_id = ?
            """, passwordEncoder.encode(normalizedNewPassword), account.username(), account.staffId());
    jdbcTemplate.update("""
            UPDATE staff_password_reset_token
            SET used_at = CURRENT_TIMESTAMP
            WHERE staff_id = ? AND used_at IS NULL
            """, account.staffId());
    jdbcTemplate.update("""
            UPDATE staff_login_session
            SET status = 'ENDED', ended_at = CURRENT_TIMESTAMP, end_reason = 'PASSWORD_RESET'
            WHERE LOWER(username) = LOWER(?) AND status = 'ACTIVE'
            """, account.username());

    notificationService.record(
            "Password Reset", "RESET_COMPLETE",
            "Password reset completed for " + account.username(),
            account.username(), sourceIp);
}
```

- [ ] **Step 3: Convert the controller to one POST action**

Change `requestPasswordReset(...)` to accept `newPassword` and `confirmPassword`, run `validateResetFields(fieldErrors, newPassword, confirmPassword)`, and call `resetPasswordForIdentity(...)`. Remove all `codeRequested`, `maskedEmail`, and `verificationCode` model handling. Replace the validator with:

```java
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
```

Keep `GET /reset-password` as a redirect and remove the obsolete `POST /reset-password` action. On success, display `Password reset successfully. You can sign in with the new password.`; on failure, preserve only the non-password identity inputs.

- [ ] **Step 4: Delete the unused result DTO and run the focused tests**

Delete `PasswordResetRequestResult.java`, then run:

```powershell
$env:BLOODINVENTORY_JAVA_HOME='C:\Users\User\.jdk\jdk-25.0.2'
Set-Location bloodinventory
.\mvnw.cmd -Dtest=AuthenticationFlowTests test
```

Expected: service/controller tests may still fail on page markup, but the project compiles and direct-reset persistence assertions pass.

---

### Task 3: Replace the verification UI and remove SMTP-only configuration

**Files:**
- Modify: `bloodinventory/src/main/resources/templates/forgot-password.html`
- Modify: `bloodinventory/src/main/resources/application.properties`
- Modify: `bloodinventory/pom.xml`
- Test: `bloodinventory/src/test/java/com/fyp/bloodinventory/AuthenticationFlowTests.java`

**Interfaces:**
- Consumes: the controller model keys `username`, `email`, `icNumber`, `fieldErrors`, `successMessage`, and `errorMessage`.
- Produces: one `/forgot-password` HTML form with `newPassword` and `confirmPassword`; no runtime mail dependency or SMTP configuration.

- [ ] **Step 1: Convert the page to a single form**

Use `Reset Password` as the heading and `Verify your staff details and choose a new password.` as the description. Move the existing new-password and confirmation controls into the always-visible `/forgot-password` form after IC number, set the submit text to `Reset Password`, and remove the conditional verification form, code box, and code-expiry note. Add this note:

```html
<p class="security-note">All staff details must match one active account.</p>
```

- [ ] **Step 2: Remove unused SMTP application wiring**

Delete `app.password-reset.*` and `spring.mail.*` lines from `application.properties`. Remove only this dependency from `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

- [ ] **Step 3: Run the focused test to verify GREEN**

Run:

```powershell
$env:BLOODINVENTORY_JAVA_HOME='C:\Users\User\.jdk\jdk-25.0.2'
Set-Location bloodinventory
.\mvnw.cmd -Dtest=AuthenticationFlowTests test
```

Expected: PASS with all authentication-flow tests green.

- [ ] **Step 4: Commit the direct-reset implementation**

```powershell
git add -- bloodinventory/pom.xml bloodinventory/src/main/resources/application.properties bloodinventory/src/main/resources/templates/forgot-password.html bloodinventory/src/main/java/com/fyp/bloodinventory/controller/AuthController.java bloodinventory/src/main/java/com/fyp/bloodinventory/service/PasswordResetService.java bloodinventory/src/main/java/com/fyp/bloodinventory/dto/PasswordResetRequestResult.java bloodinventory/src/test/java/com/fyp/bloodinventory/AuthenticationFlowTests.java
git commit -m "feat: reset passwords without email verification"
```

---

### Task 4: Verify the complete application

**Files:**
- Verify: all files changed by Tasks 1-3

**Interfaces:**
- Consumes: the completed direct-reset implementation.
- Produces: evidence that the full project remains green and the diff contains no email-code remnants in the password-reset flow.

- [ ] **Step 1: Scan for obsolete references and formatting errors**

Run:

```powershell
rg -n "Send Verification Code|verificationCode|requestResetForIdentity|resetPasswordWithCode|JavaMailSender|spring\.mail|app\.password-reset" bloodinventory/src bloodinventory/pom.xml
git diff --check HEAD~1
```

Expected: `rg` finds no password-reset mail/code references, and `git diff --check` reports no whitespace errors.

- [ ] **Step 2: Run the full Maven suite**

Run:

```powershell
$env:BLOODINVENTORY_JAVA_HOME='C:\Users\User\.jdk\jdk-25.0.2'
Set-Location bloodinventory
.\mvnw.cmd test
```

Expected: BUILD SUCCESS with every test passing.

- [ ] **Step 3: Review the final diff and repository state**

Run:

```powershell
git diff HEAD~1 --stat
git status --short
```

Expected: only the planned direct-reset files and documentation are changed, with a clean working tree after the implementation commit.
