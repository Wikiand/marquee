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

        return new ScreeningService(new ScreeningRepository(database));
    }

    @Test
    void createScreeningReturnsCreatedScreening() throws Exception {
        ScreeningService service = createService();

        var screening = service.createScreening(
            1, "Main Hall", "2026-11-01T19:30:00Z"
        );

        assertTrue(screening.screeningId() > 0);
        assertEquals(1, screening.filmId());
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

        assertEquals(2, service.listScreenings().size());
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
            created.screeningId(), 1, "Studio B", "2026-11-03T20:00:00Z"
        );

        assertTrue(updated.isPresent());
        assertEquals("Studio B", updated.get().venue());
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
