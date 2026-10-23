# PostgreSQL backup and recovery runbook

## Current verification status

The repository configures PostgreSQL, but no scheduled backup job, retained backup
artifact, or completed restore test was found. The Admin Dashboard therefore reports
backups as **UNVERIFIED**. Do not treat a timestamp supplied through application
configuration as proof that a backup is valid.

Before production use, the operator must configure backups for the actual PostgreSQL
host, define retention and recovery-point objectives, restrict access to backup
storage, and run the restore test below in an isolated environment. Keep the
application's data-encryption key separately in an approved secret-management
system: a database backup without that key may not make encrypted driver documents
or complaint evidence recoverable. Never put credentials or encryption keys in this
runbook, shell history, application logs, or the backup itself.

## Create a backup

Use a trusted `pg_dump` installation compatible with the server version. Set
`PGHOST`, `PGPORT`, `PGDATABASE`, and `PGUSER` in the operator's protected session
according to the deployment configuration. Do not place a database password in the
command line; use the organization's approved PostgreSQL credential prompt or
protected password file.

```powershell
$backupFile = "D:\secure-backups\mizigo-$(Get-Date -Format 'yyyyMMdd-HHmmss').dump"
pg_dump --format=custom --no-owner --file="$backupFile" "$env:PGDATABASE"
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed; retain the error for operations review." }
Get-FileHash -Algorithm SHA256 $backupFile
```

Restrict backup file and directory permissions and transfer the artifact to the
approved encrypted, access-controlled backup store. Record the artifact identifier,
hash, time, database, and operator in the operations system (not in application
logs). Confirm that the artifact is retained and readable from the recovery
environment.

## Verify and restore into an isolated database

Never test restore against the production database. Provision a fresh, isolated
recovery database, set the PostgreSQL connection environment variables for that
database, and make sure `PGDATABASE` names the isolated target before continuing.

```powershell
pg_restore --list "$backupFile" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Backup archive is unreadable." }
pg_restore --no-owner --exit-on-error --dbname="$env:PGDATABASE" "$backupFile"
if ($LASTEXITCODE -ne 0) { throw "Restore failed; preserve the isolated environment for diagnosis." }
```

After restore, verify schema and expected table counts, start the application against
the isolated database with the separately retrieved encryption key, and run the
approved smoke checks for authentication, shipment history, driver document
decryption, and complaint evidence access. Record the results and duration. A
successful `pg_restore` alone is not a completed recovery test. Destroy the isolated
recovery environment only through the normal infrastructure change process.

## Recovery

1. Declare the incident and identify the required recovery point with the service
   owner. Do not overwrite the affected database while investigation is active.
2. Select the latest retained backup that passed archive and restore verification;
   verify its recorded hash and obtain the encryption key from the approved secret
   manager.
3. Restore to a new database or recovery host using the isolated restore procedure.
   Validate application health and business data before directing traffic to it.
4. Obtain operational approval before changing production connection settings or
   promoting the recovered database. Retain the old database and incident evidence
   until the incident owner authorizes cleanup.
5. Record the chosen recovery point, data-loss window, validation results, and
   approvals in the incident record.

This repository does not currently automate backup creation, artifact verification,
restore testing, or production failover. Configure and test those operational
controls for the deployment environment before claiming a verified recovery plan.
