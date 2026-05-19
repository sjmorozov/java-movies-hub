package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.CreateMovieRequest;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonSyntaxException;

class MoviesHandler extends BaseHttpHandler {

    private final MoviesStore store;

    private static final int STATUS_OK = 200;
    private static final int STATUS_CREATED = 201;
    private static final int STATUS_UNPROCESSABLE_ENTITY = 422;
    private static final int STATUS_UNSUPPORTED_MEDIA_TYPE = 415;
    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_NO_CONTENT = 204;
    private static final int STATUS_METHOD_NOT_ALLOWED = 405;

    private static final String MOVIE_NOT_FOUND_ERROR = "Фильм не найден";
    private static final int MIN_YEAR = 1888;
    private static final int MAX_ALLOWED_YEAR = LocalDate.now().getYear() + 1;
    private static final String VALIDATION_ERROR_MESSAGE = "Ошибка валидации";
    private static final String EMPTY_TITLE_DETAIL = "название не должно быть пустым";
    private static final String NULL_REQUEST = "запрос пустой";
    private static final String TOO_LONG_TITLE = "название не должно быть длиннее 100 символов";
    private static final String NULL_YEAR = "год должен быть указан";
    private static final String YEAR_RANGE = "год должен быть между 1888 и " + MAX_ALLOWED_YEAR;
    private static final String UNSUPPORTED_MEDIA_TYPE_ERROR = "Неподдерживаемый тип содержимого";
    private static final String UNSUPPORTED_MEDIA_TYPE_DETAIL = "Ожидается Content-Type: application/json";
    private static final String INVALID_MOVIE_ID_ERROR = "Некорректный id фильма";
    private static final String INVALID_YEAR_ERROR = "Некорректный год";
    private static final String METHOD_NOT_ALLOWED_ERROR = "Метод не поддерживается";
    private static final String INVALID_JSON_ERROR = "Некорректный JSON";

    private final Gson gson = new Gson();

    MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {

        String method = ex.getRequestMethod();

        switch (method) {
            case "GET": {
                handleGet(ex);
                break;
            }

            case "POST": {
                if (!isJsonContentType(ex)) {
                    ErrorResponse errorResponse = new ErrorResponse(UNSUPPORTED_MEDIA_TYPE_ERROR, List.of(UNSUPPORTED_MEDIA_TYPE_DETAIL));
                    sendJson(ex, STATUS_UNSUPPORTED_MEDIA_TYPE, gson.toJson(errorResponse));
                    break;
                }

                String requestBody;

                try (InputStream requestBodyStream = ex.getRequestBody()) {
                    byte[] requestBytes = requestBodyStream.readAllBytes();
                    requestBody = new String(requestBytes, StandardCharsets.UTF_8);
                }

                CreateMovieRequest createMovieRequest;

                try {
                    createMovieRequest = gson.fromJson(requestBody, CreateMovieRequest.class);
                } catch (JsonSyntaxException e) {
                    ErrorResponse errorResponse = new ErrorResponse(INVALID_JSON_ERROR, List.of());
                    sendJson(ex, STATUS_BAD_REQUEST, gson.toJson(errorResponse));
                    break;
                }

                List<String> validationDetails = validateCreateMovieRequest(createMovieRequest);
                if (!validationDetails.isEmpty()) {
                    ErrorResponse errorResponse = new ErrorResponse(VALIDATION_ERROR_MESSAGE, validationDetails);
                    sendJson(ex, STATUS_UNPROCESSABLE_ENTITY, gson.toJson(errorResponse));
                    break;
                }

                Movie createdMovie = store.addMovie(createMovieRequest.getTitle(), createMovieRequest.getYear());
                String createdMovieJson = gson.toJson(createdMovie);
                sendJson(ex, STATUS_CREATED, createdMovieJson);
                break;
            }
            case "DELETE": {
                handleDelete(ex);
                break;
            }
            default: {
                ex.getResponseHeaders().set("Allow", "GET, POST, DELETE");

                ErrorResponse errorResponse = new ErrorResponse(METHOD_NOT_ALLOWED_ERROR, List.of());
                sendJson(ex, STATUS_METHOD_NOT_ALLOWED, gson.toJson(errorResponse));
                break;
            }
        }
    }

    private List<String> validateCreateMovieRequest(CreateMovieRequest request) {
        List<String> details = new ArrayList<>();
        if (request == null) {
            details.add(NULL_REQUEST);
            return details;
        }
        String title = request.getTitle();
        Integer year = request.getYear();

        if (title == null || title.isBlank()) {
            details.add(EMPTY_TITLE_DETAIL);
        } else if (title.length() > 100) {
            details.add(TOO_LONG_TITLE);
        }

        if (year == null) {
            details.add(NULL_YEAR);
        } else if (year > MAX_ALLOWED_YEAR || year < MIN_YEAR) {
            details.add(YEAR_RANGE);
        }

        return details;
    }

    private boolean isJsonContentType(HttpExchange ex) {
        Headers headers = ex.getRequestHeaders();
        String contentTypeHeaderValue = headers.getFirst("Content-Type");

        return contentTypeHeaderValue != null
                && contentTypeHeaderValue.toLowerCase().startsWith("application/json");
    }

    private void handleGet(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        if ("/movies".equals(path)) {
            String query = ex.getRequestURI().getQuery();

            if (query == null) {
                String moviesJson = gson.toJson(store.getAllMovies());
                sendJson(ex, STATUS_OK, moviesJson);
                return;
            }

            if (query.startsWith("year=")) {
                String yearText = query.substring("year=".length());

                int year;
                try {
                    year = Integer.parseInt(yearText);
                } catch (NumberFormatException e) {
                    ErrorResponse errorResponse = new ErrorResponse(INVALID_YEAR_ERROR, List.of());
                    sendJson(ex, STATUS_BAD_REQUEST, gson.toJson(errorResponse));
                    return;
                }

                String moviesJson = gson.toJson(store.getMoviesByYear(year));
                sendJson(ex, STATUS_OK, moviesJson);
                return;
            }
        }

        if (path.startsWith("/movies/")) {
            String idText = path.substring("/movies/".length());

            int movieId;
            try {
                movieId = Integer.parseInt(idText);
            } catch (NumberFormatException e) {
                ErrorResponse errorResponse = new ErrorResponse(INVALID_MOVIE_ID_ERROR, List.of());
                sendJson(ex, STATUS_BAD_REQUEST, gson.toJson(errorResponse));
                return;
            }

            Movie movie = store.getMovieById(movieId).orElse(null);

            if (movie == null) {
                ErrorResponse errorResponse = new ErrorResponse(MOVIE_NOT_FOUND_ERROR, List.of());
                sendJson(ex, STATUS_NOT_FOUND, gson.toJson(errorResponse));
                return;
            }

            String movieJson = gson.toJson(movie);
            sendJson(ex, STATUS_OK, movieJson);
        }
    }

    private void handleDelete(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        if (!path.startsWith("/movies/")) {
            ErrorResponse errorResponse = new ErrorResponse(MOVIE_NOT_FOUND_ERROR, List.of());
            sendJson(ex, STATUS_NOT_FOUND, gson.toJson(errorResponse));
            return;
        }

        String idText = path.substring("/movies/".length());

        int movieId;
        try {
            movieId = Integer.parseInt(idText);
        } catch (NumberFormatException e) {
            ErrorResponse errorResponse = new ErrorResponse(INVALID_MOVIE_ID_ERROR, List.of());
            sendJson(ex, STATUS_BAD_REQUEST, gson.toJson(errorResponse));
            return;
        }

        boolean deleted = store.deleteMovieById(movieId);

        if (!deleted) {
            ErrorResponse errorResponse = new ErrorResponse(MOVIE_NOT_FOUND_ERROR, List.of());
            sendJson(ex, STATUS_NOT_FOUND, gson.toJson(errorResponse));
            return;
        }

        sendNoContent(ex);
    }
}
