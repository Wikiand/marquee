package com.marquee.service;

import com.marquee.model.Screening;
import com.marquee.repository.ScreeningRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class ScreeningService {

    private final ScreeningRepository repository;

    public ScreeningService(ScreeningRepository repository) {
        this.repository = repository;
    }

    public List<Screening> listScreenings() throws SQLException {
        return repository.findAll();
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
            filmId,
            venue.trim(),
            screeningTime.trim()
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
            screeningId,
            filmId,
            venue.trim(),
            screeningTime.trim()
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
}
