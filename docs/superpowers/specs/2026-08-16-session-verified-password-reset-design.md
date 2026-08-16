# Session-Verified Password Reset Design

## Goal

Provide a two-page password reset without email verification codes. The first page verifies username, registered email, and IC number. A successful match redirects immediately to a separate page where the staff member chooses a new password.

The user explicitly accepts that knowledge of the three identity fields is the recovery factor and provides weaker assurance than an out-of-band verification factor.

## User experience

### Identity page

- `GET /forgot-password` shows username, registered email, and IC number only.
- The submit button says `Verify Account`.
- `POST /forgot-password` validates the field formats and matches all three values to one active account.
- A mismatch shows one generic error and does not reveal which field was wrong or whether an account exists.
- A match redirects immediately to `GET /reset-password`; there is no waiting period and no email or verification code.

### New-password page

- `GET /reset-password` is accessible only after successful identity verification in the same browser session.
- The page shows `New Password` and `Confirm New Password` only.
- Visible guidance and browser validation require 8-72 characters, uppercase and lowercase letters, a number, a special character, and no spaces.
- Password confirmation must match.
- A missing or expired verification redirects to `/forgot-password` with a generic instruction to verify the account again.
- A successful reset shows confirmation and a link back to login; revisiting the reset page requires verification again.

## Verification-session design

- Successful identity verification rotates the anonymous HTTP session ID to reduce session-fixation risk.
- The server stores only the verified `staff_id` and verification timestamp in the HTTP session; email and IC number are not carried to the password form.
- The marker is valid immediately and expires 10 minutes after verification. The 10-minute value is an expiry window, not a delay.
- The marker is one-use and is removed after a successful reset.
- Missing, expired, malformed, or inactive-account markers cannot reset a password.

## Server flow

1. The controller validates username, email, and IC number.
2. `PasswordResetService.verifyIdentity(...)` performs one case-insensitive username/email lookup plus exact IC-number match, restricted to active accounts, and returns the matching staff ID.
3. The controller rotates the session ID, stores the staff ID and current timestamp, and redirects to `/reset-password`.
4. The reset controller checks the session marker and its 10-minute expiry before displaying or processing the password form.
5. The reset service reloads the active account by staff ID, applies the existing password policy, rejects reuse of the current BCrypt password, hashes and stores the new password, marks unused legacy reset tokens as used, ends active login sessions, and writes `RESET_COMPLETE` with the request source IP.
6. The controller clears the verification marker after success.

## Security and error handling

- Preserve CSRF protection and browser security headers on both forms.
- Do not place username, email, IC number, or staff ID in the reset-page HTML or URL.
- Do not store email or IC number in the verification session.
- Do not log supplied identity details or passwords.
- Keep identity mismatch and expired-verification messages generic.
- Retain server-side password validation even though browser validation provides immediate feedback.
- Keep the existing reset-token database table for backward compatibility; no destructive migration is required.

## Verification

- Identity-page test: contains only the three identity fields and no password/code inputs.
- Correct-identity test: immediately redirects to `/reset-password` and stores a session-bound staff ID/timestamp.
- Incorrect-identity test: remains on the identity page with a generic error and no verification marker.
- Reset-page guard tests: missing and expired markers redirect to identity verification.
- Password-page test: contains only new-password fields and visible validation guidance.
- Validation tests: weak and mismatched passwords are rejected without changing the stored password.
- Success test: BCrypt password update, legacy-token invalidation, active-session revocation, audit notification, and one-use marker clearing.
- Run authentication-flow tests, the full Maven suite, and live localhost HTTP checks.
