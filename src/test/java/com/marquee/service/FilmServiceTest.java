package com.marquee.service;

import com.marquee.database.Database;
import com.marquee.model.FilmInput;
import com.marquee.repository.FilmRepository;
import com.marquee.repository.GenreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FilmServiceTest {

    @TempDir
    Path tempDir;

    private FilmService createService() throws Exception {
        Database database = new Database(tempDir.resolve("test.db"));
        database.initialize();
        return new FilmService(new FilmRepository(database));
    }

    @Test
    void createFilmRejectsNullInput() throws Exception {
        FilmService service = createService();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.createFilm(null)
        );

        assertEquals("Request body is required", exception.getMessage());
    }

    @Test
    void createFilmRejectsBlankTitle() throws Exception {
        FilmService service = createService();
        FilmInput input = new FilmInput("   ", 2024, "Description", null);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.createFilm(input)
        );

        assertEquals(
            "Title is required and cannot be blank",
            exception.getMessage()
        );
    }

    @Test
    void createFilmRejectsMissingYear() throws Exception {
        FilmService service = createService();
        FilmInput input = new FilmInput("Test Film", null, "Description", null);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.createFilm(input)
        );

        assertEquals(
            "Year must be a positive integer",
            exception.getMessage()
        );
    }

    @Test
    void createFilmRejectsNonPositiveYear() throws Exception {
        FilmService service = createService();
        FilmInput input = new FilmInput("Test Film", 0, "Description", null);

        assertThrows(
            IllegalArgumentException.class,
            () -> service.createFilm(input)
        );
    }

    @Test
    void createFilmTrimsTitle() throws Exception {
        FilmService service = createService();

        var film = service.createFilm(
            new FilmInput("  Test Film  ", 2024, "Description", null)
        );

        assertEquals("Test Film", film.title());
        assertEquals(2024, film.year());
    }

    @Test
    void getFilmRejectsNonPositiveId() throws Exception {
        FilmService service = createService();

        assertThrows(
            IllegalArgumentException.class,
            () -> service.getFilm(0)
        );
    }

    @Test
    void deleteFilmRejectsNonPositiveId() throws Exception {
        FilmService service = createService();

        assertThrows(
            IllegalArgumentException.class,
            () -> service.deleteFilm(-1)
        );
    }

    @Test
    void listFilmsRejectsInvalidPagination() throws Exception {
        FilmService service = createService();

        assertThrows(
            IllegalArgumentException.class,
            () -> service.listFilms(null, null, null, 0, 20)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> service.listFilms(null, null, null, 1, 101)
        );
    }

    @Test
    void listFilmsReturnsCorrectPaginationMetadata() throws Exception {
        FilmService service = createService();

        service.createFilm(new FilmInput("Film One", 2020, "First", null));
        service.createFilm(new FilmInput("Film Two", 2021, "Second", null));
        service.createFilm(new FilmInput("Film Three", 2022, "Third", null));

        var firstPage = service.listFilms(null, null, null, 1, 2);
        var secondPage = service.listFilms(null, null, null, 2, 2);

        assertEquals(3, firstPage.total());
        assertEquals(2, firstPage.films().size());
        assertEquals(1, firstPage.page());
        assertEquals(2, firstPage.per_page());

        assertEquals(3, secondPage.total());
        assertEquals(1, secondPage.films().size());
        assertEquals(2, secondPage.page());
    }

    @Test
    void listFilmsFiltersByYearAndTitle() throws Exception {
        FilmService service = createService();

        service.createFilm(new FilmInput("The Blue Ocean", 2020, null, null));
        service.createFilm(new FilmInput("Blue Mountain", 2020, null, null));
        service.createFilm(new FilmInput("The Blue Ocean 2", 2022, null, null));

        var result = service.listFilms(null, 2020, "ocean", 1, 20);

        assertEquals(1, result.total());
        assertEquals(1, result.films().size());
        assertEquals("The Blue Ocean", result.films().get(0).title());
    }

    @Test
    void createFilmAssignsGenres() throws Exception {
        Database database = new Database(tempDir.resolve("genres-create.db"));
        database.initialize();

        GenreRepository genreRepository = new GenreRepository(database);
        var action = genreRepository.create("Action");
        var drama = genreRepository.create("Drama");

        FilmService service = new FilmService(new FilmRepository(database));

        var film = service.createFilm(new FilmInput(
            "Genre Test Film",
            2025,
            "Test description",
            List.of(action.genreId(), drama.genreId())
        ));

        assertEquals(2, film.genres().size());
        assertTrue(film.genres().stream()
            .anyMatch(g -> g.genreId() == action.genreId()));
        assertTrue(film.genres().stream()
            .anyMatch(g -> g.genreId() == drama.genreId()));
    }

    @Test
    void createFilmRejectsNonexistentGenreAndRollsBack() throws Exception {
        Database database = new Database(tempDir.resolve("genres-invalid.db"));
        database.initialize();

        FilmService service = new FilmService(new FilmRepository(database));

        assertThrows(
            IllegalArgumentException.class,
            () -> service.createFilm(new FilmInput(
                "Invalid Genre Film", 2025, null, List.of(9999)
            ))
        );

        assertEquals(0, service.listFilms(null, null, null, 1, 20).total());
    }

    @Test
    void createFilmRejectsDuplicateGenreIds() throws Exception {
        Database database = new Database(tempDir.resolve("genres-duplicate.db"));
        database.initialize();

        var genre = new GenreRepository(database).create("Comedy");
        FilmService service = new FilmService(new FilmRepository(database));

        assertThrows(
            IllegalArgumentException.class,
            () -> service.createFilm(new FilmInput(
                "Duplicate Genre Film",
                2025,
                null,
                List.of(genre.genreId(), genre.genreId())
            ))
        );
    }

    @Test
    void updateFilmPreservesGenresWhenGenreIdsAreNull() throws Exception {
        Database database = new Database(tempDir.resolve("genres-update.db"));
        database.initialize();

        var genre = new GenreRepository(database).create("Thriller");
        FilmService service = new FilmService(new FilmRepository(database));

        var created = service.createFilm(new FilmInput(
            "Original Title", 2024, null, List.of(genre.genreId())
        ));

        var updated = service.updateFilm(
            created.filmId(),
            new FilmInput("Updated Title", 2025, null, null)
        ).orElseThrow();

        assertEquals("Updated Title", updated.title());
        assertEquals(1, updated.genres().size());
        assertEquals(genre.genreId(), updated.genres().get(0).genreId());
    }


    @Test
    void updateFilmReplacesExistingGenres() throws Exception {
        Database database = new Database(tempDir.resolve("genres-replace.db"));
        database.initialize();

        GenreRepository genreRepository = new GenreRepository(database);
        var action = genreRepository.create("Action");
        var drama = genreRepository.create("Drama");

        FilmService service = new FilmService(new FilmRepository(database));

        var created = service.createFilm(new FilmInput(
            "Original Film", 2024, null, List.of(action.genreId())
        ));

        var updated = service.updateFilm(
            created.filmId(),
            new FilmInput("Updated Film", 2025, null, List.of(drama.genreId()))
        ).orElseThrow();

        assertEquals(1, updated.genres().size());
        assertEquals(drama.genreId(), updated.genres().get(0).genreId());
    }

    @Test
    void updateFilmClearsGenresWhenGenreIdsAreEmpty() throws Exception {
        Database database = new Database(tempDir.resolve("genres-clear.db"));
        database.initialize();

        var genre = new GenreRepository(database).create("Comedy");
        FilmService service = new FilmService(new FilmRepository(database));

        var created = service.createFilm(new FilmInput(
            "Film With Genre", 2024, null, List.of(genre.genreId())
        ));

        var updated = service.updateFilm(
            created.filmId(),
            new FilmInput("Film Without Genre", 2025, null, List.of())
        ).orElseThrow();

        assertTrue(updated.genres().isEmpty());
    }

}
