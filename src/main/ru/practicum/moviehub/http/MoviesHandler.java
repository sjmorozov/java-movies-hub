package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.CreateMovieRequest;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

class MoviesHandler extends BaseHttpHandler {

    private final MoviesStore store;
    private static final int STATUS_OK = 200;
    private static final int STATUS_CREATED = 201;
    private final Gson gson = new Gson();

    MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {

        String method = ex.getRequestMethod();

        switch (method) {
            case "GET": {
                String moviesJson = gson.toJson(store.getAllMovies());
                sendJson(ex, STATUS_OK, moviesJson);
                break;
            }
            case "POST": {
                String requestBody;

                try (InputStream requestBodyStream = ex.getRequestBody()) {
                    byte[] requestBytes = requestBodyStream.readAllBytes();
                    requestBody = new String(requestBytes, StandardCharsets.UTF_8);
                }

                CreateMovieRequest createMovieRequest = gson.fromJson(requestBody, CreateMovieRequest.class);
                Movie createdMovie = store.addMovie(createMovieRequest.getTitle(), createMovieRequest.getYear());
                String createdMovieJson = gson.toJson(createdMovie);
                sendJson(ex, STATUS_CREATED, createdMovieJson);
                break;
            }
        }

    }
}
