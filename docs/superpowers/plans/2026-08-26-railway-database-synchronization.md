# Railway Database Synchronization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Railway PostgreSQL database with a validated copy of the local `blood_inventory_db` database and prove that the public application accepts the local administrator account.

**Architecture:** Use PostgreSQL custom-format dumps for both rollback and source artifacts, stop the Railway application before destructive work, restore with owner/privilege stripping, and clear only runtime-specific authentication state. Keep all credentials in process memory/environment and all dumps in a timestamped directory outside the Git working tree.

**Tech Stack:** PostgreSQL 18 client tools, PowerShell, Railway CLI 5.44.0, Spring Boot 3.5, Flyway, Railway PostgreSQL

**Spec:** `docs/superpowers/specs/2026-08-26-railway-database-synchronization-design.md`

## Global Constraints

- The local database is the source of truth and existing Railway data may be replaced only after a validated rollback dump exists.
- This is a one-time local-to-Railway refresh, not continuous or two-way replication.
- Never print, commit, or persist database URLs, database passwords, plaintext staff passwords, or password hashes.
- Stop before remote mutation if the Railway project, environment, application service, or PostgreSQL service cannot be identified exactly.
- Keep the Railway application stopped from the start of destructive restore work until database verification completes.
- Clear `staff_login_session` and `authentication_throttle` after source restoration; retain historical audit and business records.
- Set `FORWARD_HEADERS_STRATEGY=framework` and `SESSION_COOKIE_SECURE=true` on the Railway application service.
- Keep both dumps until public-login verification succeeds. Retain the Railway rollback dump until the user explicitly approves its removal.
- On any restore, startup, comparison, or login failure, restore the validated Railway rollback dump before attempting a different repair.

## File and Artifact Map

- No application source file changes are required.
- Create outside Git: `%TEMP%\bloodinventory-railway-sync-YYYYMMDD-HHMMSS\railway-before.dump` — pre-change Railway rollback dump.
- Create outside Git: `%TEMP%\bloodinventory-railway-sync-YYYYMMDD-HHMMSS\local-source.dump` — local source dump.
- Create outside Git: `%TEMP%\bloodinventory-railway-sync-YYYYMMDD-HHMMSS\*.sha256` — dump checksums without credentials.
- Modify remotely: Railway PostgreSQL `public` schema and data.
- Modify remotely: application variables `FORWARD_HEADERS_STRATEGY` and `SESSION_COOKIE_SECURE`.

---

### Task 1: Establish Local Preflight and Source Evidence

**Artifacts:**
- Create outside Git: timestamped migration directory
- Create outside Git: `local-preflight.txt`

**Interfaces:**
- Consumes: ignored `bloodinventory/application-local.properties`
- Produces: `$syncRoot`, PostgreSQL client paths, and non-secret local validation evidence used by Tasks 3–7

- [ ] **Step 1: Verify the repository and PostgreSQL client paths**

Run from `C:\Users\User\OneDrive\Desktop\bloodinventory`:

```powershell
$repoRoot = (Resolve-Path '.').Path
$expectedRepoRoot = 'C:\Users\User\OneDrive\Desktop\bloodinventory'
if ($repoRoot -ne $expectedRepoRoot) { throw "Unexpected repository root: $repoRoot" }

$pgBin = 'C:\Program Files\PostgreSQL\18\bin'
$pgDump = Join-Path $pgBin 'pg_dump.exe'
$pgRestore = Join-Path $pgBin 'pg_restore.exe'
$psql = Join-Path $pgBin 'psql.exe'
foreach ($tool in @($pgDump, $pgRestore, $psql)) {
    if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) {
        throw "Required PostgreSQL client is missing: $tool"
    }
}
& $pgDump --version
& $pgRestore --version
& $psql --version
```

Expected: all three tools report PostgreSQL 18 and no exception is raised.

- [ ] **Step 2: Create a validated temporary artifact directory outside Git**

```powershell
$syncStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$syncRoot = Join-Path ([IO.Path]::GetTempPath()) "bloodinventory-railway-sync-$syncStamp"
$resolvedTempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
New-Item -ItemType Directory -Path $syncRoot -ErrorAction Stop | Out-Null
$resolvedSyncRoot = [IO.Path]::GetFullPath($syncRoot)
if (-not $resolvedSyncRoot.StartsWith($resolvedTempRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Migration directory is outside the intended temp root: $resolvedSyncRoot"
}
if ($resolvedSyncRoot.StartsWith($repoRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Migration artifacts must not be stored in the Git working tree."
}
```

Expected: `$syncRoot` exists under the current user's temporary directory and not under the repository.

- [ ] **Step 3: Load the ignored local database password without displaying it**

```powershell
$localPropertiesPath = Join-Path $repoRoot 'bloodinventory\application-local.properties'
if (-not (Test-Path -LiteralPath $localPropertiesPath -PathType Leaf)) {
    throw 'Local application-local.properties is missing.'
}
$localProperties = @{}
Get-Content -LiteralPath $localPropertiesPath | ForEach-Object {
    if ($_ -match '^\s*([^#!][^=]*)=(.*)$') {
        $localProperties[$matches[1].Trim()] = $matches[2]
    }
}
$localDbPassword = $localProperties['spring.datasource.password']
if ([string]::IsNullOrEmpty($localDbPassword)) {
    throw 'Local datasource password is missing.'
}
$env:PGPASSWORD = $localDbPassword
$env:PGHOST = 'localhost'
$env:PGPORT = '5432'
$env:PGUSER = 'postgres'
$env:PGDATABASE = 'blood_inventory_db'
```

Expected: no secret value is written to the console.

- [ ] **Step 4: Capture non-secret local database evidence**

```powershell
$localPreflightPath = Join-Path $syncRoot 'local-preflight.txt'
$localPreflightSql = @'
SELECT 'server_version=' || current_setting('server_version');
SELECT 'database=' || current_database();
SELECT 'flyway_latest=' || COALESCE(MAX(version), 'none') FROM flyway_schema_history WHERE success;
SELECT 'staff=' || COUNT(*) FROM staff;
SELECT 'donor=' || COUNT(*) FROM donor;
SELECT 'donation=' || COUNT(*) FROM donation;
SELECT 'blood_component=' || COUNT(*) FROM blood_component;
SELECT 'admin_ready=' || (
    EXISTS (
        SELECT 1 FROM staff
        WHERE username = 'admin'
          AND is_active
          AND NOT is_locked
          AND password ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'
    )
)::text;
'@
& $psql --no-psqlrc --set ON_ERROR_STOP=1 --tuples-only --no-align --command $localPreflightSql |
    Set-Content -LiteralPath $localPreflightPath
if ($LASTEXITCODE -ne 0) { throw 'Local preflight query failed.' }
$localEvidence = Get-Content -LiteralPath $localPreflightPath
if ($localEvidence -notcontains 'admin_ready=true') {
    throw 'The local admin account is not active, unlocked, and BCrypt-backed.'
}
$localEvidence
```

Expected: local version, Flyway version, counts, and `admin_ready=true` are printed; no hash or password is printed.

- [ ] **Step 5: Clear the local database secret from the process environment**

```powershell
Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
$localDbPassword = $null
```

Expected: `$env:PGPASSWORD` is unset.

### Task 2: Authenticate and Resolve Exact Railway Targets

**Artifacts:**
- Modify outside Git: Railway CLI authentication state
- Create outside Git: `railway-target.txt` containing only non-secret IDs/names

**Interfaces:**
- Consumes: authorized Railway account and linked project
- Produces: `$projectId`, `$environmentId`, `$appServiceId`, `$postgresServiceId`, `$appScaleAssignmentsOriginal`, and `$appScaleAssignmentsZero`

- [ ] **Step 1: Authenticate through Railway's browser flow**

```powershell
npx --yes @railway/cli@5.44.0 login
if ($LASTEXITCODE -ne 0) { throw 'Railway login failed.' }
npx --yes @railway/cli@5.44.0 whoami
if ($LASTEXITCODE -ne 0) { throw 'Railway authentication was not established.' }
```

Expected: the authorized Railway account is displayed. The user completes the browser prompt if Railway opens one.

- [ ] **Step 2: Link the current directory to the existing Railway project**

```powershell
npx --yes @railway/cli@5.44.0 link
if ($LASTEXITCODE -ne 0) { throw 'Railway project linking failed.' }
```

Expected: select the existing blood inventory project and its production environment; do not create a new project.

- [ ] **Step 3: List and select exact non-secret target IDs**

```powershell
$environmentName = 'production'
$railwayStatusJson = npx --yes @railway/cli@5.44.0 status `
    --environment $environmentName --json
if ($LASTEXITCODE -ne 0) { throw 'Unable to read Railway project status.' }
$railwayStatus = $railwayStatusJson | ConvertFrom-Json
$railwayStatus | ConvertTo-Json -Depth 8

$railwayServicesJson = npx --yes @railway/cli@5.44.0 service list `
    --environment $environmentName --json
if ($LASTEXITCODE -ne 0) { throw 'Unable to list Railway services.' }
$railwayServices = $railwayServicesJson | ConvertFrom-Json
$railwayServices | Select-Object id, name | Format-Table
```

Expected: exactly one Spring Boot application service and one PostgreSQL service are identifiable. Service IDs and names are non-secret.

- [ ] **Step 4: Record and validate the selected IDs**

```powershell
$projectId = [string]$railwayStatus.id
$environmentEdges = @($railwayStatus.environments.edges)
if ($environmentEdges.Count -ne 1) {
    throw "Expected exactly one scoped Railway environment, found $($environmentEdges.Count)."
}
$environmentId = [string]$environmentEdges[0].node.id
$appServiceId = Read-Host 'Paste the application service ID from the list'
$postgresServiceId = Read-Host 'Paste the PostgreSQL service ID from the list'
foreach ($id in @($projectId, $environmentId, $appServiceId, $postgresServiceId)) {
    $parsedId = [Guid]::Empty
    if (-not [Guid]::TryParse($id, [ref]$parsedId)) { throw "Invalid Railway ID: $id" }
}
if ($appServiceId -eq $postgresServiceId) { throw 'Application and PostgreSQL service IDs must differ.' }

$targetEvidencePath = Join-Path $syncRoot 'railway-target.txt'
@(
    "project_id=$projectId"
    "environment_id=$environmentId"
    "application_service_id=$appServiceId"
    "postgres_service_id=$postgresServiceId"
) | Set-Content -LiteralPath $targetEvidencePath
```

Expected: four valid, distinct target identifiers are stored without credentials.

- [ ] **Step 5: Derive and validate the application's exact scale configuration**

```powershell
$serviceInstanceEdges = @($environmentEdges[0].node.serviceInstances.edges)
$appInstance = @($serviceInstanceEdges | Where-Object { $_.node.serviceId -eq $appServiceId })
if ($appInstance.Count -ne 1) {
    throw "Expected exactly one application service instance, found $($appInstance.Count)."
}
$deployConfig = $appInstance[0].node.latestDeployment.meta.serviceManifest.deploy
if ($null -eq $deployConfig) { throw 'Application deployment scale metadata is missing.' }

$appScaleAssignmentsOriginal = @()
if ($null -ne $deployConfig.multiRegionConfig) {
    $appScaleAssignmentsOriginal = @(
        $deployConfig.multiRegionConfig.PSObject.Properties | ForEach-Object {
            $replicas = [int]$_.Value.numReplicas
            if ($replicas -gt 0) { "$($_.Name)=$replicas" }
        }
    )
} elseif (-not [string]::IsNullOrWhiteSpace([string]$deployConfig.region)) {
    $replicas = if ($null -eq $deployConfig.numReplicas) { 1 } else { [int]$deployConfig.numReplicas }
    if ($replicas -gt 0) { $appScaleAssignmentsOriginal = @("$($deployConfig.region)=$replicas") }
}
if ($appScaleAssignmentsOriginal.Count -eq 0) {
    throw 'No positive application replica assignments were found.'
}
$appScaleAssignmentsZero = @($appScaleAssignmentsOriginal | ForEach-Object {
    ($_.Split('=', 2)[0]) + '=0'
})
Write-Output "Original application scale: $($appScaleAssignmentsOriginal -join ', ')"
```

Expected: one or more exact Railway region IDs and positive replica counts are captured, with a matching zero-replica assignment for maintenance.

### Task 3: Back Up and Validate the Current Railway Database

**Artifacts:**
- Create outside Git: `railway-before.dump`
- Create outside Git: `railway-before.dump.sha256`

**Interfaces:**
- Consumes: exact Railway PostgreSQL service ID and in-memory service variables
- Produces: validated rollback dump required before Task 5 may run

- [ ] **Step 1: Read Railway PostgreSQL variables into memory without printing them**

```powershell
$remoteVariablesJson = npx --yes @railway/cli@5.44.0 variable list `
    --project $projectId --environment $environmentId --service $postgresServiceId --json
if ($LASTEXITCODE -ne 0) { throw 'Unable to load Railway PostgreSQL variables.' }
try {
    $remoteVariables = $remoteVariablesJson | ConvertFrom-Json
} catch {
    throw 'Railway returned invalid variable JSON; raw output was intentionally suppressed.'
}
$remoteUrlText = [string]$remoteVariables.DATABASE_PUBLIC_URL
if ([string]::IsNullOrWhiteSpace($remoteUrlText)) {
    throw 'DATABASE_PUBLIC_URL is not defined for the Railway PostgreSQL service.'
}
$remoteUri = [Uri]$remoteUrlText
$remoteUserParts = $remoteUri.UserInfo.Split(':', 2)
if ($remoteUserParts.Count -ne 2) { throw 'Railway PostgreSQL URL has invalid user information.' }
$env:PGHOST = $remoteUri.Host
$env:PGPORT = [string]$remoteUri.Port
$env:PGUSER = [Uri]::UnescapeDataString($remoteUserParts[0])
$env:PGPASSWORD = [Uri]::UnescapeDataString($remoteUserParts[1])
$env:PGDATABASE = $remoteUri.AbsolutePath.TrimStart('/')
if ([string]::IsNullOrWhiteSpace($env:PGDATABASE)) { throw 'Railway database name is empty.' }
```

Expected: variables are populated in memory with no database URL printed.

- [ ] **Step 2: Verify the remote identity before backup**

```powershell
$remoteIdentity = & $psql --no-psqlrc --set ON_ERROR_STOP=1 --tuples-only --no-align `
    --command "SELECT current_database() || '|' || current_user || '|' || inet_server_addr()::text;"
if ($LASTEXITCODE -ne 0 -or $remoteIdentity.Count -ne 1) {
    throw 'Unable to verify the Railway PostgreSQL target.'
}
Write-Output "Verified Railway database identity (database|user|server): $remoteIdentity"
```

Expected: the database identity corresponds to the selected Railway PostgreSQL service. It must not show `localhost`.

- [ ] **Step 3: Create the Railway rollback dump**

```powershell
$railwayBackupPath = Join-Path $syncRoot 'railway-before.dump'
& $pgDump --format=custom --no-owner --no-privileges --file $railwayBackupPath
if ($LASTEXITCODE -ne 0) { throw 'Railway rollback dump failed.' }
if (-not (Test-Path -LiteralPath $railwayBackupPath -PathType Leaf)) {
    throw 'Railway rollback dump was not created.'
}
```

Expected: `railway-before.dump` exists and is non-empty.

- [ ] **Step 4: Validate and checksum the rollback dump**

```powershell
$railwayToc = & $pgRestore --list $railwayBackupPath
if ($LASTEXITCODE -ne 0 -or $railwayToc.Count -lt 10) {
    throw 'Railway rollback dump validation failed.'
}
$railwayHash = Get-FileHash -Algorithm SHA256 -LiteralPath $railwayBackupPath
"$($railwayHash.Hash)  railway-before.dump" |
    Set-Content -LiteralPath (Join-Path $syncRoot 'railway-before.dump.sha256')
Write-Output "Validated Railway rollback dump: $($railwayHash.Hash)"
```

Expected: the archive table of contents is readable and a SHA-256 checksum is recorded.

- [ ] **Step 5: Clear the Railway secret from the process environment**

```powershell
Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
$remoteUrlText = $null
$remoteVariablesJson = $null
$remoteVariables = $null
```

### Task 4: Create and Validate the Local Source Dump

**Artifacts:**
- Create outside Git: `local-source.dump`
- Create outside Git: `local-source.dump.sha256`

**Interfaces:**
- Consumes: validated local database configuration from Task 1
- Produces: validated source archive used by Task 5

- [ ] **Step 1: Restore local PostgreSQL environment variables without displaying the password**

```powershell
$localProperties = @{}
Get-Content -LiteralPath $localPropertiesPath | ForEach-Object {
    if ($_ -match '^\s*([^#!][^=]*)=(.*)$') {
        $localProperties[$matches[1].Trim()] = $matches[2]
    }
}
$env:PGPASSWORD = $localProperties['spring.datasource.password']
$env:PGHOST = 'localhost'
$env:PGPORT = '5432'
$env:PGUSER = 'postgres'
$env:PGDATABASE = 'blood_inventory_db'
```

- [ ] **Step 2: Create the local custom-format dump**

```powershell
$localDumpPath = Join-Path $syncRoot 'local-source.dump'
& $pgDump --format=custom --no-owner --no-privileges --file $localDumpPath
if ($LASTEXITCODE -ne 0) { throw 'Local source dump failed.' }
```

Expected: the dump finishes without changing the local database.

- [ ] **Step 3: Validate and checksum the local dump**

```powershell
$localToc = & $pgRestore --list $localDumpPath
if ($LASTEXITCODE -ne 0 -or $localToc.Count -lt 10) {
    throw 'Local source dump validation failed.'
}
$localHash = Get-FileHash -Algorithm SHA256 -LiteralPath $localDumpPath
"$($localHash.Hash)  local-source.dump" |
    Set-Content -LiteralPath (Join-Path $syncRoot 'local-source.dump.sha256')
Write-Output "Validated local source dump: $($localHash.Hash)"
```

- [ ] **Step 4: Clear the local database secret**

```powershell
Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
$localProperties = $null
```

### Task 5: Stop the Application and Replace Railway PostgreSQL

**Artifacts:**
- Modify remotely: Railway application replica count
- Modify remotely: Railway PostgreSQL schema and data

**Interfaces:**
- Consumes: validated rollback/source dumps and exact Railway target IDs
- Produces: Railway database restored from the local source archive

- [ ] **Step 1: Revalidate both archives immediately before destructive work**

```powershell
foreach ($archivePath in @($railwayBackupPath, $localDumpPath)) {
    & $pgRestore --list $archivePath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Archive validation failed: $archivePath" }
}
```

Expected: both archives remain readable.

- [ ] **Step 2: Stop the exact Railway application service**

```powershell
npx --yes @railway/cli@5.44.0 service scale `
    --project $projectId --environment $environmentId --service $appServiceId `
    @appScaleAssignmentsZero --json
if ($LASTEXITCODE -ne 0) { throw 'Unable to stop the Railway application.' }
```

Expected: the exact application service reports zero replicas. Do not continue while an application replica is running.

- [ ] **Step 3: Reload Railway PostgreSQL credentials into memory and revalidate identity**

```powershell
$remoteVariablesJson = npx --yes @railway/cli@5.44.0 variable list `
    --project $projectId --environment $environmentId --service $postgresServiceId --json
if ($LASTEXITCODE -ne 0) { throw 'Unable to reload Railway PostgreSQL variables.' }
try {
    $remoteVariables = $remoteVariablesJson | ConvertFrom-Json
} catch {
    throw 'Railway returned invalid variable JSON; raw output was intentionally suppressed.'
}
$remoteUrlText = [string]$remoteVariables.DATABASE_PUBLIC_URL
if ([string]::IsNullOrWhiteSpace($remoteUrlText)) {
    throw 'DATABASE_PUBLIC_URL is not defined for the Railway PostgreSQL service.'
}
$remoteUri = [Uri]$remoteUrlText
$remoteUserParts = $remoteUri.UserInfo.Split(':', 2)
if ($remoteUserParts.Count -ne 2) { throw 'Railway PostgreSQL URL has invalid user information.' }
$env:PGHOST = $remoteUri.Host
$env:PGPORT = [string]$remoteUri.Port
$env:PGUSER = [Uri]::UnescapeDataString($remoteUserParts[0])
$env:PGPASSWORD = [Uri]::UnescapeDataString($remoteUserParts[1])
$env:PGDATABASE = $remoteUri.AbsolutePath.TrimStart('/')
if ([string]::IsNullOrWhiteSpace($env:PGDATABASE)) { throw 'Railway database name is empty.' }
```

Then run:

```powershell
$remoteIdentityBeforeRestore = & $psql --no-psqlrc --set ON_ERROR_STOP=1 `
    --tuples-only --no-align --command "SELECT current_database() || '|' || current_user || '|' || inet_server_addr()::text;"
if ($LASTEXITCODE -ne 0 -or $remoteIdentityBeforeRestore -ne $remoteIdentity) {
    throw 'Railway database identity changed after the rollback backup.'
}
```

Expected: the exact identity matches Task 3.

- [ ] **Step 4: Restore the local archive with clean/replace semantics**

```powershell
& $pgRestore --clean --if-exists --no-owner --no-privileges --exit-on-error `
    --dbname $env:PGDATABASE $localDumpPath
$sourceRestoreExitCode = $LASTEXITCODE
if ($sourceRestoreExitCode -ne 0) {
    Write-Error 'Source restore failed; execute Task 8 rollback immediately.'
    throw "Source restore failed with exit code $sourceRestoreExitCode"
}
```

Expected: schema, Flyway history, sequences, functions, triggers, and data are restored. Any non-zero exit code triggers rollback; do not layer another restore attempt on top.

### Task 6: Clear Runtime Authentication State and Verify Database Parity

**Artifacts:**
- Modify remotely: transient authentication tables only
- Create outside Git: `railway-post-restore.txt`

**Interfaces:**
- Consumes: restored Railway database and local preflight evidence
- Produces: database-level acceptance evidence required before application restart

- [ ] **Step 1: Clear copied transient authentication state in one transaction**

```powershell
$authCleanupSql = @'
BEGIN;
TRUNCATE TABLE staff_login_session, authentication_throttle;
COMMIT;
'@
& $psql --no-psqlrc --set ON_ERROR_STOP=1 --command $authCleanupSql
if ($LASTEXITCODE -ne 0) {
    Write-Error 'Authentication-state cleanup failed; execute Task 8 rollback.'
    throw 'Authentication-state cleanup failed.'
}
```

Expected: only stale sessions and login-attempt buckets are removed.

- [ ] **Step 2: Capture non-secret Railway post-restore evidence**

```powershell
$railwayPostRestorePath = Join-Path $syncRoot 'railway-post-restore.txt'
& $psql --no-psqlrc --set ON_ERROR_STOP=1 --tuples-only --no-align `
    --command $localPreflightSql | Set-Content -LiteralPath $railwayPostRestorePath
if ($LASTEXITCODE -ne 0) {
    Write-Error 'Railway verification query failed; execute Task 8 rollback.'
    throw 'Railway verification query failed.'
}
$railwayEvidence = Get-Content -LiteralPath $railwayPostRestorePath
$railwayEvidence
```

- [ ] **Step 3: Compare local and Railway evidence exactly**

```powershell
$localComparable = Get-Content -LiteralPath $localPreflightPath |
    Where-Object { $_ -notlike 'server_version=*' -and $_ -notlike 'database=*' }
$railwayComparable = Get-Content -LiteralPath $railwayPostRestorePath |
    Where-Object { $_ -notlike 'server_version=*' -and $_ -notlike 'database=*' }
$evidenceDifference = Compare-Object $localComparable $railwayComparable
if ($evidenceDifference) {
    $evidenceDifference | Format-Table
    Write-Error 'Local/Railway evidence differs; execute Task 8 rollback.'
    throw 'Restored data does not match the local source evidence.'
}
```

Expected: Flyway version, representative counts, and `admin_ready=true` match exactly.

- [ ] **Step 4: Clear database credentials from memory**

```powershell
Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
$remoteUrlText = $null
$remoteVariablesJson = $null
$remoteVariables = $null
```

### Task 7: Configure, Restart, and Verify Public Login

**Artifacts:**
- Modify remotely: Railway application variables and replica count
- Create outside Git: final non-secret verification notes

**Interfaces:**
- Consumes: database parity evidence and original scale configuration
- Produces: healthy deployment and verified public admin login

- [ ] **Step 1: Set both production HTTP variables in one Railway deployment change**

```powershell
npx --yes @railway/cli@5.44.0 variable set `
    FORWARD_HEADERS_STRATEGY=framework SESSION_COOKIE_SECURE=true `
    --project $projectId --environment $environmentId --service $appServiceId --json
if ($LASTEXITCODE -ne 0) {
    Write-Error 'Railway variable update failed; execute Task 8 rollback.'
    throw 'Railway variable update failed.'
}
```

Expected: one new deployment is triggered with both variables; the command output contains no database credentials.

- [ ] **Step 2: Restore the original application replica count**

```powershell
npx --yes @railway/cli@5.44.0 service scale `
    --project $projectId --environment $environmentId --service $appServiceId `
    @appScaleAssignmentsOriginal --json
if ($LASTEXITCODE -ne 0) {
    Write-Error 'Application restart failed; execute Task 8 rollback.'
    throw 'Application restart failed.'
}
```

- [ ] **Step 3: Wait for a successful deployment with bounded checks**

```powershell
$deploymentReady = $false
for ($attempt = 1; $attempt -le 30; $attempt++) {
    $deploymentJson = npx --yes @railway/cli@5.44.0 deployment list `
        --project $projectId --environment $environmentId --service $appServiceId --json
    if ($LASTEXITCODE -ne 0) { throw 'Unable to query Railway deployments.' }
    $deployments = $deploymentJson | ConvertFrom-Json
    $latestDeployment = @($deployments)[0]
    if ($latestDeployment.status -eq 'SUCCESS') {
        $deploymentReady = $true
        break
    }
    if ($latestDeployment.status -in @('FAILED', 'CRASHED', 'REMOVED')) {
        throw "Railway deployment ended with status $($latestDeployment.status); execute Task 8 rollback."
    }
    Start-Sleep -Seconds 10
}
if (-not $deploymentReady) { throw 'Railway deployment did not become healthy within five minutes.' }
```

Expected: the latest deployment reaches `SUCCESS` within five minutes.

- [ ] **Step 4: Inspect bounded startup logs for validation errors**

```powershell
$startupLogs = npx --yes @railway/cli@5.44.0 logs `
    --project $projectId --environment $environmentId --service $appServiceId `
    --deployment --since 10m --lines 300
if ($LASTEXITCODE -ne 0) { throw 'Unable to retrieve Railway startup logs.' }
$startupLogs | Select-String -Pattern 'Flyway|Tomcat started|ERROR|Exception|validation'
if ($startupLogs -match 'Flyway.*(checksum|validation).*fail|Schema-validation.*missing|APPLICATION FAILED TO START') {
    throw 'Startup logs contain a database validation failure; execute Task 8 rollback.'
}
```

Expected: startup succeeds without Flyway checksum or Hibernate schema validation failures.

- [ ] **Step 5: Verify public login in a fresh browser session**

Open `https://bloodinventory-production-8ac9.up.railway.app/login` in an Incognito/InPrivate window. Enter `admin` and the local administrator password through the browser; do not paste the password into a shell command or record it in evidence.

Expected:

- the login POST does not return HTTP 429;
- the response does not redirect to `/login?error` or `/login?throttled`;
- the browser reaches `/admin/dashboard`;
- the `JSESSIONID` cookie is `Secure`, `HttpOnly`, and `SameSite=Strict`.

- [ ] **Step 6: Retain rollback evidence and remove only the source dump after user confirmation**

Report `$syncRoot`, both SHA-256 checksums, non-secret parity evidence, deployment status, and login result. Keep `railway-before.dump` until the user explicitly authorizes deletion. Do not delete either dump in the same turn as the migration unless the user separately approves removal.

### Task 8: Roll Back on Any Post-Mutation Failure

**Artifacts:**
- Restore remotely: pre-change Railway database
- Retain outside Git: both dump archives and failure evidence

**Interfaces:**
- Consumes: validated `railway-before.dump` and exact Railway target identity
- Produces: Railway returned to its pre-migration database state

- [ ] **Step 1: Keep or return the application to zero replicas**

```powershell
npx --yes @railway/cli@5.44.0 service scale `
    --project $projectId --environment $environmentId --service $appServiceId `
    @appScaleAssignmentsZero --json
if ($LASTEXITCODE -ne 0) { throw 'Unable to stop the application for rollback.' }
```

- [ ] **Step 2: Reload Railway PostgreSQL variables and verify the exact identity**

```powershell
$remoteVariablesJson = npx --yes @railway/cli@5.44.0 variable list `
    --project $projectId --environment $environmentId --service $postgresServiceId --json
if ($LASTEXITCODE -ne 0) { throw 'Unable to reload Railway PostgreSQL variables for rollback.' }
try {
    $remoteVariables = $remoteVariablesJson | ConvertFrom-Json
} catch {
    throw 'Railway returned invalid variable JSON; raw output was intentionally suppressed.'
}
$remoteUrlText = [string]$remoteVariables.DATABASE_PUBLIC_URL
if ([string]::IsNullOrWhiteSpace($remoteUrlText)) {
    throw 'DATABASE_PUBLIC_URL is not defined for the Railway PostgreSQL service.'
}
$remoteUri = [Uri]$remoteUrlText
$remoteUserParts = $remoteUri.UserInfo.Split(':', 2)
if ($remoteUserParts.Count -ne 2) { throw 'Railway PostgreSQL URL has invalid user information.' }
$env:PGHOST = $remoteUri.Host
$env:PGPORT = [string]$remoteUri.Port
$env:PGUSER = [Uri]::UnescapeDataString($remoteUserParts[0])
$env:PGPASSWORD = [Uri]::UnescapeDataString($remoteUserParts[1])
$env:PGDATABASE = $remoteUri.AbsolutePath.TrimStart('/')
if ([string]::IsNullOrWhiteSpace($env:PGDATABASE)) { throw 'Railway database name is empty.' }

$rollbackIdentity = & $psql --no-psqlrc --set ON_ERROR_STOP=1 `
    --tuples-only --no-align --command "SELECT current_database() || '|' || current_user || '|' || inet_server_addr()::text;"
if ($LASTEXITCODE -ne 0 -or $rollbackIdentity -ne $remoteIdentity) {
    throw 'Railway database identity does not match the validated rollback target.'
}
```

- [ ] **Step 3: Restore the validated Railway rollback archive**

```powershell
& $pgRestore --list $railwayBackupPath | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Rollback archive is no longer readable.' }
& $pgRestore --clean --if-exists --no-owner --no-privileges --exit-on-error `
    --dbname $env:PGDATABASE $railwayBackupPath
if ($LASTEXITCODE -ne 0) {
    throw 'Rollback restore failed; keep the application stopped and escalate with both validated dumps retained.'
}
```

- [ ] **Step 4: Clear secrets, restore replicas, and verify pre-migration health**

```powershell
Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
npx --yes @railway/cli@5.44.0 service scale `
    --project $projectId --environment $environmentId --service $appServiceId `
    @appScaleAssignmentsOriginal --json
if ($LASTEXITCODE -ne 0) { throw 'Database rollback succeeded but application restart failed.' }
```

Expected: Railway returns to its pre-migration data state. Retain artifacts and report the exact failing stage before any new repair attempt.
