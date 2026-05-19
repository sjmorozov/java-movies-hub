package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {

    private static final int PORT = 8080;
    private static final String BASE = "http://localhost:" + PORT;
    private static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";

    private static MoviesServer server;
    private static HttpClient client;
    private static MoviesStore store;
    private static Gson gson;

    @BeforeAll
    static void beforeAll() {
        store = new MoviesStore();
        server = new MoviesServer(store, PORT);
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        gson = new Gson();
        server.start();
    }

    @BeforeEach
    void beforeEach() {
        store.clear();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpResponse<String> response = sendGet("/movies");

        assertEquals(200, response.statusCode(), "GET /movies должен вернуть 200");

        List<Movie> movies = assertJsonMoviesArrayResponse(response);

        assertEquals(0, movies.size(), "Список фильмов должен быть пустым");
    }

    @Test
    void getMovies_whenMoviesExist_returnsMoviesList() throws Exception {
        store.addMovie("Прибытие поезда", 1896);
        store.addMovie("Хакеры", 1995);

        HttpResponse<String> response = sendGet("/movies");

        assertEquals(200, response.statusCode(), "GET /movies должен вернуть 200");

        List<Movie> movies = assertJsonMoviesArrayResponse(response);

        assertEquals(2, movies.size(), "Количество фильмов должно быть 2");

        assertMovie(movies.get(0), 1, "Прибытие поезда", 1896);
        assertMovie(movies.get(1), 2, "Хакеры", 1995);
    }

    @Test
    void postMovies_whenValidMovie_returnsCreatedMovie() throws Exception {
        HttpResponse<String> response =
                sendPost("/movies", movieRequestJson("Метрополис", 1927));

        assertEquals(201, response.statusCode(), "POST /movies должен вернуть 201");

        JsonObject movie = assertJsonObjectResponse(response);

        assertEquals(1, store.getAllMovies().size(),
                "Фильм добавился в хранилище, фильмов в нём 1");
        assertMovie(movie, 1, "Метрополис", 1927);
    }

    @Test
    void postMovies_whenTitleIsEmpty_returnsValidationError() throws Exception {
        HttpResponse<String> response =
                sendPost("/movies", movieRequestJson("", 1927));

        assertValidationError(response, "название не должно быть пустым");

        assertEquals(0, store.getAllMovies().size(),
                "Фильм не должен добавиться в хранилище");
    }

    @Test
    void postMovies_whenTitleIsTooLong_returnsValidationError() throws Exception {
        String longTitle = "А".repeat(101);

        HttpResponse<String> response =
                sendPost("/movies", movieRequestJson(longTitle, 1927));

        assertValidationError(response, "название не должно быть длиннее 100 символов");

        assertEquals(0, store.getAllMovies().size(),
                "Фильм не должен добавиться в хранилище");
    }

    @Test
    void postMovies_whenYearIsBefore1888_returnsValidationError() throws Exception {
        HttpResponse<String> response =
                sendPost("/movies", movieRequestJson("Метрополис", 1887));

        int maxAllowedYear = LocalDate.now().getYear() + 1;

        assertValidationError(response, "год должен быть между 1888 и " + maxAllowedYear);

        assertEquals(0, store.getAllMovies().size(),
                "Фильм не должен добавиться в хранилище");
    }

    @Test
    void postMovies_whenYearIsAfterMaxAllowed_returnsValidationError() throws Exception {
        int invalidYear = LocalDate.now().getYear() + 2;
        int maxAllowedYear = LocalDate.now().getYear() + 1;

        HttpResponse<String> response =
                sendPost("/movies", movieRequestJson("Бегущий по лезвию", invalidYear));

        assertValidationError(response, "год должен быть между 1888 и " + maxAllowedYear);

        assertEquals(0, store.getAllMovies().size(),
                "Фильм не должен добавиться в хранилище");
    }

    @Test
    void postMovies_whenContentTypeIsNotJson_returnsUnsupportedMediaType() throws Exception {
        HttpResponse<String> response = sendPost(
                "/movies",
                movieRequestJson("Метрополис", 1927),
                "text/plain; charset=UTF-8"
        );

        assertErrorResponse(response, 415, "Неподдерживаемый тип содержимого");

        assertEquals(0, store.getAllMovies().size(),
                "Фильм не должен добавиться в хранилище");
    }

    @Test
    void getMovieById_whenMovieExists_returnsMovie() throws Exception {
        store.addMovie("Метрополис", 1927);

        HttpResponse<String> response = sendGet("/movies/1");

        assertEquals(200, response.statusCode(),
                "GET /movies/{id} для существующего фильма должен вернуть 200");

        JsonObject movie = assertJsonObjectResponse(response);

        assertMovie(movie, 1, "Метрополис", 1927);
    }

    @Test
    void getMovieById_whenMovieDoesNotExist_returnsNotFound() throws Exception {
        store.addMovie("Метрополис", 1927);

        HttpResponse<String> response = sendGet("/movies/999");

        assertErrorResponse(response, 404, "Фильм не найден");
    }

    @Test
    void getMovieById_whenIdIsNotNumber_returnsBadRequest() throws Exception {
        HttpResponse<String> response = sendGet("/movies/abc");

        assertErrorResponse(response, 400, "Некорректный id фильма");
    }

    @Test
    void deleteMovieById_whenMovieExists_returnsNoContent() throws Exception {
        store.addMovie("Метрополис", 1927);

        HttpResponse<String> response = sendDelete("/movies/1");

        assertEquals(204, response.statusCode(),
                "DELETE /movies/{id} для существующего фильма должен вернуть 204");
        assertEquals(0, store.getAllMovies().size(),
                "Фильм должен быть удалён из хранилища");
    }

    @Test
    void deleteMovieById_whenMovieDoesNotExist_returnsNotFound() throws Exception {
        store.addMovie("Метрополис", 1927);

        HttpResponse<String> response = sendDelete("/movies/999");

        assertErrorResponse(response, 404, "Фильм не найден");

        assertEquals(1, store.getAllMovies().size(),
                "Несуществующий фильм не удалён, существующий фильм должен остаться");
    }

    @Test
    void deleteMovieById_whenIdIsNotNumber_returnsBadRequest() throws Exception {
        store.addMovie("Метрополис", 1927);

        HttpResponse<String> response = sendDelete("/movies/abc");

        assertErrorResponse(response, 400, "Некорректный id фильма");

        assertEquals(1, store.getAllMovies().size(),
                "Фильм не должен быть удалён при некорректном id");
    }

    @Test
    void getMovies_whenYearQueryIsValid_returnsMoviesByYear() throws Exception {
        store.addMovie("Хакеры", 1995);
        store.addMovie("Схватка", 1995);
        store.addMovie("Метрополис", 1927);

        HttpResponse<String> response = sendGet("/movies?year=1995");

        assertEquals(200, response.statusCode(),
                "GET /movies?year=YYYY должен вернуть 200");

        List<Movie> movies = assertJsonMoviesArrayResponse(response);

        assertEquals(2, movies.size(),
                "Должны вернуться только фильмы указанного года");

        assertMovie(movies.get(0), 1, "Хакеры", 1995);
        assertMovie(movies.get(1), 2, "Схватка", 1995);
    }

    @Test
    void getMovies_whenYearQueryIsNotNumber_returnsBadRequest() throws Exception {
        store.addMovie("Хакеры", 1995);

        HttpResponse<String> response = sendGet("/movies?year=abc");

        assertErrorResponse(response, 400, "Некорректный год");
    }

    @Test
    void movies_whenMethodIsNotSupported_returnsMethodNotAllowed() throws Exception {
        HttpResponse<String> response =
                sendPut("/movies", movieRequestJson("Метрополис", 1927));

        assertErrorResponse(response, 405, "Метод не поддерживается");
    }

    @Test
    void postMovies_whenJsonIsMalformed_returnsBadRequest() throws Exception {
        String requestBody = """
                {
                  "title": "Метрополис",
                  "year": 1927
                """;

        HttpResponse<String> response = sendPost("/movies", requestBody);

        assertErrorResponse(response, 400, "Некорректный JSON");

        assertEquals(0, store.getAllMovies().size(),
                "Фильм не должен добавиться в хранилище");
    }

    @Test
    void getMovies_whenQueryParameterIsUnknown_returnsBadRequest() throws Exception {
        HttpResponse<String> response = sendGet("/movies?abc=123");

        assertErrorResponse(response, 400, "Некорректный параметр запроса");
    }

    @Test
    void postMovies_whenPathHasId_returnsNotFound() throws Exception {
        HttpResponse<String> response =
                sendPost("/movies/1", movieRequestJson("Метрополис", 1927));

        assertErrorResponse(response, 404, "Ресурс не найден");

        assertEquals(0, store.getAllMovies().size(),
                "Фильм не должен добавиться в хранилище");
    }

    @Test
    void postMovies_whenContentTypeIsMissing_returnsUnsupportedMediaType() throws Exception {
        HttpResponse<String> response =
                sendPost("/movies", movieRequestJson("Метрополис", 1927), null);

        assertErrorResponse(response, 415, "Неподдерживаемый тип содержимого");

        assertEquals(0, store.getAllMovies().size(),
                "Фильм не должен добавиться в хранилище");
    }

    @Test
    void postMovies_whenYearIsMissing_returnsValidationError() throws Exception {
        String requestBody = """
                {
                  "title": "Метрополис"
                }
                """;

        HttpResponse<String> response = sendPost("/movies", requestBody);

        assertValidationError(response, "год должен быть указан");

        assertEquals(0, store.getAllMovies().size(),
                "Фильм не должен добавиться в хранилище");
    }

    @Test
    void getMovies_whenYearQueryIsValidButNoMoviesFound_returnsEmptyArray() throws Exception {
        store.addMovie("Метрополис", 1927);
        store.addMovie("Хакеры", 1995);

        HttpResponse<String> response = sendGet("/movies?year=2001");

        assertEquals(200, response.statusCode(),
                "GET /movies?year=YYYY должен вернуть 200 даже если фильмов такого года нет");

        List<Movie> movies = assertJsonMoviesArrayResponse(response);

        assertEquals(0, movies.size(),
                "Если фильмов указанного года нет, должен вернуться пустой список");
    }

    @Test
    void getMovies_whenPathHasTrailingSlash_returnsMoviesList() throws Exception {
        store.addMovie("Хакеры", 1995);

        HttpResponse<String> response = sendGet("/movies/");

        assertEquals(200, response.statusCode(), "GET /movies/ должен вернуть 200");

        List<Movie> movies = assertJsonMoviesArrayResponse(response);

        assertEquals(1, movies.size(), "Должен вернуться список фильмов");
        assertMovie(movies.get(0), 1, "Хакеры", 1995);
    }

    @Test
    void postMovies_whenPathHasTrailingSlash_createsMovie() throws Exception {
        HttpResponse<String> response =
                sendPost("/movies/", movieRequestJson("Хакеры", 1995));

        assertEquals(201, response.statusCode(), "POST /movies/ должен создать фильм");

        assertEquals(1, store.getAllMovies().size(),
                "Фильм должен добавиться в хранилище");

        JsonObject movie = assertJsonObjectResponse(response);

        assertMovie(movie, 1, "Хакеры", 1995);
    }

    @Test
    void postMovies_whenTitleHasSpacesAround_savesTrimmedTitle() throws Exception {
        HttpResponse<String> response =
                sendPost("/movies", movieRequestJson(" Метрополис 2 ", 1927));

        assertEquals(201, response.statusCode(),
                "POST /movies с пробелами по краям title должен создать фильм");

        JsonObject movie = assertJsonObjectResponse(response);

        assertMovie(movie, 1, "Метрополис 2", 1927);
    }

    @Test
    void postMovies_whenTitleWithSpacesIsLongerThan100ButTrimmedIsValid_createsMovie()
            throws Exception {
        String title = "  " + "А".repeat(99) + "  ";

        HttpResponse<String> response = sendPost("/movies", movieRequestJson(title, 1927));

        assertEquals(201, response.statusCode(),
                "Если title после обрезки пробелов не длиннее 100 символов, фильм должен создаться");

        JsonObject movie = assertJsonObjectResponse(response);

        assertMovie(movie, 1, "А".repeat(99), 1927);
    }

    private HttpResponse<String> sendGet(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .GET()
                .timeout(Duration.ofSeconds(2))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendDelete(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .DELETE()
                .timeout(Duration.ofSeconds(2))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendPost(String path, String requestBody) throws Exception {
        return sendPost(path, requestBody, JSON_CONTENT_TYPE);
    }

    private HttpResponse<String> sendPost(String path, String requestBody, String contentType)
            throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(2));

        if (contentType != null) {
            requestBuilder.header("Content-Type", contentType);
        }

        return client.send(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendPut(String path, String requestBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .method("PUT", HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(2))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String movieRequestJson(String title, int year) {
        return """
                {
                  "title": "%s",
                  "year": %d
                }
                """.formatted(title, year);
    }

    private void assertJsonContentType(HttpResponse<String> response) {
        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");

        assertEquals(JSON_CONTENT_TYPE, contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");
    }

    private JsonObject assertJsonObjectResponse(HttpResponse<String> response) {
        assertJsonContentType(response);

        String body = response.body().trim();

        assertTrue(body.startsWith("{") && body.endsWith("}"),
                "Ожидается JSON-объект");

        return JsonParser.parseString(body).getAsJsonObject();
    }

    private List<Movie> assertJsonMoviesArrayResponse(HttpResponse<String> response) {
        assertJsonContentType(response);

        String body = response.body().trim();

        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");

        return gson.fromJson(body, new ListOfMoviesTypeToken().getType());
    }

    private JsonObject assertErrorResponse(HttpResponse<String> response,
                                           int expectedStatus,
                                           String expectedError) {
        assertEquals(expectedStatus, response.statusCode(),
                "Ожидается HTTP-статус " + expectedStatus);

        JsonObject errorResponse = assertJsonObjectResponse(response);

        assertEquals(expectedError, errorResponse.get("error").getAsString(),
                "Поле error должно содержать ожидаемое сообщение");

        return errorResponse;
    }

    private void assertValidationError(HttpResponse<String> response, String expectedDetail) {
        JsonObject errorResponse =
                assertErrorResponse(response, 422, "Ошибка валидации");

        JsonArray details = errorResponse.getAsJsonArray("details");

        assertEquals(1, details.size(),
                "В массиве details должна быть одна ошибка");

        assertEquals(expectedDetail, details.get(0).getAsString(),
                "Деталь ошибки должна совпадать");
    }

    private void assertMovie(Movie movie, int expectedId, String expectedTitle, int expectedYear) {
        assertEquals(expectedId, movie.getId(), "id фильма должен совпадать");
        assertEquals(expectedTitle, movie.getTitle(), "Название фильма должно совпадать");
        assertEquals(expectedYear, movie.getYear(), "Год фильма должен совпадать");
    }

    private void assertMovie(JsonObject movie, int expectedId, String expectedTitle, int expectedYear) {
        assertEquals(expectedId, movie.get("id").getAsInt(), "id фильма должен совпадать");
        assertEquals(expectedTitle, movie.get("title").getAsString(),
                "Название фильма должно совпадать");
        assertEquals(expectedYear, movie.get("year").getAsInt(),
                "Год фильма должен совпадать");
    }
}
