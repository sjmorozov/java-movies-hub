package ru.practicum.moviehub.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {

    private static final int PORT = 8080;
    private static final String BASE = "http://localhost:" + PORT;
    private static MoviesServer server;
    private static HttpClient client;
    private static MoviesStore store;

    @BeforeAll
    static void beforeAll() {
        store = new MoviesStore();
        server = new MoviesServer(store, PORT);
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
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

        JsonArray moviesArray = JsonParser.parseString(body).getAsJsonArray();
        assertEquals(2, moviesArray.size(), "Количество фильмов должно быть 2");

        JsonObject firstMovie = moviesArray.get(0).getAsJsonObject();
        assertEquals(1, firstMovie.get("id").getAsInt());
        assertEquals("Прибытие поезда", firstMovie.get("title").getAsString(), "Название первого фильма должно быть Прибытие поезда");
        assertEquals(1896, firstMovie.get("year").getAsInt(), "Год первого фильма должен быть 1896");

        JsonObject secondMovie = moviesArray.get(1).getAsJsonObject();
        assertEquals(2, secondMovie.get("id").getAsInt());
        assertEquals("Хакеры", secondMovie.get("title").getAsString(), "Название первого фильма должно быть Хакеры");
        assertEquals(1995, secondMovie.get("year").getAsInt(), "Год второго фильма должен быть 1995");
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
}
