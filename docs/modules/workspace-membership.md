# Workspace membership

P1.2 adds the internal membership lookup used by the future identity boundary. The default profile still runs the local health service. Use the `postgres` profile for workspace persistence.

## Prepare a database

Use a dedicated PostgreSQL 18 database. In that database, run the following once as its administrator. Role names are cluster-wide. Assign the login passwords through your normal secret mechanism (in psql, use `\password zincc_migrator` and `\password zincc_service`).

```sql
CREATE ROLE zincc_reader NOLOGIN;
CREATE ROLE zincc_migrator LOGIN;
CREATE ROLE zincc_service LOGIN IN ROLE zincc_reader;
CREATE SCHEMA zincc AUTHORIZATION zincc_migrator;
```

Grant both logins `CONNECT` on this database if your deployment revokes the default connect privilege. Keep the service account out of the owner and administrative roles.

## Migrate and run

1. In the migration environment, set `FLYWAY_URL` to the PostgreSQL JDBC URL, `FLYWAY_USER` to `zincc_migrator`, and `FLYWAY_PASSWORD` to its secret. From the repository root run `.\mvnw.cmd flyway:migrate flyway:validate`. Flyway records versions and checksums in `zincc.flyway_schema_history`.
2. In a separate application environment set `SPRING_PROFILES_ACTIVE=postgres`, `ZINCC_DB_URL`, `ZINCC_DB_USER=zincc_service`, and `ZINCC_DB_PASSWORD`. Run the [packaged service](../../README.md). At startup Zincc checks that the runtime login can read both tables with row security active.
3. Provision internal principals, organizations, workspaces and their memberships in an owner transaction. Each membership pairs a workspace UUID with a principal UUID and carries the role `owner` or `member`. The service account has read access to its scoped rows. P1.3 supplies the authenticated principal. P1.2 introduces no public membership API.

## Recovery

V1 runs in a PostgreSQL transaction. On failure, inspect the error and Flyway history. Correct the prerequisite or the unapplied migration and retry. Preserve applied scripts and checksums. For a change that has already reached an environment, ship a new forward migration. Before a destructive change, qualify a backup and restore separately. The integration test deliberately fails a later DDL migration, checks rollback, retries a corrected script and reads the original membership.
