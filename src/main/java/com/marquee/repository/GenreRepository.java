package com.marquee.repository;

import com.marquee.database.Database;
import com.marquee.model.Genre;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class GenreRepository {

    private final Database database;

    public GenreRepository(Database database) {
        this.database = database;
    }

    public List<Genre> findAll() throws SQLException {
        String sql = """
            SELECT genre_id, name
            FROM genres
            ORDER BY name
            """;

        List<Genre> genres = new ArrayList<>();

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                genres.add(new Genre(
                    result.getInt("genre_id"),
                    result.getString("name")
                ));
            }
        }

        return List.copyOf(genres);
    }

    public Genre create(String name) throws SQLException {
        String sql = "INSERT INTO genres (name) VALUES (?)";

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 sql, Statement.RETURN_GENERATED_KEYS
             )) {

            statement.setString(1, name);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return new Genre(keys.getInt(1), name);
                }
            }

            throw new SQLException(
                "Creating genre failed: no ID returned"
            );
        }
    }

    public java.util.Optional<Genre> update(
            int genreId, String name
    ) throws SQLException {
        String sql = "UPDATE genres SET name = ? WHERE genre_id = ?";

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, name);
            statement.setInt(2, genreId);

            int affectedRows = statement.executeUpdate();

            if (affectedRows == 0) {
                return java.util.Optional.empty();
            }
        }

        String selectSql =
            "SELECT genre_id, name FROM genres WHERE genre_id = ?";

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(selectSql)) {

            statement.setInt(1, genreId);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return java.util.Optional.of(new Genre(
                        result.getInt("genre_id"),
                        result.getString("name")
                    ));
                }
            }
        }

        return java.util.Optional.empty();
    }

    public boolean delete(int genreId) throws SQLException {
        String sql = "DELETE FROM genres WHERE genre_id = ?";

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, genreId);
            return statement.executeUpdate() > 0;
        }
    }
}
