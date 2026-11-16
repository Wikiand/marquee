package com.marquee.database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MigrationRunner {

    private record Migration(
            int version,
            String description,
            String resourcePath
    ) {
    }

    private static final Map<String, Set<String>>
            BASELINE_EXPECTED_COLUMNS = Map.of(
                "films", Set.of(
                    "film_id", "title", "year",
                    "description", "created_at"
                ),
                "genres", Set.of("genre_id", "name"),
                "film_genres", Set.of("film_id", "genre_id"),
                "screenings", Set.of(
                    "screening_id", "film_id",
                    "venue", "screening_time"
                )
            );

    private static final Map<String, Set<String>>
            LATEST_EXPECTED_COLUMNS = Map.of(
                "films", Set.of(
                    "film_id", "title", "year",
                    "description", "created_at"
                ),
                "genres", Set.of("genre_id", "name"),
                "film_genres", Set.of("film_id", "genre_id"),
                "screenings", Set.of(
                    "screening_id", "film_id",
                    "venue", "screening_time", "room"
                )
            );

    private static final List<Migration> MIGRATIONS = List.of(
        new Migration(
            1,
            "Create films and genres tables",
            "migrations/001_create_films_and_genres.sql"
        ),
        new Migration(
            2,
            "Create film genres relationship table",
            "migrations/002_create_film_genres.sql"
        ),
        new Migration(
            3,
            "Create screenings table",
            "migrations/003_create_screenings.sql"
        ),
        new Migration(
            4,
            "Create database indexes",
            "migrations/004_create_database_indexes.sql"
        ),
        new Migration(
            5,
            "Add room to screenings",
            "migrations/005_add_screening_room.sql"
        )
    );

    public void migrate(Connection connection) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();

        try {
            connection.setAutoCommit(false);

            validateExistingSchema(connection);
            createMigrationTable(connection);

            Set<Integer> appliedVersions =
                getAppliedVersions(connection);

            validateMigrationHistory(appliedVersions);

            for (Migration migration : MIGRATIONS) {
                if (!appliedVersions.contains(migration.version())) {
                    applyMigration(connection, migration);
                }
            }

            validateCompleteSchema(connection);

        } catch (SQLException exception) {
            rollback(connection, exception);
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private void validateExistingSchema(Connection connection)
            throws SQLException {

        for (Map.Entry<String, Set<String>> entry
                : BASELINE_EXPECTED_COLUMNS.entrySet()) {

            String table = entry.getKey();

            if (databaseObjectExists(connection, table)) {
                validateTableColumns(
                    connection, table, entry.getValue()
                );
            }
        }
    }

    private void validateCompleteSchema(Connection connection)
            throws SQLException {

        for (Map.Entry<String, Set<String>> entry
                : LATEST_EXPECTED_COLUMNS.entrySet()) {

            validateTableColumns(
                connection, entry.getKey(), entry.getValue()
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

        // Table names come exclusively from the fixed internal schema map.
        String sql = "PRAGMA table_info('" + table + "')";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                actualColumns.add(resultSet.getString("name"));
            }
        }

        if (actualColumns.isEmpty()) {
            throw new SQLException(
                "Database table '" + table
                    + "' does not exist or has no columns"
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

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                 SELECT version
                 FROM schema_migrations
                 ORDER BY version
                 """)) {

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
            if (version <= 0 || version > latestVersion) {
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
                for (String sql : readMigrationStatements(migration)) {
                    statement.execute(sql);
                }
            }

            try (PreparedStatement statement =
                    connection.prepareStatement("""
                        INSERT INTO schema_migrations (
                            version, description
                        )
                        VALUES (?, ?)
                        """)) {

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

    private List<String> readMigrationStatements(Migration migration)
            throws SQLException {

        try (InputStream input = MigrationRunner.class
                .getClassLoader()
                .getResourceAsStream(migration.resourcePath())) {

            if (input == null) {
                throw new SQLException(
                    "Migration resource not found: "
                        + migration.resourcePath()
                );
            }

            String script = new String(
                input.readAllBytes(),
                StandardCharsets.UTF_8
            );

            List<String> statements = splitSqlStatements(script);

            if (statements.isEmpty()) {
                throw new SQLException(
                    "Migration contains no SQL statements: "
                        + migration.resourcePath()
                );
            }

            return statements;

        } catch (IOException exception) {
            throw new SQLException(
                "Could not read migration resource: "
                    + migration.resourcePath(),
                exception
            );
        }
    }

    /*
     * Splits SQL statements while respecting quoted values,
     * quoted identifiers, bracket identifiers and comments.
     */
    static List<String> splitSqlStatements(String script)
            throws SQLException {

        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        char quote = 0;
        boolean bracketIdentifier = false;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = 0; i < script.length(); i++) {
            char character = script.charAt(i);
            char next = i + 1 < script.length()
                ? script.charAt(i + 1)
                : 0;

            if (lineComment) {
                current.append(character);

                if (character == '\n' || character == '\r') {
                    lineComment = false;
                }

                continue;
            }

            if (blockComment) {
                current.append(character);

                if (character == '*' && next == '/') {
                    current.append(next);
                    i++;
                    blockComment = false;
                }

                continue;
            }

            if (quote != 0) {
                current.append(character);

                if (character == quote) {
                    if (next == quote) {
                        current.append(next);
                        i++;
                    } else {
                        quote = 0;
                    }
                }

                continue;
            }

            if (bracketIdentifier) {
                current.append(character);

                if (character == ']') {
                    if (next == ']') {
                        current.append(next);
                        i++;
                    } else {
                        bracketIdentifier = false;
                    }
                }

                continue;
            }

            if (character == '-' && next == '-') {
                current.append(character).append(next);
                i++;
                lineComment = true;
                continue;
            }

            if (character == '/' && next == '*') {
                current.append(character).append(next);
                i++;
                blockComment = true;
                continue;
            }

            if (character == '\'' || character == '"'
                    || character == '`') {
                quote = character;
                current.append(character);
                continue;
            }

            if (character == '[') {
                bracketIdentifier = true;
                current.append(character);
                continue;
            }

            if (character == ';') {
                String statement = current.toString().trim();

                if (!statement.isEmpty()) {
                    statements.add(statement);
                }

                current.setLength(0);
                continue;
            }

            current.append(character);
        }

        if (quote != 0 || bracketIdentifier || blockComment) {
            throw new SQLException(
                "Migration contains an unterminated SQL quote or comment"
            );
        }

        String remaining = current.toString().trim();

        if (!remaining.isEmpty()) {
            statements.add(remaining);
        }

        return statements;
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
