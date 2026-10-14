package com.marquee.service;

import com.marquee.model.Film;
import com.marquee.repository.FilmRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class FilmService {

    private final FilmRepository repository;

    public FilmService(FilmRepository repository) {
        this.repository = repository;
    }

    public List<Film> listFilms() throws SQLException {
        return repository.findAll();
    }

    public Optional<Film> getFilm(int filmId) throws SQLException {
        if (filmId <= 0) {
            throw new IllegalArgumentException(
                "Film ID must be a positive integer"
            );
        }

        return repository.findById(filmId);
    }
}
