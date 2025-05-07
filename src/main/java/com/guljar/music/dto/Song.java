package com.guljar.music.dto;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter

public class Song {

    private String title;
    private String artist;
    private String album;
    private String url;
    private String coverImage;
    private String genre;

    // Constructors, getters, and setters
    public Song(String title, String artist, String album, String url, String coverImage, String genre) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.url = url;
        this.coverImage = coverImage;
        this.genre = genre;
    }
}
