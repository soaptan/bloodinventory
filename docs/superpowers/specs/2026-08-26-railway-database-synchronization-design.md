# Railway Database Synchronization Design

**Date:** 2026-08-26

## Goal

Replace the current Railway PostgreSQL database with the local
`blood_inventory_db` database so the deployed application uses the same staff
accounts, password hashes, configuration, and business data as localhost.
After the replacement, the public application must accept the local `admin`
credentials without inheriting stale sessions or login-throttling state.

## Scope and Direction

This is a controlled, one-time **local-to-Railway** environment refresh. The
local database is the source of truth. Existing Railway data may be replaced,
but it must be backed up first so the operation can be rolled back.

This design does not implement continuous or two-way replication. Once the
public environment begins receiving data that must be preserved, Railway
becomes its own source of truth and future refreshes require a separate data
merge design.

## Preconditions

- The Railway application and PostgreSQL services belong to the authorized
  project.
- The operator can authenticate to Railway and obtain a public PostgreSQL
  connection string without committing it to the repository.
- PostgreSQL client tools compatible with the database server are available:
  `pg_dump`, `pg_restore`, and `psql`.
- The local PostgreSQL database is reachable and the application is not writing
  to it during the final dump.
- The Railway application can be stopped or scaled down during restoration.

If any precondition fails, the migration stops before changing Railway.

## Migration Flow

### 1. Preflight and Inventory

Record non-secret source and destination metadata: PostgreSQL versions,
database names, Flyway migration versions, schema names, and selected table
counts. Confirm that the local `admin` account exists, is active, is unlocked,
and stores a BCrypt password hash. Never print the password hash or connection
credentials.

### 2. Railway Rollback Backup

Create a timestamped custom-format dump of the current Railway database before
the application is stopped. Validate the dump with `pg_restore --list`. Store
it outside the Git working tree and retain it until the replacement has passed
all checks.

### 3. Consistent Local Source Dump

Create a custom-format dump of `blood_inventory_db` using `--no-owner` and
`--no-privileges`. A PostgreSQL dump uses a consistent snapshot, so it captures
schema, Flyway history, sequence positions, functions, triggers, and table data
without requiring a long local outage.

### 4. Railway Maintenance and Restore

Stop or scale down the Railway application before destructive restore work.
Restore the local dump into the Railway database with clean/replace semantics,
`--no-owner`, and `--no-privileges`. The PostgreSQL application service remains
stopped until validation and authentication cleanup are complete.

The restore command must target the exact Railway database resolved during
preflight. It must not use a broad or inferred database target.

### 5. Authentication-State Cleanup

Preserve staff accounts and BCrypt password hashes, but clear transient records
that are valid only for the local runtime:

- `staff_login_session`
- `authentication_throttle`

This prevents copied session IDs or failed-attempt buckets from blocking the
first public login. Historical audit and business records remain intact.

### 6. Railway Runtime Configuration

Keep Railway's datasource variables pointed at the Railway PostgreSQL service.
Set production proxy/session variables as follows:

```text
FORWARD_HEADERS_STRATEGY=framework
SESSION_COOKIE_SECURE=true
```

`PORT` and the existing `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` variables
remain Railway-specific. Local configuration files and `.env` files are never
uploaded.

### 7. Restart and Verification

Restart the application and verify:

1. Railway reports a healthy deployment with no Flyway checksum or Hibernate
   validation errors.
2. The deployed Flyway version matches the local database.
3. Critical table counts and identifiers match the source snapshot.
4. The Railway `admin` row is active, unlocked, and BCrypt-backed.
5. A fresh public browser session can authenticate with the local `admin`
   credentials and reaches `/admin/dashboard`.
6. The response sets an HTTPS session cookie and does not redirect to
   `/login?error`, `/login?throttled`, or return HTTP 429.

Password values and database connection strings must not appear in command
output, files under version control, screenshots, or verification logs.

## Failure Handling and Rollback

If restore, startup, data comparison, or login verification fails:

1. Keep the application stopped.
2. Capture the non-secret error output.
3. Clean the Railway database and restore the validated rollback dump.
4. Restart the application and verify its pre-migration health.
5. Retain both dumps until the cause is understood.

No additional repair attempts are layered onto a partially restored database.

## Deliverables

- A validated pre-change Railway rollback dump stored outside Git.
- A validated local source dump stored outside Git for the duration of the
  migration.
- A completed Railway restore with transient authentication state cleared.
- Railway proxy/session variables configured for HTTPS deployment.
- Verification evidence covering schema version, representative row counts,
  account status, and public login behavior.
- Removal of temporary source dumps after successful verification, while the
  rollback dump is retained until the user confirms it can be discarded.

## Security Constraints

- Never place plaintext passwords, database URLs, or dumps in the repository.
- Pass secrets through process environment or interactive credential prompts,
  not command-line arguments that may be retained in shell history.
- Do not reuse the password visible in the earlier screenshot; reset it to a
  new strong value after synchronization if that screenshot was shared beyond
  the authorized workspace.
- Do not alter Railway project data until both backup creation and backup
  validation succeed.
