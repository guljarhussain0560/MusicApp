package com.guljar.music.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@NoArgsConstructor
public class Song {

    private String title;
    private String artist;
    private String album;
    private String url;
    private String coverImage;
    private String genre;

    /** Source platform: "Spotify", "YouTube", "Apple Music", "SoundCloud", "JioSaavn", "Gaana", "Web" */
    private String platform;

    /** Description/snippet from web search result */
    private String description;

    /** Relevance score from 0.0 to 1.0 — how closely this song matches the image analysis */
    private double relevanceScore;

    /** Which AI-generated search query found this song */
    private String matchedQuery;

    public Song(String title, String artist, String album, String url, String coverImage, String genre) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.url = url;
        this.coverImage = coverImage;
        this.genre = genre;
        this.relevanceScore = 0.0;
        this.platform = "Web";
    }
}
