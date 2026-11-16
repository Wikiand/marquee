CREATE INDEX IF NOT EXISTS idx_film_genres_genre
ON film_genres(genre_id, film_id);

CREATE INDEX IF NOT EXISTS idx_screenings_film
ON screenings(film_id);
