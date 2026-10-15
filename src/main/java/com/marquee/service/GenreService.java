package com.marquee.service;

import com.marquee.model.Genre;
import com.marquee.repository.GenreRepository;

import java.sql.SQLException;
import java.util.List;

public final class GenreService {

    private final GenreRepository repository;

    public GenreService(GenreRepository repository) {
        this.repository = repository;
    }

    public List<Genre> listGenres() throws SQLException {
        return repository.findAll();
    }
}
