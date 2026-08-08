# Production Cutover

The Compose stack is a migration proof environment. Production execution must reuse the guarded workflow while replacing local infrastructure and credentials.

## Required controls

- Immutable inventory of namespaces and tables in scope
- Confirmed Iceberg format and engine compatibility
- Read-only window covering every source writer
- Verification that no Iceberg commit is in progress
- Backups of all catalog databases
- Versioned copy of every current Iceberg metadata JSON location
- Durable object-store versioning and retention policy
- Separate source and target service identities
- TLS for catalog, database, and object-store traffic
- Central secret management with short-lived credentials
- Approved rollback owner and decision deadline

## Rehearsal

Restore production metadata and representative object data into an isolated environment. Run the exact pinned migrator image and selector. Record duration, throughput, failures, and validation evidence.

The rehearsal is complete only when the target fingerprint equals the source baseline and the old registrations are absent.

## Cutover sequence

1. Announce the write freeze and stop all known writers.
2. Verify catalog and object-store request logs are quiet.
3. Export the approved identifier manifest.
4. Capture the source state and current metadata locations.
5. Run dry-run and compare its output to the manifest.
6. Execute migration with the same pinned image and selector.
7. Validate target state against the immutable baseline.
8. Confirm source registrations were removed.
9. Move a read-only canary to the target.
10. Move writers, then readers, to the target.
11. Monitor commit failures, authorization errors, and object-store access.
12. Close the freeze only after the acceptance window passes.

## Rollback boundary

Before target writers start, rollback can restore source registrations to the captured metadata locations after removing target registrations.

After any target writer commits, do not restore the old source baseline. The current target metadata location is authoritative and must be used in a separately reviewed recovery plan.

## Acceptance evidence

Retain these artifacts for every cutover:

- Approved table manifest
- Dry-run output
- Source and target state JSON
- Migrator logs
- Failed identifier reports
- Database backup identifiers
- Object-store versioning evidence
- Cutover timestamps and approver identities

