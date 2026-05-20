package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";
    private static final int STATUS_NO_CONTENT = 204;

    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        ex.sendResponseHeaders(status, bytes.length);

        OutputStream os = ex.getResponseBody();
        os.write(bytes);
        os.close();
    }

    protected void sendNoContent(HttpExchange ex) throws IOException {
        ex.sendResponseHeaders(STATUS_NO_CONTENT, -1);
        ex.close();
    }
}
