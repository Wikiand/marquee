CREATE TABLE IF NOT EXISTS screenings (
    screening_id INTEGER PRIMARY KEY AUTOINCREMENT,
    film_id INTEGER NOT NULL,
    venue TEXT NOT NULL CHECK (length(trim(venue)) > 0),
    screening_time TEXT NOT NULL,
    FOREIGN KEY (film_id)
        REFERENCES films(film_id) ON DELETE CASCADE
);
