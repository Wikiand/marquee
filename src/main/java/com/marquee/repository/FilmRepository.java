package com.marquee.repository;

import com.marquee.database.Database;
import com.marquee.model.Film;
import com.marquee.model.Genre;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FilmRepository {

    public enum GenreAssociationResult {
        SUCCESS,
        FILM_NOT_FOUND,
        GENRE_NOT_FOUND,
        ALREADY_ASSIGNED,
        ASSOCIATION_NOT_FOUND
    }


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

    public Film create(
            String title,
            int year,
            String description,
            List<Integer> genreIds
    ) throws SQLException {

        String sql = """
            INSERT INTO films (title, year, description)
            VALUES (?, ?, ?)
            """;

        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);

            try {
                int filmId;

                try (PreparedStatement statement =
                        connection.prepareStatement(
                            sql, Statement.RETURN_GENERATED_KEYS)) {

                    statement.setString(1, title);
                    statement.setInt(2, year);
                    statement.setString(3, description);
                    statement.executeUpdate();

                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                "Creating film failed: no ID returned"
                            );
                        }
                        filmId = keys.getInt(1);
                    }
                }

                if (genreIds != null) {
                    replaceGenres(connection, filmId, genreIds);
                }

                Film film = findById(connection, filmId).orElseThrow(
                    () -> new SQLException(
                        "Created film could not be retrieved"
                    )
                );

                connection.commit();
                return film;

            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public Optional<Film> update(
            int filmId,
            String title,
            int year,
            String description,
            List<Integer> genreIds
    ) throws SQLException {

        String sql = """
            UPDATE films
            SET title = ?, year = ?, description = ?
            WHERE film_id = ?
            """;

        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);

            try {
                int affectedRows;

                try (PreparedStatement statement =
                        connection.prepareStatement(sql)) {

                    statement.setString(1, title);
                    statement.setInt(2, year);
                    statement.setString(3, description);
                    statement.setInt(4, filmId);
                    affectedRows = statement.executeUpdate();
                }

                if (affectedRows == 0) {
                    connection.rollback();
                    return Optional.empty();
                }

                if (genreIds != null) {
                    replaceGenres(connection, filmId, genreIds);
                }

                Film film = findById(connection, filmId).orElseThrow(
                    () -> new SQLException(
                        "Updated film could not be retrieved"
                    )
                );

                connection.commit();
                return Optional.of(film);

            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public Optional<Film> findById(int filmId) throws SQLException {
        try (Connection connection = database.getConnection()) {
            return findById(connection, filmId);
        }
    }

    private Optional<Film> findById(
            Connection connection,
            int filmId
    ) throws SQLException {

        String sql = """
            SELECT film_id, title, year, description, created_at
            FROM films
            WHERE film_id = ?
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setInt(1, filmId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapFilm(connection, result, true));
            }
        }
    }

    private void replaceGenres(
            Connection connection,
            int filmId,
            List<Integer> genreIds
    ) throws SQLException {

        List<Integer> validatedIds = new ArrayList<>();

        for (Integer genreId : genreIds) {
            if (genreId == null || genreId <= 0) {
                throw new IllegalArgumentException(
                    "Genre IDs must be positive integers"
                );
            }

            if (validatedIds.contains(genreId)) {
                throw new IllegalArgumentException(
                    "Duplicate genre ID: " + genreId
                );
            }

            validatedIds.add(genreId);
        }

        for (Integer genreId : validatedIds) {
            if (!genreExists(connection, genreId)) {
                throw new IllegalArgumentException(
                    "Genre not found: " + genreId
                );
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM film_genres WHERE film_id = ?")) {

            statement.setInt(1, filmId);
            statement.executeUpdate();
        }

        String sql = """
            INSERT INTO film_genres (film_id, genre_id)
            VALUES (?, ?)
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            for (Integer genreId : validatedIds) {
                statement.setInt(1, filmId);
                statement.setInt(2, genreId);
                statement.executeUpdate();
            }
        }
    }

    private boolean genreExists(
            Connection connection,
            int genreId
    ) throws SQLException {

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM genres WHERE genre_id = ?")) {

            statement.setInt(1, genreId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
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


    public GenreAssociationResult assignGenre(
            int filmId, int genreId
    ) throws SQLException {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);

            try {
                if (!filmExists(connection, filmId)) {
                    connection.rollback();
                    return GenreAssociationResult.FILM_NOT_FOUND;
                }

                if (!genreExists(connection, genreId)) {
                    connection.rollback();
                    return GenreAssociationResult.GENRE_NOT_FOUND;
                }

                String checkSql = """
                    SELECT 1 FROM film_genres
                    WHERE film_id = ? AND genre_id = ?
                    """;

                try (PreparedStatement statement =
                        connection.prepareStatement(checkSql)) {
                    statement.setInt(1, filmId);
                    statement.setInt(2, genreId);

                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            connection.rollback();
                            return GenreAssociationResult.ALREADY_ASSIGNED;
                        }
                    }
                }

                String insertSql = """
                    INSERT INTO film_genres (film_id, genre_id)
                    VALUES (?, ?)
                    """;

                try (PreparedStatement statement =
                        connection.prepareStatement(insertSql)) {
                    statement.setInt(1, filmId);
                    statement.setInt(2, genreId);
                    statement.executeUpdate();
                }

                connection.commit();
                return GenreAssociationResult.SUCCESS;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public GenreAssociationResult removeGenre(
            int filmId, int genreId
    ) throws SQLException {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);

            try {
                if (!filmExists(connection, filmId)) {
                    connection.rollback();
                    return GenreAssociationResult.FILM_NOT_FOUND;
                }

                if (!genreExists(connection, genreId)) {
                    connection.rollback();
                    return GenreAssociationResult.GENRE_NOT_FOUND;
                }

                String sql = """
                    DELETE FROM film_genres
                    WHERE film_id = ? AND genre_id = ?
                    """;

                int affectedRows;

                try (PreparedStatement statement =
                        connection.prepareStatement(sql)) {
                    statement.setInt(1, filmId);
                    statement.setInt(2, genreId);
                    affectedRows = statement.executeUpdate();
                }

                if (affectedRows == 0) {
                    connection.rollback();
                    return GenreAssociationResult.ASSOCIATION_NOT_FOUND;
                }

                connection.commit();
                return GenreAssociationResult.SUCCESS;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private boolean filmExists(
            Connection connection, int filmId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM films WHERE film_id = ?")) {
            statement.setInt(1, filmId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public boolean delete(int filmId) throws SQLException {
        String sql = "DELETE FROM films WHERE film_id = ?";

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, filmId);
            return statement.executeUpdate() > 0;
        }
    }
}
