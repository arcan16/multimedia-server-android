package com.multimediaserver.android.model;

import java.util.ArrayList;
import java.util.List;

/** FullMovieSeriesDTO. */
public class MovieSeries {
    public static final String MOVIE = "MOVIE";
    public static final String SERIE = "SERIE";

    public long id;
    public String name;
    public String synopsis;
    public String posterPath;
    public Integer releaseYear;
    public String classification;
    public String type;
    public List<Long> genreIds = new ArrayList<>();

    public boolean isSeries() {
        return SERIE.equals(type);
    }

    public List<Long> genres() {
        return genreIds != null ? genreIds : new ArrayList<>();
    }
}
