package com.marquee.service;

import com.marquee.database.Database;
import com.marquee.repository.GenreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GenreServiceTest {

    @TempDir
    Path tempDir;

    private GenreService createService() throws Exception {
        Database database = new Database(tempDir.resolve("genre-test.db"));
        database.initialize();
        return new GenreService(new GenreRepository(database));
    }

    @Test
    void createGenreRejectsNullName() throws Exception {
        GenreService service = createService();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.createGenre(null)
        );

        assertEquals(
            "Genre name is required and cannot be blank",
            exception.getMessage()
        );
    }

    @Test
    void createGenreRejectsBlankName() throws Exception {
        GenreService service = createService();

        assertThrows(
            IllegalArgumentException.class,
            () -> service.createGenre("   ")
        );
    }

    @Test
    void createGenreTrimsName() throws Exception {
        GenreService service = createService();

        var genre = service.createGenre("  Action  ");

        assertEquals("Action", genre.name());
        assertTrue(genre.genreId() > 0);
    }

    @Test
    void listGenresReturnsCreatedGenres() throws Exception {
        GenreService service = createService();

        service.createGenre("Action");
        service.createGenre("Comedy");

        var genres = service.listGenres();

        assertEquals(2, genres.size());
        assertTrue(genres.stream().anyMatch(g -> g.name().equals("Action")));
        assertTrue(genres.stream().anyMatch(g -> g.name().equals("Comedy")));
    }

    @Test
    void findGenreByIdReturnsExistingGenre() throws Exception {
        GenreService service = createService();
        var created = service.createGenre("Drama");

        var found = service.findGenreById(created.genreId());

        assertTrue(found.isPresent());
        assertEquals("Drama", found.get().name());
    }

    @Test
    void findGenreByIdReturnsEmptyForMissingGenre() throws Exception {
        GenreService service = createService();

        assertTrue(service.findGenreById(9999).isEmpty());
    }

    @Test
    void findGenreByIdRejectsNonPositiveId() throws Exception {
        GenreService service = createService();

        assertThrows(
            IllegalArgumentException.class,
            () -> service.findGenreById(0)
        );
    }

    @Test
    void updateGenreChangesName() throws Exception {
        GenreService service = createService();
        var created = service.createGenre("Thriller");

        var updated = service.updateGenre(created.genreId(), "Mystery");

        assertTrue(updated.isPresent());
        assertEquals("Mystery", updated.get().name());
    }

    @Test
    void updateGenreReturnsEmptyForMissingGenre() throws Exception {
        GenreService service = createService();

        assertTrue(service.updateGenre(9999, "Mystery").isEmpty());
    }

    @Test
    void updateGenreRejectsBlankName() throws Exception {
        GenreService service = createService();
        var created = service.createGenre("Comedy");

        assertThrows(
            IllegalArgumentException.class,
            () -> service.updateGenre(created.genreId(), " ")
        );
    }

    @Test
    void deleteGenreRemovesExistingGenre() throws Exception {
        GenreService service = createService();
        var created = service.createGenre("Western");

        assertTrue(service.deleteGenre(created.genreId()));
        assertTrue(service.findGenreById(created.genreId()).isEmpty());
    }

    @Test
    void deleteGenreReturnsFalseForMissingGenre() throws Exception {
        GenreService service = createService();

        assertFalse(service.deleteGenre(9999));
    }

    @Test
    void deleteGenreRejectsNonPositiveId() throws Exception {
        GenreService service = createService();

        assertThrows(
            IllegalArgumentException.class,
            () -> service.deleteGenre(-1)
        );
    }
}
