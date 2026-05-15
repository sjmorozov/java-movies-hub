package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MoviesStore {
    private final Map<Integer, Movie> moviesById = new LinkedHashMap<>();
    private int nextId = 1;

    public List<Movie> getAllMovies() {
        return new ArrayList<>(moviesById.values());
    }

    public Movie addMovie(String title, int year) {
        int movieId = nextId;
        Movie movie = new Movie(movieId, title, year);
        moviesById.put(movieId, movie);
        nextId++;
        return movie;
    }

    public Optional<Movie> getMovieById(int id) {
        return Optional.ofNullable(moviesById.get(id));
    }

    public boolean deleteMovieById(int id) {
        return moviesById.remove(id) != null;
    }

    public void clear() {
        moviesById.clear();
        nextId = 1;
    }
}
