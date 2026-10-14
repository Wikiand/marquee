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

    public List<Film> findAll(
            String genre,
            Integer year,
            String title,
            int page,
            int perPage
    ) throws SQLException {

        StringBuilder sql = new StringBuilder("""
            SELECT f.film_id, f.title, f.year, f.created_at
            FROM films f
            WHERE 1 = 1
            """);

        List<Object> parameters = new ArrayList<>();

        if (genre != null && !genre.isBlank()) {
            sql.append("""
                AND EXISTS (
                    SELECT 1
                    FROM film_genres fg
                    JOIN genres g ON g.genre_id = fg.genre_id
                    WHERE fg.film_id = f.film_id
                      AND LOWER(g.name) = LOWER(?)
                )
                """);
            parameters.add(genre.trim());
        }

        if (year != null) {
            sql.append(" AND f.year = ?");
            parameters.add(year);
        }

        if (title != null && !title.isBlank()) {
            sql.append(" AND LOWER(f.title) LIKE LOWER(?)");
            parameters.add("%" + title.trim() + "%");
        }

        sql.append(" ORDER BY f.film_id LIMIT ? OFFSET ?");

        parameters.add(perPage);
        parameters.add((page - 1) * perPage);

        List<Film> films = new ArrayList<>();

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql.toString())) {

            bindParameters(statement, parameters);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    films.add(mapFilm(connection, result, false));
                }
            }
        }

        return films;
    }

    public int count(
            String genre,
            Integer year,
            String title
    ) throws SQLException {

        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM films f
            WHERE 1 = 1
            """);

        List<Object> parameters = new ArrayList<>();

        if (genre != null && !genre.isBlank()) {
            sql.append("""
                AND EXISTS (
                    SELECT 1
                    FROM film_genres fg
                    JOIN genres g ON g.genre_id = fg.genre_id
                    WHERE fg.film_id = f.film_id
                      AND LOWER(g.name) = LOWER(?)
                )
                """);
            parameters.add(genre.trim());
        }

        if (year != null) {
            sql.append(" AND f.year = ?");
            parameters.add(year);
        }

        if (title != null && !title.isBlank()) {
            sql.append(" AND LOWER(f.title) LIKE LOWER(?)");
            parameters.add("%" + title.trim() + "%");
        }

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql.toString())) {

            bindParameters(statement, parameters);

            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
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

    private void bindParameters(
            PreparedStatement statement,
            List<Object> parameters
    ) throws SQLException {

        for (int i = 0; i < parameters.size(); i++) {
            statement.setObject(i + 1, parameters.get(i));
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
