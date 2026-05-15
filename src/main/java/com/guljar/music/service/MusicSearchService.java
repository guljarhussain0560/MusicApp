package com.guljar.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guljar.music.dto.ImageAnalysisResult;
import com.guljar.music.dto.Song;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Web-based music search service using Google Custom Search API.
 * Finds music from across the web — Spotify, YouTube, Apple Music,
 * SoundCloud, JioSaavn, Gaana, and other music platforms.
 *
 * Replaces the old YouTube-only approach with a multi-platform search.
 */
@Service
public class MusicSearchService {

    @Value("${google.api.key}")
    private String googleApiKey;

    @Value("${google.cse.id}")
    private String googleCseId;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Maximum results to return to the frontend */
    private static final int MAX_RESULTS = 20;

    /** Google CSE results per query (max 10 per API call) */
    private static final int RESULTS_PER_QUERY = 8;

    /** Google Custom Search JSON API endpoint */
    private static final String GOOGLE_CSE_URL = "https://customsearch.googleapis.com/customsearch/v1";

    /** Known music platform domains → platform names */
    private static final Map<String, String> PLATFORM_MAP = new LinkedHashMap<>();
    static {
        PLATFORM_MAP.put("open.spotify.com", "Spotify");
        PLATFORM_MAP.put("spotify.com", "Spotify");
        PLATFORM_MAP.put("music.youtube.com", "YouTube Music");
        PLATFORM_MAP.put("youtube.com", "YouTube");
        PLATFORM_MAP.put("youtu.be", "YouTube");
        PLATFORM_MAP.put("music.apple.com", "Apple Music");
        PLATFORM_MAP.put("soundcloud.com", "SoundCloud");
        PLATFORM_MAP.put("jiosaavn.com", "JioSaavn");
        PLATFORM_MAP.put("saavn.com", "JioSaavn");
        PLATFORM_MAP.put("gaana.com", "Gaana");
        PLATFORM_MAP.put("wynk.in", "Wynk Music");
        PLATFORM_MAP.put("deezer.com", "Deezer");
        PLATFORM_MAP.put("tidal.com", "Tidal");
        PLATFORM_MAP.put("amazon.com/music", "Amazon Music");
        PLATFORM_MAP.put("music.amazon", "Amazon Music");
        PLATFORM_MAP.put("pandora.com", "Pandora");
        PLATFORM_MAP.put("bandcamp.com", "Bandcamp");
    }

    /**
     * Search for songs across the web using the structured AI analysis result.
     * Queries Google Custom Search, identifies music platforms from results,
     * deduplicates, scores by relevance, and returns the top results.
     */
    public List<Song> searchSongs(ImageAnalysisResult analysis) {
        Map<String, Song> uniqueSongs = new LinkedHashMap<>();

        List<String> allQueries = buildSearchQueries(analysis);

        for (int i = 0; i < allQueries.size(); i++) {
            String query = allQueries.get(i);
            double baseScore = 1.0 - (i * 0.08);
            baseScore = Math.max(baseScore, 0.3);

            try {
                List<Song> results = executeWebSearch(query, RESULTS_PER_QUERY);

                for (int j = 0; j < results.size(); j++) {
                    Song song = results.get(j);
                    String dedupeKey = normalizeUrl(song.getUrl());

                    if (dedupeKey != null && !uniqueSongs.containsKey(dedupeKey)) {
                        double positionBonus = 1.0 - (j * 0.04);
                        double relevanceScore = baseScore * positionBonus;

                        // Boost for recognized music platforms
                        relevanceScore = applyPlatformBoost(song, relevanceScore);

                        // Boost if title/artist matches mood or genre keywords
                        relevanceScore = applyKeywordBoost(song, analysis, relevanceScore);

                        song.setRelevanceScore(Math.min(relevanceScore, 1.0));
                        song.setMatchedQuery(query);
                        if (analysis.getGenre() != null && !"unknown".equals(analysis.getGenre())) {
                            song.setGenre(analysis.getGenre());
                        }
                        uniqueSongs.put(dedupeKey, song);
                    }
                }
            } catch (Exception e) {
                System.out.println("Web search failed for query: " + query + " — " + e.getMessage());
            }
        }

        return uniqueSongs.values().stream()
                .sorted(Comparator.comparingDouble(Song::getRelevanceScore).reversed())
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());
    }

    /**
     * Backward-compatible method — wraps raw keywords into an ImageAnalysisResult.
     */
    public List<Song> searchSongs(String keywordsCommaSeparated) {
        ImageAnalysisResult fallback = new ImageAnalysisResult();
        fallback.setRawKeywords(keywordsCommaSeparated);
        fallback.setMood("unknown");
        fallback.setGenre("unknown");
        fallback.setEnergy("medium");
        fallback.setScene("");
        fallback.setArtistSuggestions(new ArrayList<>());

        List<String> queries = new ArrayList<>();
        for (String kw : keywordsCommaSeparated.split(",")) {
            String trimmed = kw.trim();
            if (!trimmed.isEmpty()) {
                queries.add(trimmed + " song listen online");
            }
        }
        fallback.setSearchQueries(queries);

        return searchSongs(fallback);
    }

    /**
     * Build search queries optimized for finding music across the web.
     * Appends music-platform-friendly terms to boost relevant results.
     */
    private List<String> buildSearchQueries(ImageAnalysisResult analysis) {
        List<String> queries = new ArrayList<>();

        // 1. AI-generated search queries (add "song" to make them music-specific)
        if (analysis.getSearchQueries() != null) {
            for (String q : analysis.getSearchQueries()) {
                queries.add(q);
            }
        }

        // 2. Artist/song-specific queries — search for actual tracks
        if (analysis.getArtistSuggestions() != null) {
            for (String artist : analysis.getArtistSuggestions()) {
                queries.add(artist + " song listen");
            }
        }

        // 3. Mood + genre combination for playlist discovery
        if (analysis.getMood() != null && analysis.getGenre() != null
                && !"unknown".equals(analysis.getMood())) {
            queries.add(analysis.getMood() + " " + analysis.getGenre() + " songs playlist");
        }

        // 4. Fallback to raw keywords
        if (queries.isEmpty() && analysis.getRawKeywords() != null) {
            for (String kw : analysis.getRawKeywords().split(",")) {
                String trimmed = kw.trim();
                if (!trimmed.isEmpty()) {
                    queries.add(trimmed + " songs");
                }
            }
        }

        if (queries.isEmpty()) {
            queries.add("best songs to listen right now");
        }

        return queries;
    }

    /**
     * Execute a Google Custom Search API query and parse results into Song objects.
     * Automatically detects the music platform from the result URL and extracts
     * artist/title metadata from the search result title.
     */
    private List<Song> executeWebSearch(String query, int maxResults) {
        List<Song> songs = new ArrayList<>();

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = GOOGLE_CSE_URL
                    + "?key=" + googleApiKey
                    + "&cx=" + googleCseId
                    + "&q=" + encodedQuery
                    + "&num=" + Math.min(maxResults, 10);

            System.out.println("[DEBUG] ===== Web Search =====");
            System.out.println("[DEBUG] Query: " + query);
            System.out.println("[DEBUG] Full URL: " + url);

            String json = restTemplate.getForObject(url, String.class);

            System.out.println("[DEBUG] Response length: " + (json != null ? json.length() : "null"));
            // Print first 500 chars of response to see errors
            if (json != null) {
                System.out.println("[DEBUG] Response preview: " + json.substring(0, Math.min(json.length(), 500)));
            }

            JsonNode root = mapper.readTree(json);
            JsonNode items = root.path("items");

            if (!items.isArray()) {
                System.out.println("[DEBUG] No 'items' array found in response. Error? "
                        + root.path("error").path("message").asText("none"));
                return songs;
            }

            System.out.println("[DEBUG] Found " + items.size() + " results");

            for (JsonNode item : items) {
                String resultUrl = item.path("link").asText("");
                String resultTitle = item.path("title").asText("");
                String snippet = item.path("snippet").asText("");

                String platform = detectPlatform(resultUrl);
                String[] parsed = parseArtistAndTitle(resultTitle, platform);
                String artist = parsed[0];
                String title = parsed[1];
                String coverImage = extractThumbnail(item);

                System.out.println("[DEBUG]   → " + platform + " | " + title + " by " + artist);

                Song song = new Song();
                song.setTitle(title);
                song.setArtist(artist);
                song.setAlbum("Unknown Album");
                song.setUrl(resultUrl);
                song.setCoverImage(coverImage);
                song.setGenre("Unknown Genre");
                song.setPlatform(platform);
                song.setDescription(snippet);

                songs.add(song);
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.out.println("[DEBUG] ❌ HTTP ERROR: " + e.getStatusCode());
            System.out.println("[DEBUG] ❌ Response Body: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.out.println(
                    "[DEBUG] ❌ Google Custom Search error: " + e.getClass().getSimpleName() + " — " + e.getMessage());
            e.printStackTrace();
        }

        return songs;
    }

    /**
     * Detect which music platform a URL belongs to.
     */
    private String detectPlatform(String url) {
        if (url == null || url.isEmpty())
            return "Web";

        String urlLower = url.toLowerCase();
        for (Map.Entry<String, String> entry : PLATFORM_MAP.entrySet()) {
            if (urlLower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "Web";
    }

    /**
     * Parse artist name and song title from a search result title.
     * Common patterns:
     * - "Artist - Song Title | Spotify"
     * - "Artist - Song Title - YouTube"
     * - "Song Title by Artist on Apple Music"
     * - "Listen to Song Title by Artist on SoundCloud"
     */
    private String[] parseArtistAndTitle(String rawTitle, String platform) {
        if (rawTitle == null || rawTitle.isEmpty()) {
            return new String[] { "Unknown Artist", "Unknown Title" };
        }

        // Remove common platform suffixes
        String cleaned = rawTitle
                .replaceAll(
                        "\\s*[-|]\\s*(Spotify|YouTube|Apple Music|SoundCloud|JioSaavn|Gaana|Wynk|Deezer|Tidal|Bandcamp).*$",
                        "")
                .replaceAll("\\s*-\\s*YouTube$", "")
                .replaceAll("\\s*\\|.*$", "")
                .trim();

        // Try "Artist - Title" pattern (most common)
        if (cleaned.contains(" - ")) {
            String[] parts = cleaned.split("\\s*-\\s*", 2);
            return new String[] { parts[0].trim(), parts[1].trim() };
        }

        // Try "Title by Artist" pattern (Apple Music, SoundCloud)
        if (cleaned.toLowerCase().contains(" by ")) {
            String[] parts = cleaned.split("\\s+by\\s+", 2);
            return new String[] { parts[1].trim(), parts[0].trim() };
        }

        // Try "Listen to X by Y" pattern
        if (cleaned.toLowerCase().startsWith("listen to ")) {
            String rest = cleaned.substring("listen to ".length());
            if (rest.toLowerCase().contains(" by ")) {
                String[] parts = rest.split("\\s+by\\s+", 2);
                return new String[] { parts[1].trim(), parts[0].trim() };
            }
        }

        // Fallback — use the whole title as the song title
        return new String[] { "Unknown Artist", cleaned };
    }

    /**
     * Extract a thumbnail URL from the Google Custom Search result metadata.
     * Looks in pagemap.cse_thumbnail, pagemap.metatags og:image, etc.
     */
    private String extractThumbnail(JsonNode item) {
        try {
            // Try CSE thumbnail first
            JsonNode cseThumbnail = item.path("pagemap").path("cse_thumbnail");
            if (cseThumbnail.isArray() && cseThumbnail.size() > 0) {
                String src = cseThumbnail.get(0).path("src").asText("");
                if (!src.isEmpty())
                    return src;
            }

            // Try cse_image
            JsonNode cseImage = item.path("pagemap").path("cse_image");
            if (cseImage.isArray() && cseImage.size() > 0) {
                String src = cseImage.get(0).path("src").asText("");
                if (!src.isEmpty())
                    return src;
            }

            // Try metatags og:image
            JsonNode metatags = item.path("pagemap").path("metatags");
            if (metatags.isArray() && metatags.size() > 0) {
                String ogImage = metatags.get(0).path("og:image").asText("");
                if (!ogImage.isEmpty())
                    return ogImage;
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    /**
     * Boost relevance score for results from recognized music platforms.
     * Spotify and Apple Music get higher boosts since they're dedicated music
     * services.
     */
    private double applyPlatformBoost(Song song, double currentScore) {
        String platform = song.getPlatform();
        if (platform == null)
            return currentScore;

        return switch (platform) {
            case "Spotify" -> currentScore + 0.15;
            case "Apple Music" -> currentScore + 0.12;
            case "YouTube Music" -> currentScore + 0.10;
            case "JioSaavn", "Gaana" -> currentScore + 0.10;
            case "SoundCloud", "Deezer", "Tidal" -> currentScore + 0.08;
            case "YouTube" -> currentScore + 0.05;
            case "Bandcamp", "Wynk Music" -> currentScore + 0.05;
            default -> currentScore;
        };
    }

    /**
     * Boost the relevance score if the song title or artist contains
     * keywords from the detected mood, genre, or scene.
     */
    private double applyKeywordBoost(Song song, ImageAnalysisResult analysis, double currentScore) {
        String titleLower = (song.getTitle() != null ? song.getTitle() : "").toLowerCase();
        String artistLower = (song.getArtist() != null ? song.getArtist() : "").toLowerCase();
        String combined = titleLower + " " + artistLower;

        double boost = 0.0;

        if (analysis.getMood() != null && combined.contains(analysis.getMood().toLowerCase())) {
            boost += 0.08;
        }

        if (analysis.getGenre() != null && combined.contains(analysis.getGenre().toLowerCase())) {
            boost += 0.08;
        }

        if (analysis.getArtistSuggestions() != null) {
            for (String artist : analysis.getArtistSuggestions()) {
                String artistName = artist.split("-")[0].trim().toLowerCase();
                if (!artistName.isEmpty() && combined.contains(artistName)) {
                    boost += 0.12;
                    break;
                }
            }
        }

        return currentScore + boost;
    }

    /**
     * Normalize a URL for deduplication — strips protocol, trailing slashes,
     * and query parameters to detect the same page from different search results.
     */
    private String normalizeUrl(String url) {
        if (url == null || url.isEmpty())
            return null;
        return url.toLowerCase()
                .replaceAll("^https?://", "")
                .replaceAll("^www\\.", "")
                .replaceAll("[?#].*$", "")
                .replaceAll("/+$", "");
    }
}
