# ADR-001: Versioned SQL migrations

- Status: Accepted
- Decision area: Database schema management

## Context

The application needs to initialize new SQLite databases and evolve
existing databases without discarding application data. Schema changes
must be repeatable, ordered, and traceable.

Embedding migration SQL directly in Java makes schema changes harder
to review and maintain independently of application logic.

## Decision

Use numbered SQL migration resources stored in the root `migrations/`
directory.

The current migration sequence is:

1. `001_create_films_and_genres.sql`
2. `002_create_film_genres.sql`
3. `003_create_screenings.sql`
4. `004_create_database_indexes.sql`
5. `005_add_screening_room.sql`

Maven packages these files under the `migrations/` classpath directory.
`MigrationRunner` reads the resources and executes unapplied migrations
in version order.

Applied versions are recorded in `schema_migrations`. Each migration
and its history record are committed together. The runner validates
migration history and checks the resulting schema.

## Consequences

- Fresh databases can be initialized through the same migration sequence.
- Existing databases can be upgraded without discarding their records.
- Successfully applied migrations are not executed again.
- Migration history makes schema evolution traceable.
- Applied migration files must be treated as immutable.
- Future schema changes require a new, higher-numbered migration.
- Migration resources must remain available on the runtime classpath.

## Verification

Migration behavior is covered by `MigrationRunnerTest`, including fresh
initialization, repeat initialization, legacy database adoption,
schema evolution, data preservation, migration history validation,
and failure rollback.
