package com.marquee.service;

import com.marquee.model.Screening;
import com.marquee.repository.ScreeningRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

public final class ScreeningService {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PER_PAGE = 20;
    public static final int MAX_PER_PAGE = 100;

    private final ScreeningRepository repository;

    public ScreeningService(ScreeningRepository repository) {
        this.repository = repository;
    }

    public ScreeningPage listScreenings(
            Integer filmId,
            String venue,
            String date,
            int page,
            int perPage
    ) throws SQLException {
        if (filmId != null && filmId <= 0) {
            throw new IllegalArgumentException(
                "film_id must be a positive integer"
            );
        }
        if (page < 1) {
            throw new IllegalArgumentException("Page must be at least 1");
        }
        if (perPage < 1 || perPage > MAX_PER_PAGE) {
            throw new IllegalArgumentException(
                "per_page must be between 1 and " + MAX_PER_PAGE
            );
        }
        if (date != null) {
            try {
                LocalDate.parse(date);
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(
                    "date must use YYYY-MM-DD format"
                );
            }
        }

        List<Screening> screenings = repository.findAll(
            filmId, venue, date, page, perPage
        );
        int total = repository.count(filmId, venue, date);

        return new ScreeningPage(page, perPage, total, screenings);
    }

    public Optional<Screening> getScreening(int screeningId)
            throws SQLException {
        validateId(screeningId);
        return repository.findById(screeningId);
    }

    public Screening createScreening(
            Integer filmId,
            String venue,
            String screeningTime
    ) throws SQLException {
        validateFilmId(filmId);
        validateVenue(venue);
        validateScreeningTime(screeningTime);

        return repository.create(
            filmId, venue.trim(), screeningTime.trim()
        );
    }

    public Optional<Screening> updateScreening(
            int screeningId,
            Integer filmId,
            String venue,
            String screeningTime
    ) throws SQLException {
        validateId(screeningId);
        validateFilmId(filmId);
        validateVenue(venue);
        validateScreeningTime(screeningTime);

        return repository.update(
            screeningId, filmId, venue.trim(), screeningTime.trim()
        );
    }

    public boolean deleteScreening(int screeningId) throws SQLException {
        validateId(screeningId);
        return repository.delete(screeningId);
    }

    private void validateId(int screeningId) {
        if (screeningId <= 0) {
            throw new IllegalArgumentException(
                "Screening ID must be positive"
            );
        }
    }

    private void validateFilmId(Integer filmId) {
        if (filmId == null || filmId <= 0) {
            throw new IllegalArgumentException(
                "Film ID must be positive"
            );
        }
    }

    private void validateVenue(String venue) {
        if (venue == null || venue.isBlank()) {
            throw new IllegalArgumentException(
                "Venue must not be blank"
            );
        }
    }

    private void validateScreeningTime(String screeningTime) {
        if (screeningTime == null || screeningTime.isBlank()) {
            throw new IllegalArgumentException(
                "Screening time must not be blank"
            );
        }
    }

    public record ScreeningPage(
        int page,
        int per_page,
        int total,
        List<Screening> screenings
    ) {
        public ScreeningPage {
            screenings = List.copyOf(screenings);
        }
    }
}
