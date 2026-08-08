# Security Policy

## Reporting

Report vulnerabilities privately through GitHub Security Advisories. Do not open public issues for credentials, authorization bypasses, destructive migration behavior, or data exposure.

## Secret handling

The values in `.env.example` are local demonstration credentials. Never reuse them in a shared environment.

Do not commit environment files, cloud credentials, service-account JSON, access tokens, database dumps, state captures, or migration failure reports. Rotate any credential that has ever appeared in Git history before making the repository public.

## Public release gate

Before changing repository visibility:

1. Run a full-history secret scan.
2. Revoke every identified credential.
3. Rewrite Git history to remove secret material.
4. Clone the rewritten repository into a clean directory.
5. Repeat the full-history scan.
6. Run the quality and end-to-end workflows.

Deleting a secret from the current tree does not remove it from earlier commits.

## Supported versions

Security fixes are applied to the default branch. Images and build dependencies are pinned and reviewed through automated dependency pull requests.
