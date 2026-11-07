
package com.marquee.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MigrationRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void freshDatabaseAppliesAllMigrations() throws Exception {
        Database database = new Database(tempDir.resolve("fresh.db"));

        database.initialize();

        assertEquals(
            List.of(1, 2, 3, 4, 5),
            appliedVersions(database)
        );

        assertTrue(objectExists(database, "table", "films"));
        assertTrue(objectExists(database, "table", "genres"));
        assertTrue(objectExists(database, "table", "film_genres"));
        assertTrue(objectExists(database, "table", "screenings"));

        assertTrue(objectExists(
            database, "index", "idx_film_genres_genre"
        ));

        assertTrue(objectExists(
            database, "index", "idx_screenings_film"
        ));
    }

    @Test
    void repeatedInitializationDoesNotDuplicateMigrations()
            throws Exception {

        Database database = new Database(
            tempDir.resolve("repeated.db")
        );

        database.initialize();

        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                INSERT INTO films (title, year)
                VALUES ('Persistent Film', 2025)
                """);
        }

        database.initialize();
        database.initialize();

        assertEquals(
            List.of(1, 2, 3, 4, 5),
            appliedVersions(database)
        );

        assertEquals(1, countRows(database, "films"));
    }

    @Test
    void adoptsLegacyDatabaseWithoutLosingRecords()
            throws Exception {

        Path databasePath = tempDir.resolve("legacy.db");

        createLegacyDatabase(databasePath);

        Database database = new Database(databasePath);
        database.initialize();

        assertEquals(
            List.of(1, 2, 3, 4, 5),
            appliedVersions(database)
        );

        assertEquals(1, countRows(database, "films"));
        assertEquals(1, countRows(database, "genres"));
        assertEquals(1, countRows(database, "film_genres"));
        assertEquals(1, countRows(database, "screenings"));

        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                 SELECT title, year
                 FROM films
                 WHERE film_id = 7
                 """)) {

            assertTrue(resultSet.next());
            assertEquals("Legacy Film", resultSet.getString("title"));
            assertEquals(2020, resultSet.getInt("year"));
        }
    }


    @Test
    void appliesEvolutionMigrationToExistingVersionFourDatabase()
            throws Exception {

        Path databasePath = tempDir.resolve("version-four.db");

        createVersionFourDatabase(databasePath);

        Database database = new Database(databasePath);
        database.initialize();

        assertEquals(
            List.of(1, 2, 3, 4, 5),
            appliedVersions(database)
        );

        assertEquals(1, countRows(database, "films"));
        assertEquals(1, countRows(database, "genres"));
        assertEquals(1, countRows(database, "film_genres"));
        assertEquals(1, countRows(database, "screenings"));

        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                 SELECT film_id, venue, screening_time, room
                 FROM screenings
                 WHERE screening_id = 5
                 """)) {

            assertTrue(resultSet.next());
            assertEquals(7, resultSet.getInt("film_id"));
            assertEquals("Nairobi Cinema", resultSet.getString("venue"));
            assertEquals(
                "2026-11-07T18:00:00",
                resultSet.getString("screening_time")
            );
            assertNull(resultSet.getString("room"));
        }

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                    connection.prepareStatement("""
                        INSERT INTO screenings (
                            film_id, venue, screening_time, room
                        )
                        VALUES (?, ?, ?, ?)
                        """)) {

            statement.setInt(1, 7);
            statement.setString(2, "Nairobi Cinema");
            statement.setString(3, "2026-11-08T19:00:00");
            statement.setString(4, "Screen 2");

            assertEquals(1, statement.executeUpdate());
        }

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                    connection.prepareStatement("""
                        SELECT room
                        FROM screenings
                        WHERE screening_time = ?
                        """)) {

            statement.setString(1, "2026-11-08T19:00:00");

            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("Screen 2", resultSet.getString("room"));
            }
        }
    }

    @Test
    void failedMigrationRollsBackChangesAndIsNotRecorded()
            throws Exception {

        Path databasePath = tempDir.resolve("failed-migration.db");

        try (Connection connection =
                DriverManager.getConnection(
                    "jdbc:sqlite:" + databasePath.toAbsolutePath()
                );
             Statement statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE schema_migrations (
                    version INTEGER PRIMARY KEY,
                    description TEXT NOT NULL,
                    applied_at TEXT NOT NULL DEFAULT
                        (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
                )
                """);

            statement.execute("""
                CREATE TRIGGER reject_version_four
                BEFORE INSERT ON schema_migrations
                WHEN NEW.version = 4
                BEGIN
                    SELECT RAISE(
                        ABORT,
                        'Simulated migration history write failure'
                    );
                END
                """);
        }

        Database database = new Database(databasePath);

        SQLException exception = assertThrows(
            SQLException.class,
            database::initialize
        );

        assertTrue(
            exception.getMessage().contains(
                "Simulated migration history write failure"
            )
        );

        assertEquals(
            List.of(1, 2, 3),
            appliedVersions(database)
        );

        assertFalse(objectExists(
            database, "index", "idx_film_genres_genre"
        ));

        assertFalse(objectExists(
            database, "index", "idx_screenings_film"
        ));
    }

    @Test
    void rejectsUnsupportedFutureMigrationVersion()
            throws Exception {

        Path databasePath = tempDir.resolve("future-version.db");

        createMigrationHistory(databasePath, 99);

        Database database = new Database(databasePath);

        SQLException exception = assertThrows(
            SQLException.class,
            database::initialize
        );

        assertTrue(
            exception.getMessage().contains(
                "unsupported migration version: 99"
            )
            || exception.getMessage().contains(
                "Unsupported migration version: 99"
            )
        );
    }

    @Test
    void rejectsIncompleteMigrationHistory()
            throws Exception {

        Path databasePath = tempDir.resolve("incomplete-history.db");

        createMigrationHistory(databasePath, 2);

        Database database = new Database(databasePath);

        SQLException exception = assertThrows(
            SQLException.class,
            database::initialize
        );

        assertTrue(
            exception.getMessage().contains(
                "incomplete at version 2"
            )
        );
    }

    @Test
    void rejectsExistingTableWithIncompatibleColumns()
            throws Exception {

        Path databasePath = tempDir.resolve("incompatible.db");

        try (Connection connection =
                DriverManager.getConnection(
                    "jdbc:sqlite:" + databasePath.toAbsolutePath()
                );
             Statement statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE films (
                    film_id INTEGER PRIMARY KEY,
                    name TEXT NOT NULL
                )
                """);
        }

        Database database = new Database(databasePath);

        SQLException exception = assertThrows(
            SQLException.class,
            database::initialize
        );

        assertTrue(exception.getMessage().contains("films"));
        assertTrue(exception.getMessage().contains("title"));
    }

    private void createLegacyDatabase(Path databasePath)
            throws SQLException {

        try (Connection connection =
                DriverManager.getConnection(
                    "jdbc:sqlite:" + databasePath.toAbsolutePath()
                );
             Statement statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE films (
                    film_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL
                        CHECK (length(trim(title)) > 0),
                    year INTEGER NOT NULL CHECK (year > 0),
                    description TEXT,
                    created_at TEXT NOT NULL DEFAULT
                        (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
                )
                """);

            statement.execute("""
                CREATE TABLE genres (
                    genre_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE
                        CHECK (length(trim(name)) > 0)
                )
                """);

            statement.execute("""
                CREATE TABLE film_genres (
                    film_id INTEGER NOT NULL,
                    genre_id INTEGER NOT NULL,
                    PRIMARY KEY (film_id, genre_id),
                    FOREIGN KEY (film_id)
                        REFERENCES films(film_id) ON DELETE CASCADE,
                    FOREIGN KEY (genre_id)
                        REFERENCES genres(genre_id) ON DELETE CASCADE
                )
                """);

            statement.execute("""
                CREATE TABLE screenings (
                    screening_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    film_id INTEGER NOT NULL,
                    venue TEXT NOT NULL
                        CHECK (length(trim(venue)) > 0),
                    screening_time TEXT NOT NULL,
                    FOREIGN KEY (film_id)
                        REFERENCES films(film_id) ON DELETE CASCADE
                )
                """);

            statement.executeUpdate("""
                INSERT INTO films (film_id, title, year)
                VALUES (7, 'Legacy Film', 2020)
                """);

            statement.executeUpdate("""
                INSERT INTO genres (genre_id, name)
                VALUES (3, 'Drama')
                """);

            statement.executeUpdate("""
                INSERT INTO film_genres (film_id, genre_id)
                VALUES (7, 3)
                """);

            statement.executeUpdate("""
                INSERT INTO screenings (
                    screening_id,
                    film_id,
                    venue,
                    screening_time
                )
                VALUES (
                    5,
                    7,
                    'Nairobi Cinema',
                    '2026-11-07T18:00:00'
                )
                """);
        }
    }


    /*
     * Builds a populated database at migration version 4.
     * Initialization should apply only migration 5.
     */
    private void createVersionFourDatabase(Path databasePath)
            throws SQLException {

        createLegacyDatabase(databasePath);

        try (Connection connection =
                DriverManager.getConnection(
                    "jdbc:sqlite:" + databasePath.toAbsolutePath()
                );
             Statement statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE schema_migrations (
                    version INTEGER PRIMARY KEY,
                    description TEXT NOT NULL,
                    applied_at TEXT NOT NULL DEFAULT
                        (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
                )
                """);

            statement.execute("""
                CREATE INDEX idx_film_genres_genre
                ON film_genres(genre_id, film_id)
                """);

            statement.execute("""
                CREATE INDEX idx_screenings_film
                ON screenings(film_id)
                """);

            try (PreparedStatement insert =
                    connection.prepareStatement("""
                        INSERT INTO schema_migrations (
                            version, description
                        )
                        VALUES (?, ?)
                        """)) {

                String[] descriptions = {
                    "Create films and genres tables",
                    "Create film genres relationship table",
                    "Create screenings table",
                    "Create database indexes"
                };

                for (int version = 1; version <= 4; version++) {
                    insert.setInt(1, version);
                    insert.setString(2, descriptions[version - 1]);
                    insert.executeUpdate();
                }
            }
        }
    }

    private void createMigrationHistory(
            Path databasePath,
            int version
    ) throws SQLException {

        try (Connection connection =
                DriverManager.getConnection(
                    "jdbc:sqlite:" + databasePath.toAbsolutePath()
                );
             Statement statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE schema_migrations (
                    version INTEGER PRIMARY KEY,
                    description TEXT NOT NULL,
                    applied_at TEXT NOT NULL DEFAULT
                        (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
                )
                """);

            try (PreparedStatement insert =
                    connection.prepareStatement("""
                        INSERT INTO schema_migrations (
                            version,
                            description
                        )
                        VALUES (?, ?)
                        """)) {

                insert.setInt(1, version);
                insert.setString(2, "Test migration");
                insert.executeUpdate();
            }
        }
    }

    private List<Integer> appliedVersions(Database database)
            throws SQLException {

        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                 SELECT version
                 FROM schema_migrations
                 ORDER BY version
                 """)) {

            List<Integer> versions = new ArrayList<>();

            while (resultSet.next()) {
                versions.add(resultSet.getInt("version"));
            }

            return versions;
        }
    }

    private int countRows(Database database, String table)
            throws SQLException {

        String sql = "SELECT COUNT(*) FROM " + table;

        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    private boolean objectExists(
            Database database,
            String type,
            String name
    ) throws SQLException {

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                    connection.prepareStatement("""
                        SELECT 1
                        FROM sqlite_master
                        WHERE type = ? AND name = ?
                        """)) {

            statement.setString(1, type);
            statement.setString(2, name);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
