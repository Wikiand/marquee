package com.marquee.service;

import com.marquee.database.Database;
import com.marquee.model.FilmInput;
import com.marquee.repository.FilmRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

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
        FilmInput input = new FilmInput("   ", 2024, "Description");

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
        FilmInput input = new FilmInput("Test Film", null, "Description");

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
        FilmInput input = new FilmInput("Test Film", 0, "Description");

        assertThrows(
            IllegalArgumentException.class,
            () -> service.createFilm(input)
        );
    }

    @Test
    void createFilmTrimsTitle() throws Exception {
        FilmService service = createService();

        var film = service.createFilm(
            new FilmInput("  Test Film  ", 2024, "Description")
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

        service.createFilm(new FilmInput("Film One", 2020, "First"));
        service.createFilm(new FilmInput("Film Two", 2021, "Second"));
        service.createFilm(new FilmInput("Film Three", 2022, "Third"));

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

        service.createFilm(new FilmInput("The Blue Ocean", 2020, null));
        service.createFilm(new FilmInput("Blue Mountain", 2020, null));
        service.createFilm(new FilmInput("The Blue Ocean 2", 2022, null));

        var result = service.listFilms(null, 2020, "ocean", 1, 20);

        assertEquals(1, result.total());
        assertEquals(1, result.films().size());
        assertEquals("The Blue Ocean", result.films().get(0).title());
    }
}
