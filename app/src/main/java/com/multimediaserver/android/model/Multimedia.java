package com.multimediaserver.android.model;

/** FullMultimediaDTO. A movie video has no season or episode. */
public class Multimedia {
    public long id;
    public long movieSeriesId;
    public String multimediaPath;
    public String title;
    public Integer season;
    public Integer episodeNumber;
    public Double durationSeconds;
}
