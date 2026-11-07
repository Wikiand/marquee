package com.marquee.database;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

    private final String url;
    private final MigrationRunner migrationRunner =
            new MigrationRunner();

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
        try (Connection connection = getConnection()) {
            migrationRunner.migrate(connection);
        }
    }
}
