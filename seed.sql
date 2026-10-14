-- Marquee catalogue seed data
-- Run after the database schema has been initialized.
-- Safe to rerun: explicit IDs and ON CONFLICT prevent duplicates.

PRAGMA foreign_keys = ON;

BEGIN TRANSACTION;

INSERT INTO genres (genre_id, name) VALUES
    (1, 'Action'),
    (2, 'Comedy'),
    (3, 'Drama'),
    (4, 'Science Fiction'),
    (5, 'Horror'),
    (6, 'Documentary'),
    (7, 'Animation'),
    (8, 'Thriller')
ON CONFLICT DO NOTHING;

INSERT INTO films (film_id, title, year, description) VALUES
    (1, 'The Invisible Fight', 2023,
     'A Soviet Estonian security guard discovers an unexpected calling.'),
    (2, '2001: A Space Odyssey', 1968,
     'An enigmatic monolith accompanies humanity on a journey through space.'),
    (3, 'Inception', 2010,
     'A specialist enters dreams to carry out an unusual mission.'),
    (4, 'Parasite', 2019,
     'Two families become entangled across a sharp class divide.'),
    (5, 'Get Out', 2017,
     'A weekend visit reveals increasingly disturbing secrets.'),
    (6, 'Spirited Away', 2001,
     'A young girl enters a mysterious world of spirits.'),
    (7, 'The Grand Budapest Hotel', 2014,
     'A hotel concierge and his protégé become embroiled in an adventure.'),
    (8, 'Arrival', 2016,
     'A linguist works to communicate with visitors from another world.'),
    (9, 'Moonlight', 2016,
     'A young man navigates identity, family, and belonging.'),
    (10, 'The Thing', 1982,
     'Researchers in Antarctica face a shape-shifting threat.'),
    (11, 'Mad Max: Fury Road', 2015,
     'Survivors cross a harsh desert in a high-speed pursuit.'),
    (12, 'My Neighbor Totoro', 1988,
     'Two sisters discover magical forest creatures.'),
    (13, 'The Social Network', 2010,
     'A new social platform changes friendships and ambitions.'),
    (14, 'Blade Runner 2049', 2017,
     'A replicant uncovers a secret that could reshape society.'),
    (15, 'Whiplash', 2014,
     'A drummer faces an intense and demanding music instructor.'),
    (16, 'Knives Out', 2019,
     'A detective investigates a wealthy family after a suspicious death.'),
    (17, 'The Act of Killing', 2012,
     'Former Indonesian death squad leaders revisit their past.'),
    (18, 'Coco', 2017,
     'A young musician travels to the Land of the Dead.'),
    (19, 'Alien', 1979,
     'A space crew encounters a deadly life form.'),
    (20, 'Everything Everywhere All at Once', 2022,
     'A laundromat owner encounters multiple versions of reality.'),
    (21, 'The Lighthouse', 2019,
     'Two lighthouse keepers struggle with isolation and reality.'),
    (22, 'Free Solo', 2018,
     'A climber prepares to scale El Capitan without ropes.'),
    (23, 'The Iron Giant', 1999,
     'A boy befriends a mysterious giant robot.'),
    (24, '12 Angry Men', 1957,
     'A jury deliberates a difficult murder case.')
ON CONFLICT DO NOTHING;

INSERT INTO film_genres (film_id, genre_id) VALUES
    (1, 1), (1, 2),
    (2, 4), (2, 3),
    (3, 1), (3, 4), (3, 8),
    (4, 3), (4, 8),
    (5, 5), (5, 8),
    (6, 7), (6, 2),
    (7, 2), (7, 3),
    (8, 4), (8, 3),
    (9, 3),
    (10, 5), (10, 4),
    (11, 1), (11, 4),
    (12, 7), (12, 2),
    (13, 3),
    (14, 4), (14, 8),
    (15, 3),
    (16, 2), (16, 8),
    (17, 6), (17, 3),
    (18, 7), (18, 2),
    (19, 5), (19, 4),
    (20, 1), (20, 2), (20, 4),
    (21, 5), (21, 3),
    (22, 6),
    (23, 7), (23, 4),
    (24, 3)
ON CONFLICT DO NOTHING;

INSERT INTO screenings
    (screening_id, film_id, venue, screening_time) VALUES
    (1, 1, 'Main Hall', '2026-10-20T19:30:00Z'),
    (2, 2, 'Rooftop Cinema', '2026-10-21T18:00:00Z'),
    (3, 3, 'Main Hall', '2026-10-22T19:00:00Z'),
    (4, 4, 'Studio A', '2026-10-23T20:00:00Z'),
    (5, 5, 'Studio B', '2026-10-24T21:00:00Z'),
    (6, 6, 'Main Hall', '2026-10-25T15:00:00Z'),
    (7, 7, 'Rooftop Cinema', '2026-10-26T18:30:00Z'),
    (8, 8, 'Studio A', '2026-10-27T19:00:00Z'),
    (9, 9, 'Main Hall', '2026-10-28T19:30:00Z'),
    (10, 10, 'Studio B', '2026-10-29T21:00:00Z'),
    (11, 11, 'Main Hall', '2026-10-30T20:00:00Z'),
    (12, 12, 'Community Cinema', '2026-10-31T14:00:00Z'),
    (13, 13, 'Studio A', '2026-11-01T18:00:00Z'),
    (14, 14, 'Rooftop Cinema', '2026-11-02T19:00:00Z'),
    (15, 15, 'Main Hall', '2026-11-03T19:30:00Z'),
    (16, 16, 'Studio B', '2026-11-04T20:00:00Z'),
    (17, 17, 'Community Cinema', '2026-11-05T18:00:00Z'),
    (18, 18, 'Main Hall', '2026-11-06T15:00:00Z'),
    (19, 19, 'Studio B', '2026-11-07T21:00:00Z'),
    (20, 20, 'Rooftop Cinema', '2026-11-08T19:00:00Z'),
    (21, 21, 'Studio A', '2026-11-09T20:00:00Z'),
    (22, 22, 'Community Cinema', '2026-11-10T18:00:00Z'),
    (23, 23, 'Main Hall', '2026-11-11T15:00:00Z'),
    (24, 24, 'Studio A', '2026-11-12T19:00:00Z'),
    (25, 1, 'Community Cinema', '2026-11-13T18:00:00Z'),
    (26, 2, 'Main Hall', '2026-11-14T19:00:00Z'),
    (27, 5, 'Studio B', '2026-11-15T21:00:00Z'),
    (28, 8, 'Rooftop Cinema', '2026-11-16T19:30:00Z'),
    (29, 16, 'Main Hall', '2026-11-17T18:00:00Z'),
    (30, 20, 'Studio A', '2026-11-18T20:00:00Z')
ON CONFLICT DO NOTHING;

COMMIT;
