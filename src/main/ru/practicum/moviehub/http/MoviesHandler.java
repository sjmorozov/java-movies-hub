package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;

class MoviesHandler extends BaseHttpHandler {

    private final MoviesStore store;
    private static final int STATUS_OK = 200;
    private final Gson gson = new Gson();

    MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {

        String method = ex.getRequestMethod();

        if (method.equalsIgnoreCase("GET")) {
            String moviesJson = gson.toJson(store.getAllMovies());
            sendJson(ex, STATUS_OK, moviesJson);
        }
    }
}
