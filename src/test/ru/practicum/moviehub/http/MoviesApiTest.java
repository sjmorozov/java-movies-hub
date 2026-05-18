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
}
