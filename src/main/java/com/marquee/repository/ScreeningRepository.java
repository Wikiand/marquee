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

    public List<Screening> findAll() throws SQLException {
        String sql = """
            SELECT screening_id, film_id, venue, screening_time
            FROM screenings
            ORDER BY screening_id
            """;

        List<Screening> screenings = new ArrayList<>();

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                screenings.add(mapScreening(result));
            }
        }

        return screenings;
    }

    public Optional<Screening> findById(int screeningId)
            throws SQLException {
        String sql = """
            SELECT screening_id, film_id, venue, screening_time
            FROM screenings
            WHERE screening_id = ?
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
                 sql, Statement.RETURN_GENERATED_KEYS
             )) {

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

            throw new SQLException(
                "Creating screening failed: no ID returned"
            );
        }
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
            result.getString("venue"),
            result.getString("screening_time")
        );
    }
}
