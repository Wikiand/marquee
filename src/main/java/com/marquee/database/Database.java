package com.marquee.database;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

    private final String url;

    public Database(Path databasePath) {
        this.url = "jdbc:sqlite:" + databasePath.toAbsolutePath();
    }

    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(url);

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }

        return connection;
    }

    public void initialize() throws SQLException {
        String[] statements = {
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
            """,
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
            """,
            """
            CREATE TABLE IF NOT EXISTS screenings (
                screening_id INTEGER PRIMARY KEY AUTOINCREMENT,
                film_id INTEGER NOT NULL,
                venue TEXT NOT NULL CHECK (length(trim(venue)) > 0),
                screening_time TEXT NOT NULL,
                FOREIGN KEY (film_id)
                    REFERENCES films(film_id) ON DELETE CASCADE
            )
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_film_genres_genre
            ON film_genres(genre_id, film_id)
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_screenings_film
            ON screenings(film_id)
            """
        };

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }
}
