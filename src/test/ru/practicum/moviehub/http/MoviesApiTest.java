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

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");

        List<Movie> movies = gson.fromJson(body, new ListOfMoviesTypeToken().getType());

        assertEquals(0, movies.size(),
                "Список фильмов должен быть пустым");
    }

    @Test
    void getMovies_whenMoviesExist_returnsMoviesList() throws Exception {
        store.addMovie("Прибытие поезда", 1896);
        store.addMovie("Хакеры", 1995);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");

        List<Movie> movies = gson.fromJson(body, new ListOfMoviesTypeToken().getType());

        assertEquals(2, movies.size(), "Количество фильмов должно быть 2");

        Movie firstMovie = movies.get(0);
        assertEquals(1, firstMovie.getId(), "id первого фильма должен быть 1");
        assertEquals("Прибытие поезда", firstMovie.getTitle(),
                "Название первого фильма должно быть Прибытие поезда");
        assertEquals(1896, firstMovie.getYear(),
                "Год первого фильма должен быть 1896");

        Movie secondMovie = movies.get(1);
        assertEquals(2, secondMovie.getId(), "id второго фильма должен быть 2");
        assertEquals("Хакеры", secondMovie.getTitle(),
                "Название второго фильма должно быть Хакеры");
        assertEquals(1995, secondMovie.getYear(),
                "Год второго фильма должен быть 1995");
    }

    @Test
    void postMovies_whenValidMovie_returnsCreatedMovie() throws Exception {
        String requestBody = """
                {
                  "title": "Метрополис",
                  "year": 1927
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(201, response.statusCode(), "POST /movies должен вернуть 201");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = response.body().trim();
        assertTrue(body.startsWith("{") && body.endsWith("}"),
                "Ожидается JSON-объект");

        JsonObject movie = JsonParser.parseString(body).getAsJsonObject();
        assertEquals(1, store.getAllMovies().size(), "Фильм добавился в хранилище, фильмов в нём 1");

        assertEquals(1, movie.get("id").getAsInt(), "ID созданного фильма должен быть 1");
        assertEquals("Метрополис", movie.get("title").getAsString(), "Название созданного фильма должно быть Метрополис");
        assertEquals(1927, movie.get("year").getAsInt(), "Год созданного фильма должен быть 1927");
    }

    @Test
    void postMovies_whenTitleIsEmpty_returnsValidationError() throws Exception {
        String requestBody = """
                {
                   "title": "",
                   "year": 1927
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, response.statusCode(), "POST /movies с пустым title должен вернуть 422");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = response.body().trim();
        assertTrue(body.startsWith("{") && body.endsWith("}"),
                "Ожидается JSON-объект");

        JsonObject errorResponse = JsonParser.parseString(body).getAsJsonObject();
        assertEquals(0, store.getAllMovies().size(), "Фильм не добавился в хранилище, фильмов в нём 0");

        assertEquals("Ошибка валидации", errorResponse.get("error").getAsString(), "Поле error должно содержать сообщение об ошибке валидации");
        JsonArray details = errorResponse.getAsJsonArray("details");
        assertEquals(1, details.size(), "В массиве с сообщением об ошибке должно быть 1 значение");
        assertEquals("название не должно быть пустым", details.get(0).getAsString(), "Ожидается сообщение 'название не должно быть пустым'");
    }

    @Test
    void postMovies_whenTitleIsTooLong_returnsValidationError() throws Exception {
        String longTitle = "А".repeat(101);

        String requestBody = """
                {
                  "title": "%s",
                  "year": 1927
                }
                """.formatted(longTitle);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, response.statusCode(),
                "POST /movies с title длиннее 100 символов должен вернуть 422");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = response.body().trim();
        assertTrue(body.startsWith("{") && body.endsWith("}"),
                "Ожидается JSON-объект");

        JsonObject errorResponse = JsonParser.parseString(body).getAsJsonObject();
        assertEquals(0, store.getAllMovies().size(),
                "Фильм не должен добавиться в хранилище");

        assertEquals("Ошибка валидации", errorResponse.get("error").getAsString(),
                "Поле error должно содержать сообщение об ошибке валидации");

        JsonArray details = errorResponse.getAsJsonArray("details");
        assertEquals(1, details.size(),
                "В массиве с сообщением об ошибке должно быть 1 значение");
        assertEquals("название не должно быть длиннее 100 символов", details.get(0).getAsString(),
                "Ожидается сообщение о слишком длинном названии");
    }

    @Test
    void postMovies_whenYearIsBefore1888_returnsValidationError() throws Exception {
        String requestBody = """
                {
                  "title": "Метрополис",
                  "year": 1887
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, response.statusCode(),
                "POST /movies с year меньше 1888 должен вернуть 422");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = response.body().trim();
        assertTrue(body.startsWith("{") && body.endsWith("}"),
                "Ожидается JSON-объект");

        JsonObject errorResponse = JsonParser.parseString(body).getAsJsonObject();
        assertEquals(0, store.getAllMovies().size(),
                "Фильм не должен добавиться в хранилище");

        assertEquals("Ошибка валидации", errorResponse.get("error").getAsString(),
                "Поле error должно содержать сообщение об ошибке валидации");

        JsonArray details = errorResponse.getAsJsonArray("details");
        assertEquals(1, details.size(),
                "В массиве с сообщением об ошибке должно быть 1 значение");

        int maxAllowedYear = LocalDate.now().getYear() + 1;
        assertEquals("год должен быть между 1888 и " + maxAllowedYear, details.get(0).getAsString(),
                "Ожидается сообщение о неверном диапазоне года");
    }

    @Test
    void postMovies_whenYearIsAfterMaxAllowed_returnsValidationError() throws Exception {
        int invalidYear = LocalDate.now().getYear() + 2;
        int maxAllowedYear = LocalDate.now().getYear() + 1;

        String requestBody = """
                {
                  "title": "Бегущий по лезвию",
                  "year": %d
                }
                """.formatted(invalidYear);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, response.statusCode(),
                "POST /movies с year больше текущего года + 1 должен вернуть 422");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = response.body().trim();
        assertTrue(body.startsWith("{") && body.endsWith("}"),
                "Ожидается JSON-объект");

        JsonObject errorResponse = JsonParser.parseString(body).getAsJsonObject();
        assertEquals(0, store.getAllMovies().size(),
                "Фильм не должен добавиться в хранилище");

        assertEquals("Ошибка валидации", errorResponse.get("error").getAsString(),
                "Поле error должно содержать сообщение об ошибке валидации");

        JsonArray details = errorResponse.getAsJsonArray("details");
        assertEquals(1, details.size(),
                "В массиве с сообщением об ошибке должно быть 1 значение");
        assertEquals("год должен быть между 1888 и " + maxAllowedYear, details.get(0).getAsString(),
                "Ожидается сообщение о неверном диапазоне года");
    }

    @Test
    void postMovies_whenContentTypeIsNotJson_returnsUnsupportedMediaType() throws Exception {
        String requestBody = """
                {
                  "title": "Метрополис",
                  "year": 1927
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, response.statusCode(),
                "POST /movies с неверным Content-Type должен вернуть 415");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Ответ с ошибкой должен быть в формате JSON");

        String body = response.body().trim();
        assertTrue(body.startsWith("{") && body.endsWith("}"),
                "Ожидается JSON-объект");

        JsonObject errorResponse = JsonParser.parseString(body).getAsJsonObject();

        assertEquals(0, store.getAllMovies().size(),
                "Фильм не должен добавиться в хранилище");

        assertEquals("Неподдерживаемый тип содержимого", errorResponse.get("error").getAsString(),
                "Поле error должно содержать сообщение о неподдерживаемом типе содержимого");
    }

    @Test
    void getMovieById_whenMovieExists_returnsMovie() throws Exception {
        store.addMovie("Метрополис", 1927);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .GET()
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode(),
                "GET /movies/{id} для существующего фильма должен вернуть 200");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = response.body().trim();
        assertTrue(body.startsWith("{") && body.endsWith("}"),
                "Ожидается JSON-объект");

        JsonObject movie = JsonParser.parseString(body).getAsJsonObject();

        assertEquals(1, movie.get("id").getAsInt(),
                "id фильма должен быть 1");
        assertEquals("Метрополис", movie.get("title").getAsString(),
                "Название фильма должно совпадать");
        assertEquals(1927, movie.get("year").getAsInt(),
                "Год фильма должен совпадать");
    }

    @Test
    void getMovieById_whenMovieDoesNotExist_returnsNotFound() throws Exception {
        store.addMovie("Метрополис", 1927);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .GET()
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, response.statusCode(),
                "GET /movies/{id} для несуществующего фильма должен вернуть 404");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Ответ с ошибкой должен быть в формате JSON");

        String body = response.body().trim();
        assertTrue(body.startsWith("{") && body.endsWith("}"),
                "Ожидается JSON-объект");

        JsonObject errorResponse = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Фильм не найден", errorResponse.get("error").getAsString(),
                "Поле error должно содержать сообщение, что фильм не найден");
    }

    @Test
    void getMovieById_whenIdIsNotNumber_returnsBadRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .GET()
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, response.statusCode(),
                "GET /movies/{id} с некорректным id должен вернуть 400");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Ответ с ошибкой должен быть в формате JSON");

        String body = response.body().trim();
        assertTrue(body.startsWith("{") && body.endsWith("}"),
                "Ожидается JSON-объект");

        JsonObject errorResponse = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Некорректный id фильма", errorResponse.get("error").getAsString(),
                "Поле error должно содержать сообщение о некорректном id");
    }

    @Test
    void deleteMovieById_whenMovieExists_returnsNoContent() throws Exception {
        store.addMovie("Метрополис", 1927);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .DELETE()
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(204, response.statusCode(),
                "DELETE /movies/{id} для существующего фильма должен вернуть 204");

        assertEquals(0, store.getAllMovies().size(),
                "Фильм должен быть удалён из хранилища");
    }

    @Test
    void deleteMovieById_whenMovieDoesNotExist_returnsNotFound() throws Exception {
        store.addMovie("Метрополис", 1927);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .DELETE()
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, response.statusCode(),
                "DELETE /movies/{id} для несуществующего фильма должен вернуть 404");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Ответ с ошибкой должен быть в формате JSON");

        String body = response.body().trim();
        assertTrue(body.startsWith("{") && body.endsWith("}"),
                "Ожидается JSON-объект");

        JsonObject errorResponse = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Фильм не найден", errorResponse.get("error").getAsString(),
                "Поле error должно содержать сообщение, что фильм не найден");

        assertEquals(1, store.getAllMovies().size(),
                "Несуществующий фильм не удалён, существующий фильм должен остаться");
    }

    @Test
    void deleteMovieById_whenIdIsNotNumber_returnsBadRequest() throws Exception {
        store.addMovie("Метрополис", 1927);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .DELETE()
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, response.statusCode(),
                "DELETE /movies/{id} с некорректным id должен вернуть 400");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Ответ с ошибкой должен быть в формате JSON");

        String body = response.body().trim();
        assertTrue(body.startsWith("{") && body.endsWith("}"),
                "Ожидается JSON-объект");

        JsonObject errorResponse = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Некорректный id фильма", errorResponse.get("error").getAsString(),
                "Поле error должно содержать сообщение о некорректном id");

        assertEquals(1, store.getAllMovies().size(),
                "Фильм не должен быть удалён при некорректном id");
    }

    @Test
    void getMovies_whenYearQueryIsValid_returnsMoviesByYear() throws Exception {
        store.addMovie("Хакеры", 1995);
        store.addMovie("Схватка", 1995);
        store.addMovie("Метрополис", 1927);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=1995"))
                .GET()
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode(),
                "GET /movies?year=YYYY должен вернуть 200");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = response.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");

        List<Movie> movies = gson.fromJson(body, new ListOfMoviesTypeToken().getType());

        assertEquals(2, movies.size(),
                "Должны вернуться только фильмы указанного года");

        assertEquals("Хакеры", movies.get(0).getTitle(),
                "Первым должен быть фильм 1995 года");
        assertEquals(1995, movies.get(0).getYear(),
                "Год первого фильма должен быть 1995");

        assertEquals("Схватка", movies.get(1).getTitle(),
                "Вторым должен быть фильм 1995 года");
        assertEquals(1995, movies.get(1).getYear(),
                "Год второго фильма должен быть 1995");
    }

    @Test
    void getMovies_whenYearQueryIsNotNumber_returnsBadRequest() throws Exception {
        store.addMovie("Хакеры", 1995);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=abc"))
                .GET()
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, response.statusCode(),
                "GET /movies?year=abc должен вернуть 400");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Ответ с ошибкой должен быть в формате JSON");

        String body = response.body().trim();
        assertTrue(body.startsWith("{") && body.endsWith("}"),
                "Ожидается JSON-объект");

        JsonObject errorResponse = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Некорректный год", errorResponse.get("error").getAsString(),
                "Поле error должно содержать сообщение о некорректном годе");
    }

    @Test
    void movies_whenMethodIsNotSupported_returnsMethodNotAllowed() throws Exception {
        String requestBody = """
            {
              "title": "Метрополис",
              "year": 1927
            }
            """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .method("PUT", HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(405, response.statusCode(),
                "Неподдерживаемый метод должен вернуть 405");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Ответ с ошибкой должен быть в формате JSON");

        String body = response.body().trim();
        assertTrue(body.startsWith("{") && body.endsWith("}"),
                "Ожидается JSON-объект");

        JsonObject errorResponse = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Метод не поддерживается", errorResponse.get("error").getAsString(),
                "Поле error должно содержать сообщение о неподдерживаемом методе");
    }

    @Test
    void postMovies_whenJsonIsMalformed_returnsBadRequest() throws Exception {
        String requestBody = """
            {
              "title": "Метрополис",
              "year": 1927
            """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, response.statusCode(),
                "POST /movies с некорректным JSON должен вернуть 400");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Ответ с ошибкой должен быть в формате JSON");

        String body = response.body().trim();
        assertTrue(body.startsWith("{") && body.endsWith("}"),
                "Ожидается JSON-объект");

        JsonObject errorResponse = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("Некорректный JSON", errorResponse.get("error").getAsString(),
                "Поле error должно содержать сообщение о некорректном JSON");

        assertEquals(0, store.getAllMovies().size(),
                "Фильм не должен добавиться в хранилище");
    }
}
