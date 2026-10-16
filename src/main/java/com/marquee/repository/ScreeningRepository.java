package com.marquee.repository;

import com.marquee.database.Database;
import com.marquee.model.Screening;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ScreeningRepository {

    private final Database database;

    public ScreeningRepository(Database database) {
        this.database = database;
    }

    public List<Screening> findAll(
            Integer filmId,
            String venue,
            String date,
            int page,
            int perPage
    ) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT s.screening_id, s.film_id, f.title AS film_title,
                   s.venue, s.screening_time
            FROM screenings s
            JOIN films f ON f.film_id = s.film_id
            WHERE 1 = 1
            """);

        List<Object> parameters = new ArrayList<>();
        appendFilters(sql, parameters, filmId, venue, date);
        sql.append(" ORDER BY s.screening_id LIMIT ? OFFSET ?");
        parameters.add(perPage);
        parameters.add((page - 1) * perPage);

        List<Screening> screenings = new ArrayList<>();

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                 connection.prepareStatement(sql.toString())) {
            bindParameters(statement, parameters);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    screenings.add(mapScreening(result));
                }
            }
        }

        return screenings;
    }

    public int count(
            Integer filmId,
            String venue,
            String date
    ) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM screenings s
            WHERE 1 = 1
            """);

        List<Object> parameters = new ArrayList<>();
        appendFilters(sql, parameters, filmId, venue, date);

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

    private void appendFilters(
            StringBuilder sql,
            List<Object> parameters,
            Integer filmId,
            String venue,
            String date
    ) {
        if (filmId != null) {
            sql.append(" AND s.film_id = ?");
            parameters.add(filmId);
        }

        if (venue != null && !venue.isBlank()) {
            sql.append(" AND LOWER(s.venue) LIKE LOWER(?)");
            parameters.add("%" + venue.trim() + "%");
        }

        if (date != null) {
            sql.append(" AND substr(s.screening_time, 1, 10) = ?");
            parameters.add(date);
        }
    }

    public Optional<Screening> findById(int screeningId)
            throws SQLException {
        String sql = """
            SELECT s.screening_id, s.film_id, f.title AS film_title,
                   s.venue, s.screening_time
            FROM screenings s
            JOIN films f ON f.film_id = s.film_id
            WHERE s.screening_id = ?
            """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, screeningId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapScreening(result));
            }
        }
    }

    public Screening create(
            int filmId,
            String venue,
            String screeningTime
    ) throws SQLException {
        String sql = """
            INSERT INTO screenings (film_id, venue, screening_time)
            VALUES (?, ?, ?)
            """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, filmId);
            statement.setString(2, venue);
            statement.setString(3, screeningTime);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return findById(keys.getInt(1)).orElseThrow(
                        () -> new SQLException(
                            "Created screening could not be retrieved"
                        )
                    );
                }
            }
        }

        throw new SQLException("Creating screening failed: no ID returned");
    }

    public Optional<Screening> update(
            int screeningId,
            int filmId,
            String venue,
            String screeningTime
    ) throws SQLException {
        String sql = """
            UPDATE screenings
            SET film_id = ?, venue = ?, screening_time = ?
            WHERE screening_id = ?
            """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, filmId);
            statement.setString(2, venue);
            statement.setString(3, screeningTime);
            statement.setInt(4, screeningId);

            if (statement.executeUpdate() == 0) {
                return Optional.empty();
            }
        }

        return findById(screeningId);
    }

    public boolean delete(int screeningId) throws SQLException {
        String sql = "DELETE FROM screenings WHERE screening_id = ?";

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, screeningId);
            return statement.executeUpdate() > 0;
        }
    }

    private Screening mapScreening(ResultSet result) throws SQLException {
        return new Screening(
            result.getInt("screening_id"),
            result.getInt("film_id"),
            result.getString("film_title"),
            result.getString("venue"),
            result.getString("screening_time")
        );
    }

    private void bindParameters(
            PreparedStatement statement,
            List<Object> parameters
    ) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            statement.setObject(i + 1, parameters.get(i));
        }
    }
}
