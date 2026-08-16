# Session-Verified Password Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split password recovery into immediate account verification followed by a separate, session-protected new-password page without email or verification codes.

**Architecture:** `POST /forgot-password` verifies the three identity fields and stores only a staff ID plus verification timestamp in the rotated HTTP session before redirecting to `/reset-password`. The reset controller enforces a one-use 10-minute expiry, and `PasswordResetService` resets only the active staff ID authorized by that session.

**Tech Stack:** Java 25, Spring Boot MVC/Security/JDBC, Thymeleaf, PostgreSQL, JUnit 5, MockMvc, AssertJ, vanilla JavaScript/CSS

## Global Constraints

- Correct identity verification redirects immediately; 10 minutes is an expiry window, never a waiting period.
- No verification code or email delivery is used.
- The reset page and URL contain no username, email, IC number, or staff ID.
- The session stores only verified staff ID and verification timestamp.
- The password marker is one-use and valid for at most 10 minutes.
- Password validation requires 8-72 characters, uppercase and lowercase letters, a number, a special character, no spaces, and matching confirmation.
- Password hashing, legacy-token invalidation, active-session revocation, and `RESET_COMPLETE` auditing remain transactional.

---

### Task 1: Specify the two-page reset flow

**Files:**
- Modify: `bloodinventory/src/test/java/com/fyp/bloodinventory/AuthenticationFlowTests.java`

**Interfaces:**
- Consumes: `GET/POST /forgot-password`, `GET/POST /reset-password`, `MockHttpSession`, and existing authentication database fixtures.
- Produces: executable expectations for immediate verification, guarded reset-page access, password validation, and one-use completion.

- [ ] **Step 1: Replace the single-page shape test**

Assert that `GET /forgot-password` contains `Verify Account`, username/email/IC inputs, and no `newPassword`, `confirmPassword`, or verification-code input.

```java
@Test
void forgotPasswordPageOnlyCollectsIdentityDetails() throws Exception {
    mockMvc.perform(get("/forgot-password"))
            .andExpect(expect(status().isOk()))
            .andExpect(expect(content().string(containsString("Verify Account"))))
            .andExpect(expect(content().string(not(containsString("name=\"newPassword\"")))))
            .andExpect(expect(content().string(not(containsString("name=\"verificationCode\"")))));
}
```

- [ ] **Step 2: Add immediate redirect and reset-page guard tests**

Create an active test account, submit matching identity fields, expect an immediate redirect to `/reset-password`, retain the returned `MockHttpSession`, and verify `GET /reset-password` contains the two password inputs and `New password requirements` but no identity fields. Also assert missing and deliberately expired verification sessions redirect to `/forgot-password?verificationRequired`.

- [ ] **Step 3: Rewrite mismatch and password-validation tests**

The mismatch test posts only identity fields, expects the generic error, and verifies no redirect/session authorization. A password validation test first completes real identity verification, then posts weak and mismatched passwords to `/reset-password`, expects field errors, and verifies the old password remains stored.

- [ ] **Step 4: Rewrite the successful reset test**

Use the session returned by successful verification to submit:

```java
mockMvc.perform(post("/reset-password")
                .session(resetSession)
                .with(csrf())
                .param("newPassword", "NewPassword123!")
                .param("confirmPassword", "NewPassword123!"))
        .andExpect(expect(status().isOk()))
        .andExpect(expect(content().string(containsString("Password reset successfully"))));
```

Retain assertions for BCrypt matching, used legacy tokens, ended sessions, and the audit notification. Then assert the reset session can no longer access `GET /reset-password`.

- [ ] **Step 5: Run the focused test and verify RED**

```powershell
$env:BLOODINVENTORY_JAVA_HOME='C:\Users\User\.jdk\jdk-25.0.2'
Set-Location bloodinventory
.\mvnw.cmd -q -Dtest=AuthenticationFlowTests test
```

Expected: FAIL because identity submission currently requires password fields, `/reset-password` always redirects, and no separate template/session authorization exists.

---

### Task 2: Split identity verification and add session-bound navigation

**Files:**
- Modify: `bloodinventory/src/main/java/com/fyp/bloodinventory/service/PasswordResetService.java`
- Modify: `bloodinventory/src/main/java/com/fyp/bloodinventory/controller/AuthController.java`
- Test: `bloodinventory/src/test/java/com/fyp/bloodinventory/AuthenticationFlowTests.java`

**Interfaces:**
- Produces: `Optional<Long> verifyIdentity(String username, String email, String icNumber)`.
- Produces: `void resetPasswordForVerifiedStaff(Long staffId, String newPassword, String confirmPassword, String sourceIp)`.
- Produces: session attributes `passwordResetStaffId` and `passwordResetVerifiedAt`, with a 600,000 ms expiry.

- [ ] **Step 1: Add identity verification without mutation**

Implement `verifyIdentity(...)` by normalizing the three fields, reusing the active-account lookup, and returning `Optional.of(account.staffId())` or `Optional.empty()`.

- [ ] **Step 2: Reset only the session-authorized account**

Replace `resetPasswordForIdentity(...)` with `resetPasswordForVerifiedStaff(...)`. Validate passwords, reload the account using `staff_id = ? AND is_active = TRUE`, and pass it to the existing transactional reset updates. Never accept identity fields in this method.

- [ ] **Step 3: Convert `POST /forgot-password` into verification**

Remove password parameters/validation. Clear any earlier reset marker, validate identity fields, call `verifyIdentity(...)`, and show the existing generic error when it returns empty. On success call `request.changeSessionId()`, store staff ID and `System.currentTimeMillis()`, and return `redirect:/reset-password`.

- [ ] **Step 4: Guard `GET /reset-password`**

Add a helper that accepts only numeric staff ID/timestamp attributes whose age is between 0 and 600,000 ms. Clear invalid/expired attributes and redirect to `/forgot-password?verificationRequired`; otherwise return the new `reset-password` template.

- [ ] **Step 5: Add `POST /reset-password`**

Reject missing/expired verification with the same redirect. Validate password and confirmation into field errors, call `resetPasswordForVerifiedStaff(...)`, and render the reset template. Clear both session attributes only after successful service completion.

- [ ] **Step 6: Explain an expired/missing marker generically**

Allow `GET /forgot-password?verificationRequired` to display `Verify your account details before choosing a new password.` without revealing account state. A fresh visit to the identity page clears stale reset markers.

---

### Task 3: Create separate identity and password pages with validation feedback

**Files:**
- Modify: `bloodinventory/src/main/resources/templates/forgot-password.html`
- Create: `bloodinventory/src/main/resources/templates/reset-password.html`
- Modify: `bloodinventory/src/main/resources/static/js/auth-validation.js`
- Modify: `bloodinventory/src/main/resources/static/css/auth-pages.css`

**Interfaces:**
- Consumes: `errorMessage`, `successMessage`, and `fieldErrors` controller model attributes.
- Produces: one identity-only form and one password-only form with client/server validation feedback.

- [ ] **Step 1: Simplify the identity template**

Use heading `Verify Account`, explanatory copy stating that all three details must match, three identity inputs, and a `Verify Account` submit button. Remove both password fields and password guidance.

- [ ] **Step 2: Add the reset-password template**

Create a card using the existing auth layout. The `/reset-password` form contains only `newPassword` and `confirmPassword`, with the existing `minlength`, `maxlength`, `pattern`, `data-match`, and field-error elements. Hide the form when `successMessage` exists.

Add a visible list marked `data-password-requirements`:

```html
<ul class="password-requirements" aria-label="New password requirements">
    <li data-password-rule="length">8-72 characters</li>
    <li data-password-rule="letterCase">Uppercase and lowercase</li>
    <li data-password-rule="number">At least one number</li>
    <li data-password-rule="symbol">At least one special character</li>
    <li data-password-rule="noWhitespace">No spaces</li>
</ul>
```

- [ ] **Step 3: Add live requirement indicators**

Extend `auth-validation.js` so a form containing `#newPassword` toggles `is-met` on each requirement item using literal checks for length, case, number, symbol, and whitespace. Keep the existing submit-time validation and confirmation matching.

- [ ] **Step 4: Style requirement states**

Add compact `.password-requirements` styles to `auth-pages.css`; unmet rules use neutral text and met rules use accessible green text with a check indicator.

- [ ] **Step 5: Run the focused tests to verify GREEN**

```powershell
$env:BLOODINVENTORY_JAVA_HOME='C:\Users\User\.jdk\jdk-25.0.2'
Set-Location bloodinventory
.\mvnw.cmd -q -Dtest=AuthenticationFlowTests test
```

Expected: PASS with immediate redirect, guards, validation, security side effects, and one-use session behavior covered.

---

### Task 4: Verify and commit the complete two-page flow

**Files:**
- Verify: all files modified in Tasks 1-4

**Interfaces:**
- Produces: a clean, tested two-page reset implementation on the current feature branch.

- [ ] **Step 1: Scan runtime files and validate formatting**

```powershell
rg -n "Send Verification Code|verificationCode|JavaMailSender|spring\.mail" bloodinventory/src/main/java bloodinventory/src/main/resources/templates bloodinventory/src/main/resources/static bloodinventory/src/main/resources/application.properties
git diff --check
```

Expected: no obsolete runtime email/code references and no whitespace errors.

- [ ] **Step 2: Run the full Maven suite**

```powershell
$env:BLOODINVENTORY_JAVA_HOME='C:\Users\User\.jdk\jdk-25.0.2'
Set-Location bloodinventory
.\mvnw.cmd test
```

Expected: BUILD SUCCESS with zero failures and errors.

- [ ] **Step 3: Verify live localhost navigation**

Use HTTP requests with one cookie session and CSRF token to verify the identity page renders separately, unverified `/reset-password` redirects, and no email/code text is served. Do not mutate a real user account.

- [ ] **Step 4: Commit**

```powershell
git add -- bloodinventory/src/main/java/com/fyp/bloodinventory/controller/AuthController.java bloodinventory/src/main/java/com/fyp/bloodinventory/service/PasswordResetService.java bloodinventory/src/main/resources/templates/forgot-password.html bloodinventory/src/main/resources/templates/reset-password.html bloodinventory/src/main/resources/static/js/auth-validation.js bloodinventory/src/main/resources/static/css/auth-pages.css bloodinventory/src/test/java/com/fyp/bloodinventory/AuthenticationFlowTests.java
git commit -m "feat: split password reset into verified steps"
```
