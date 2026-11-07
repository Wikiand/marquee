package com.marquee.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MigrationRunner {

    private record Migration(
            int version,
            String description,
            List<String> statements
    ) {
    }

    /*
     * The original schema expected before applying any pending migrations.
     * Keep this separate from the latest schema so older databases can
     * pass validation and receive their pending migrations.
     */
    private static final Map<String, Set<String>> BASELINE_EXPECTED_COLUMNS =
            Map.of(
                "films", Set.of(
                    "film_id",
                    "title",
                    "year",
                    "description",
                    "created_at"
                ),
                "genres", Set.of(
                    "genre_id",
                    "name"
                ),
                "film_genres", Set.of(
                    "film_id",
                    "genre_id"
                ),
                "screenings", Set.of(
                    "screening_id",
                    "film_id",
                    "venue",
                    "screening_time"
                )
            );

    /*
     * The complete schema after every migration has been applied.
     */
    private static final Map<String, Set<String>> LATEST_EXPECTED_COLUMNS =
            Map.of(
                "films", Set.of(
                    "film_id",
                    "title",
                    "year",
                    "description",
                    "created_at"
                ),
                "genres", Set.of(
                    "genre_id",
                    "name"
                ),
                "film_genres", Set.of(
                    "film_id",
                    "genre_id"
                ),
                "screenings", Set.of(
                    "screening_id",
                    "film_id",
                    "venue",
                    "screening_time",
                    "room"
                )
            );

    private static final List<Migration> MIGRATIONS = List.of(
        new Migration(
            1,
            "Create films and genres tables",
            List.of(
                """
                CREATE TABLE IF NOT EXISTS films (
                    film_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL CHECK (length(trim(title)) > 0),
                    year INTEGER NOT NULL CHECK (year > 0),
                    description TEXT,
                    created_at TEXT NOT NULL DEFAULT
                        (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS genres (
                    genre_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE
                        CHECK (length(trim(name)) > 0)
                )
                """
            )
        ),
        new Migration(
            2,
            "Create film genres relationship table",
            List.of(
                """
                CREATE TABLE IF NOT EXISTS film_genres (
                    film_id INTEGER NOT NULL,
                    genre_id INTEGER NOT NULL,
                    PRIMARY KEY (film_id, genre_id),
                    FOREIGN KEY (film_id)
                        REFERENCES films(film_id) ON DELETE CASCADE,
                    FOREIGN KEY (genre_id)
                        REFERENCES genres(genre_id) ON DELETE CASCADE
                )
                """
            )
        ),
        new Migration(
            3,
            "Create screenings table",
            List.of(
                """
                CREATE TABLE IF NOT EXISTS screenings (
                    screening_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    film_id INTEGER NOT NULL,
                    venue TEXT NOT NULL
                        CHECK (length(trim(venue)) > 0),
                    screening_time TEXT NOT NULL,
                    FOREIGN KEY (film_id)
                        REFERENCES films(film_id) ON DELETE CASCADE
                )
                """
            )
        ),
        new Migration(
            4,
            "Create database indexes",
            List.of(
                """
                CREATE INDEX IF NOT EXISTS idx_film_genres_genre
                ON film_genres(genre_id, film_id)
                """,
                """
                CREATE INDEX IF NOT EXISTS idx_screenings_film
                ON screenings(film_id)
                """
            )
        ),
        new Migration(
            5,
            "Add room to screenings",
            List.of(
                """
                ALTER TABLE screenings
                ADD COLUMN room TEXT
                """
            )
        )
    );

    public void migrate(Connection connection) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();

        try {
            connection.setAutoCommit(false);

            validateExistingSchema(connection);
            createMigrationTable(connection);

            Set<Integer> appliedVersions = getAppliedVersions(connection);

            validateMigrationHistory(appliedVersions);

            for (Migration migration : MIGRATIONS) {
                if (!appliedVersions.contains(migration.version())) {
                    applyMigration(connection, migration);
                }
            }

            validateCompleteSchema(connection);

        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    /*
     * Existing databases only need to satisfy the original schema.
     * Pending migrations are responsible for bringing them forward.
     */
    private void validateExistingSchema(Connection connection)
            throws SQLException {

        for (Map.Entry<String, Set<String>> entry
                : BASELINE_EXPECTED_COLUMNS.entrySet()) {

            String table = entry.getKey();

            if (databaseObjectExists(connection, table)) {
                validateTableColumns(
                    connection,
                    table,
                    entry.getValue()
                );
            }
        }
    }

    /*
     * Once migrations finish, every column in the latest schema must exist.
     */
    private void validateCompleteSchema(Connection connection)
            throws SQLException {

        for (Map.Entry<String, Set<String>> entry
                : LATEST_EXPECTED_COLUMNS.entrySet()) {

            validateTableColumns(
                connection,
                entry.getKey(),
                entry.getValue()
            );
        }
    }

    private boolean databaseObjectExists(
            Connection connection,
            String name
    ) throws SQLException {

        String sql = """
            SELECT type
            FROM sqlite_master
            WHERE name = ?
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setString(1, name);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return false;
                }

                String type = resultSet.getString("type");

                if (!"table".equals(type)) {
                    throw new SQLException(
                        "Expected database object '" + name
                            + "' to be a table, but found: " + type
                    );
                }

                return true;
            }
        }
    }

    private void validateTableColumns(
            Connection connection,
            String table,
            Set<String> expectedColumns
    ) throws SQLException {

        Set<String> actualColumns = new HashSet<>();

        String sql = "PRAGMA table_info('" + table + "')";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                actualColumns.add(resultSet.getString("name"));
            }
        }

        if (actualColumns.isEmpty()) {
            throw new SQLException(
                "Database table '" + table + "' does not exist "
                    + "or has no columns"
            );
        }

        Set<String> missingColumns = new HashSet<>(expectedColumns);
        missingColumns.removeAll(actualColumns);

        if (!missingColumns.isEmpty()) {
            throw new SQLException(
                "Database table '" + table
                    + "' has an incompatible schema. Missing columns: "
                    + missingColumns
            );
        }
    }

    private void createMigrationTable(Connection connection)
            throws SQLException {

        String sql = """
            CREATE TABLE IF NOT EXISTS schema_migrations (
                version INTEGER PRIMARY KEY,
                description TEXT NOT NULL,
                applied_at TEXT NOT NULL DEFAULT
                    (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
            )
            """;

        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            connection.commit();
        } catch (SQLException exception) {
            rollback(connection, exception);
            throw exception;
        }
    }

    private Set<Integer> getAppliedVersions(Connection connection)
            throws SQLException {

        Set<Integer> versions = new HashSet<>();

        String sql = """
            SELECT version
            FROM schema_migrations
            ORDER BY version
            """;

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                versions.add(resultSet.getInt("version"));
            }
        }

        return versions;
    }

    private void validateMigrationHistory(Set<Integer> appliedVersions)
            throws SQLException {

        int latestVersion = MIGRATIONS.get(
            MIGRATIONS.size() - 1
        ).version();

        for (int version : appliedVersions) {
            if (version > latestVersion || version <= 0) {
                throw new SQLException(
                    "Database contains unsupported migration version: "
                        + version
                );
            }
        }

        boolean missingEarlierVersion = false;

        for (Migration migration : MIGRATIONS) {
            int version = migration.version();

            if (!appliedVersions.contains(version)) {
                missingEarlierVersion = true;
            } else if (missingEarlierVersion) {
                throw new SQLException(
                    "Database migration history is incomplete at version "
                        + version
                );
            }
        }
    }

    private void applyMigration(
            Connection connection,
            Migration migration
    ) throws SQLException {

        try {
            try (Statement statement = connection.createStatement()) {
                for (String sql : migration.statements()) {
                    statement.execute(sql);
                }
            }

            String insertSql = """
                INSERT INTO schema_migrations (version, description)
                VALUES (?, ?)
                """;

            try (PreparedStatement statement =
                    connection.prepareStatement(insertSql)) {

                statement.setInt(1, migration.version());
                statement.setString(2, migration.description());
                statement.executeUpdate();
            }

            connection.commit();

        } catch (SQLException exception) {
            rollback(connection, exception);
            throw exception;
        }
    }

    private void rollback(
            Connection connection,
            SQLException originalException
    ) {

        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }
}
