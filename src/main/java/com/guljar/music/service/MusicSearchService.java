package com.guljar.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guljar.music.dto.Song;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class MusicSearchService {


    @Value("${youtube.api.key}")
    private String youtubeApiKey;

    private final RestTemplate restTemplate = new RestTemplate();



    public List<Song> searchSongs(String keywordsCommaSeparated) {
        List<Song> allSongs = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        try {
            String[] keywords = keywordsCommaSeparated.split(",");
            for (String rawKeyword : keywords) {
                String keyword = rawKeyword.trim();
                String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
                String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=10&videoDuration=medium&videoCategoryId=10&q="
                        + encodedKeyword + "&key=" + youtubeApiKey;
                String json = restTemplate.getForObject(url, String.class);

                JsonNode root = mapper.readTree(json);
                JsonNode items = root.path("items");

                for (JsonNode item : items) {
                    JsonNode snippet = item.path("snippet");
                    String videoId = item.path("id").path("videoId").asText();
                    String title = snippet.path("title").asText();
                    String channelTitle = snippet.path("channelTitle").asText();
                    String thumbnailUrl = snippet.path("thumbnails").path("high").path("url").asText();

                    Song song = new Song(
                            title,
                            channelTitle,
                            "Unknown Album", // YouTube doesn't provide album info
                            "https://www.youtube.com/watch?v=" + videoId,
                            thumbnailUrl,
                            "Unknown Genre"
                    );

                    allSongs.add(song);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return allSongs;
    }

}
