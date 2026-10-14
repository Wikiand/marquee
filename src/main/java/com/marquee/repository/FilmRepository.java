package com.marquee.repository;

import com.marquee.database.Database;
import com.marquee.model.Film;
import com.marquee.model.Genre;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FilmRepository {

    private final Database database;

    public FilmRepository(Database database) {
        this.database = database;
    }

    public List<Film> findAll() throws SQLException {
        String sql = """
            SELECT film_id, title, year, created_at
            FROM films
            ORDER BY film_id
            """;

        List<Film> films = new ArrayList<>();

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                films.add(mapFilm(connection, result, false));
            }
        }

        return films;
    }

    public Optional<Film> findById(int filmId) throws SQLException {
        String sql = """
            SELECT film_id, title, year, description, created_at
            FROM films
            WHERE film_id = ?
            """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, filmId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapFilm(connection, result, true));
            }
        }
    }

    private Film mapFilm(
            Connection connection,
            ResultSet result,
            boolean includeDescription
    ) throws SQLException {

        int filmId = result.getInt("film_id");
        List<Genre> genres = new ArrayList<>();

        String genreSql = """
            SELECT g.genre_id, g.name
            FROM genres g
            JOIN film_genres fg ON fg.genre_id = g.genre_id
            WHERE fg.film_id = ?
            ORDER BY g.name
            """;

        try (PreparedStatement statement =
                     connection.prepareStatement(genreSql)) {

            statement.setInt(1, filmId);

            try (ResultSet genreResults = statement.executeQuery()) {
                while (genreResults.next()) {
                    genres.add(new Genre(
                        genreResults.getInt("genre_id"),
                        genreResults.getString("name")
                    ));
                }
            }
        }

        return new Film(
            filmId,
            result.getString("title"),
            result.getInt("year"),
            includeDescription ? result.getString("description") : null,
            result.getString("created_at"),
            genres
        );
    }
}
