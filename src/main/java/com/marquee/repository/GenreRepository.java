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
}
