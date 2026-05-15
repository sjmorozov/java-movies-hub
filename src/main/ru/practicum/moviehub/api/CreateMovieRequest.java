package ru.practicum.moviehub.api;

public class CreateMovieRequest {
    private String title;
    private Integer year;

    public CreateMovieRequest() {
    }

    public String getTitle() {
        return title;
    }

    public Integer getYear() {
        return year;
    }
}
