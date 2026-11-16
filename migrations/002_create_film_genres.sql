CREATE TABLE IF NOT EXISTS film_genres (
    film_id INTEGER NOT NULL,
    genre_id INTEGER NOT NULL,
    PRIMARY KEY (film_id, genre_id),
    FOREIGN KEY (film_id)
        REFERENCES films(film_id) ON DELETE CASCADE,
    FOREIGN KEY (genre_id)
        REFERENCES genres(genre_id) ON DELETE CASCADE
);
