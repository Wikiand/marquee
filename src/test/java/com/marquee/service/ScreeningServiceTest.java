package com.marquee.service;

import com.marquee.database.Database;
import com.marquee.repository.FilmRepository;
import com.marquee.repository.ScreeningRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ScreeningServiceTest {

    @TempDir
    Path tempDir;

    private ScreeningService createService() throws Exception {
        Database database = new Database(tempDir.resolve("screening-test.db"));
        database.initialize();

        FilmRepository filmRepository = new FilmRepository(database);
        filmRepository.create(
            "Test Film", 2025, "A film for screening tests", null
        );
        filmRepository.create(
            "Second Film", 2024, "Another film", null
        );

        return new ScreeningService(new ScreeningRepository(database));
    }

    @Test
    void createScreeningReturnsCreatedScreeningWithFilmTitle()
            throws Exception {
        ScreeningService service = createService();

        var screening = service.createScreening(
            1, "Main Hall", "2026-11-01T19:30:00Z"
        );

        assertTrue(screening.screeningId() > 0);
        assertEquals(1, screening.filmId());
        assertEquals("Test Film", screening.filmTitle());
        assertEquals("Main Hall", screening.venue());
        assertEquals("2026-11-01T19:30:00Z", screening.screeningTime());
    }

    @Test
    void createScreeningRejectsNullFilmId() throws Exception {
        ScreeningService service = createService();

        assertThrows(
            IllegalArgumentException.class,
            () -> service.createScreening(
                null, "Main Hall", "2026-11-01T19:30:00Z"
            )
        );
    }

    @Test
    void createScreeningRejectsNonPositiveFilmId() throws Exception {
        ScreeningService service = createService();

        assertThrows(
            IllegalArgumentException.class,
            () -> service.createScreening(
                0, "Main Hall", "2026-11-01T19:30:00Z"
            )
        );
    }

    @Test
    void createScreeningRejectsBlankVenue() throws Exception {
        ScreeningService service = createService();

        assertThrows(
            IllegalArgumentException.class,
            () -> service.createScreening(
                1, "  ", "2026-11-01T19:30:00Z"
            )
        );
    }

    @Test
    void createScreeningRejectsMissingScreeningTime() throws Exception {
        ScreeningService service = createService();

        assertThrows(
            IllegalArgumentException.class,
            () -> service.createScreening(1, "Main Hall", " ")
        );
    }

    @Test
    void createScreeningTrimsVenueAndTime() throws Exception {
        ScreeningService service = createService();

        var screening = service.createScreening(
            1, "  Main Hall  ", "  2026-11-01T19:30:00Z  "
        );

        assertEquals("Main Hall", screening.venue());
        assertEquals("2026-11-01T19:30:00Z", screening.screeningTime());
    }

    @Test
    void listScreeningsReturnsCreatedScreenings() throws Exception {
        ScreeningService service = createService();

        service.createScreening(1, "Main Hall", "2026-11-01T19:30:00Z");
        service.createScreening(1, "Studio A", "2026-11-02T18:00:00Z");

        var page = service.listScreenings(null, null, null, 1, 20);

        assertEquals(2, page.total());
        assertEquals(2, page.screenings().size());
        assertEquals(1, page.page());
        assertEquals(20, page.per_page());
    }

    @Test
    void listScreeningsFiltersByFilmId() throws Exception {
        ScreeningService service = createService();

        service.createScreening(1, "Main Hall", "2026-11-01T19:30:00Z");
        service.createScreening(2, "Studio A", "2026-11-02T18:00:00Z");

        var page = service.listScreenings(2, null, null, 1, 20);

        assertEquals(1, page.total());
        assertEquals("Second Film", page.screenings().get(0).filmTitle());
    }

    @Test
    void listScreeningsFiltersVenueCaseInsensitively() throws Exception {
        ScreeningService service = createService();

        service.createScreening(1, "Main Hall", "2026-11-01T19:30:00Z");
        service.createScreening(1, "Studio A", "2026-11-02T18:00:00Z");

        var page = service.listScreenings(
            null, "main", null, 1, 20
        );

        assertEquals(1, page.total());
        assertEquals("Main Hall", page.screenings().get(0).venue());
    }

    @Test
    void listScreeningsFiltersByDate() throws Exception {
        ScreeningService service = createService();

        service.createScreening(1, "Main Hall", "2026-11-01T19:30:00Z");
        service.createScreening(1, "Studio A", "2026-11-02T18:00:00Z");

        var page = service.listScreenings(
            null, null, "2026-11-02", 1, 20
        );

        assertEquals(1, page.total());
        assertEquals(
            "2026-11-02T18:00:00Z",
            page.screenings().get(0).screeningTime()
        );
    }

    @Test
    void listScreeningsPaginatesResults() throws Exception {
        ScreeningService service = createService();

        service.createScreening(1, "Hall 1", "2026-11-01T19:30:00Z");
        service.createScreening(1, "Hall 2", "2026-11-02T19:30:00Z");
        service.createScreening(1, "Hall 3", "2026-11-03T19:30:00Z");

        var page = service.listScreenings(null, null, null, 2, 2);

        assertEquals(3, page.total());
        assertEquals(2, page.page());
        assertEquals(2, page.per_page());
        assertEquals(1, page.screenings().size());
        assertEquals("Hall 3", page.screenings().get(0).venue());
    }

    @Test
    void listScreeningsReturnsEmptyPageWhenNoMatches() throws Exception {
        ScreeningService service = createService();

        var page = service.listScreenings(
            null, "No such venue", null, 1, 20
        );

        assertEquals(0, page.total());
        assertTrue(page.screenings().isEmpty());
    }

    @Test
    void listScreeningsRejectsInvalidPage() throws Exception {
        ScreeningService service = createService();

        assertThrows(
            IllegalArgumentException.class,
            () -> service.listScreenings(null, null, null, 0, 20)
        );
    }

    @Test
    void listScreeningsRejectsPerPageAboveMaximum() throws Exception {
        ScreeningService service = createService();

        assertThrows(
            IllegalArgumentException.class,
            () -> service.listScreenings(null, null, null, 1, 101)
        );
    }

    @Test
    void listScreeningsRejectsInvalidDate() throws Exception {
        ScreeningService service = createService();

        assertThrows(
            IllegalArgumentException.class,
            () -> service.listScreenings(null, null, "not-a-date", 1, 20)
        );
    }

    @Test
    void getScreeningReturnsExistingScreening() throws Exception {
        ScreeningService service = createService();
        var created = service.createScreening(
            1, "Main Hall", "2026-11-01T19:30:00Z"
        );

        var found = service.getScreening(created.screeningId());

        assertTrue(found.isPresent());
        assertEquals(created.screeningId(), found.get().screeningId());
        assertEquals("Test Film", found.get().filmTitle());
    }

    @Test
    void getScreeningReturnsEmptyForMissingScreening() throws Exception {
        ScreeningService service = createService();

        assertTrue(service.getScreening(9999).isEmpty());
    }

    @Test
    void getScreeningRejectsNonPositiveId() throws Exception {
        ScreeningService service = createService();

        assertThrows(
            IllegalArgumentException.class,
            () -> service.getScreening(0)
        );
    }

    @Test
    void updateScreeningChangesDetails() throws Exception {
        ScreeningService service = createService();
        var created = service.createScreening(
            1, "Main Hall", "2026-11-01T19:30:00Z"
        );

        var updated = service.updateScreening(
            created.screeningId(), 2, "Studio B", "2026-11-03T20:00:00Z"
        );

        assertTrue(updated.isPresent());
        assertEquals("Studio B", updated.get().venue());
        assertEquals("Second Film", updated.get().filmTitle());
        assertEquals("2026-11-03T20:00:00Z", updated.get().screeningTime());
    }

    @Test
    void updateScreeningReturnsEmptyForMissingScreening() throws Exception {
        ScreeningService service = createService();

        assertTrue(
            service.updateScreening(
                9999, 1, "Main Hall", "2026-11-01T19:30:00Z"
            ).isEmpty()
        );
    }

    @Test
    void deleteScreeningRemovesExistingScreening() throws Exception {
        ScreeningService service = createService();
        var created = service.createScreening(
            1, "Main Hall", "2026-11-01T19:30:00Z"
        );

        assertTrue(service.deleteScreening(created.screeningId()));
        assertTrue(service.getScreening(created.screeningId()).isEmpty());
    }

    @Test
    void deleteScreeningReturnsFalseForMissingScreening() throws Exception {
        ScreeningService service = createService();

        assertFalse(service.deleteScreening(9999));
    }

    @Test
    void deleteScreeningRejectsNonPositiveId() throws Exception {
        ScreeningService service = createService();

        assertThrows(
            IllegalArgumentException.class,
            () -> service.deleteScreening(-1)
        );
    }
}
