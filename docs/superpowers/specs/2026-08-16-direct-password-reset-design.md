# Direct Password Reset Design

## Goal

Replace the email verification-code password reset with a single-step form. A staff member supplies their username, registered email, IC number, new password, and password confirmation. The application resets the password only when all three identity fields match the same active staff account.

The user explicitly accepts that this provides weaker account-recovery assurance than an out-of-band verification factor.

## User experience

- `/forgot-password` displays one form containing username, registered email, IC number, new password, and password confirmation.
- The page no longer mentions email verification, codes, delivery, or code expiry.
- Submitting valid matching details updates the password and displays a success message with a link back to login.
- Field-format and password-policy errors remain specific and appear beside their fields.
- An identity mismatch returns one generic error so the response does not disclose which detail was incorrect or whether an account exists.
- `/reset-password` remains as a compatibility redirect to `/forgot-password`; the public reset action is handled by `POST /forgot-password`.

## Server flow

1. The controller normalizes and validates the three identity fields.
2. It validates the new password with the existing strong-password policy and checks confirmation.
3. The service performs one case-insensitive username/email lookup plus exact IC-number match, restricted to active accounts.
4. If no account matches, the service raises a generic identity-mismatch error and does not update any data.
5. If an account matches, the service BCrypt-hashes and stores the new password.
6. Any unused legacy password-reset tokens for the account are marked used.
7. Active login sessions for the username are revoked.
8. A `RESET_COMPLETE` notification/audit record is written.

## Code changes

- Simplify `forgot-password.html` to a single reset form and update its explanatory/security text.
- Replace the two-step controller actions with a single `POST /forgot-password` reset action.
- Add a direct identity-based reset operation to `PasswordResetService` and remove mail/code behavior that is no longer reachable from the public flow.
- Keep the existing reset-token database migration/table intact for backward compatibility; no destructive schema migration is required.
- Remove mail mocking and code-extraction assumptions from authentication-flow tests.

## Security and error handling

- Require all three identity fields and an active account.
- Continue enforcing the existing password-strength policy and BCrypt storage.
- Never return account-existence or per-identity-field mismatch details.
- Revoke existing sessions after a successful reset.
- Preserve CSRF protection and the existing browser security headers.
- Never write passwords, email addresses, IC numbers, or other supplied secrets to audit messages.

## Verification

- Controller test: the page has one reset form and no verification-code/email-send copy.
- Success test: matching identity details reset the password, store a BCrypt hash, revoke sessions/tokens, write the audit notification, and never invoke mail.
- Failure test: mismatched identity details return the generic error and leave the password unchanged.
- Validation tests: malformed identity fields, weak password, and mismatched confirmation are rejected.
- Run the authentication-flow test class and then the full Maven test suite.
