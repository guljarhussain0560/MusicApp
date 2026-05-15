package com.guljar.music.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guljar.music.dto.ImageAnalysisResult;
import com.guljar.music.model.SearchKeyword;
import com.guljar.music.model.User;
import com.guljar.music.repo.SearchKeywordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced image-to-music pipeline using Groq Vision API.
 *
 * Improvements over the original:
 * 1. Proper Groq Vision API format (image_url content type)
 * 2. Image compression to stay under 4MB API limit
 * 3. Structured prompt → mood, genre, energy, scene, artist suggestions, search queries
 * 4. User history personalization — AI sees the user's past preferences
 * 5. Structured JSON response parsing with fallback
 */
@Service
@RequiredArgsConstructor
public class ImageProcessor {

    @Autowired
    private SearchKeywordRepository searchKeywordRepository;

    @Autowired
    private ImageCompressor imageCompressor;

    @Value("${groq.api.key}")
    private String groqApiKey;

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String VISION_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";

    /**
     * Process an uploaded image and return a structured analysis result
     * with mood, genre, energy, search queries, and artist suggestions.
     */
    public ImageAnalysisResult process(MultipartFile image, User user) throws IOException, InterruptedException {
        // Step 1: Compress image to stay under Groq's 4MB base64 limit
        String base64Image = imageCompressor.compressToBase64(image);
        String mimeType = imageCompressor.detectMimeType(image);

        // Step 2: Build user history context for personalization
        String userHistoryContext = buildUserHistoryContext(user);

        // Step 3: Call Groq Vision API with structured prompt
        String aiResponse = callGroqVisionAPI(base64Image, mimeType, userHistoryContext);
        System.out.println("Groq Response:\n" + aiResponse);

        // Step 4: Parse structured response
        ImageAnalysisResult result = parseAnalysisResult(aiResponse);
        System.out.println("Analysis Result - Mood: " + result.getMood()
                + ", Genre: " + result.getGenre()
                + ", Queries: " + result.getSearchQueries());

        // Step 5: Save enriched data to DB
        saveSearchKeyword(user, result);

        return result;
    }

    /**
     * Build context from user's recent search history so the AI
     * can personalize recommendations based on past preferences.
     */
    private String buildUserHistoryContext(User user) {
        List<SearchKeyword> recentSearches = searchKeywordRepository.findTop10ByUserOrderByCreatedAtDesc(user);
        if (recentSearches.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("\n\nUser's recent music taste preferences:\n");
        for (SearchKeyword sk : recentSearches) {
            sb.append("- ");
            if (sk.getMood() != null) sb.append("Mood: ").append(sk.getMood()).append(", ");
            if (sk.getGenre() != null) sb.append("Genre: ").append(sk.getGenre()).append(", ");
            if (sk.getKeyword() != null) sb.append("Keywords: ").append(sk.getKeyword());
            sb.append("\n");
        }
        sb.append("\nConsider these preferences when making suggestions, but prioritize what the image conveys.");
        return sb.toString();
    }

    /**
     * Call Groq Vision API using the proper multimodal content format.
     * Uses content array with text + image_url types instead of embedding
     * the base64 string directly in the text message.
     */
    private String callGroqVisionAPI(String base64Image, String mimeType, String userHistoryContext)
            throws IOException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();

        // System message — detailed role instruction
        String systemPrompt = """
                You are an expert music recommendation AI that analyzes images to suggest the perfect music.
                You understand visual aesthetics, emotions, scenes, lighting, colors, and cultural context,
                and you map them to music moods, genres, and specific artists/songs.
                
                When analyzing an image, you MUST respond with ONLY a valid JSON object (no markdown, no explanation) in this exact format:
                {
                  "mood": "primary emotional mood (e.g., melancholic, euphoric, peaceful, energetic, nostalgic, romantic)",
                  "genre": "best matching music genre (e.g., Lo-fi Hip Hop, Classical, Indie Folk, Jazz, EDM, R&B, Ambient)",
                  "energy": "low or medium or high",
                  "scene": "brief description of what the image depicts (max 10 words)",
                  "artistSuggestions": ["Artist1 - Song1", "Artist2 - Song2", "Artist3 - Song3"],
                  "searchQueries": ["web search query 1", "web search query 2", "web search query 3", "web search query 4", "web search query 5"]
                }
                
                IMPORTANT rules for searchQueries:
                - These are WEB SEARCH queries to find songs on Spotify, Apple Music, YouTube, SoundCloud, JioSaavn, etc.
                - Include artist name + song name for specific suggestions (e.g., "Bon Iver Holocene Spotify")
                - Include mood + genre for discovery queries (e.g., "chill lo-fi songs for rainy day playlist")
                - Mix specific song queries with broader mood/genre discovery queries
                - Each query should find DIFFERENT types of songs (variety)
                - Add terms like "song", "listen", "playlist", or platform names to find actual music pages
                """;

        // Build user content with both text and image
        ArrayNode userContent = mapper.createArrayNode();

        // Text part — the analysis request + user history
        String userPrompt = "Analyze this image and recommend music that perfectly matches its mood, scene, and atmosphere. "
                + "Respond with ONLY the JSON object, no other text."
                + userHistoryContext;

        ObjectNode textPart = mapper.createObjectNode();
        textPart.put("type", "text");
        textPart.put("text", userPrompt);
        userContent.add(textPart);

        // Image part — proper Groq Vision format using data URI
        ObjectNode imagePart = mapper.createObjectNode();
        imagePart.put("type", "image_url");
        ObjectNode imageUrl = mapper.createObjectNode();
        imageUrl.put("url", "data:" + mimeType + ";base64," + base64Image);
        imagePart.set("image_url", imageUrl);
        userContent.add(imagePart);

        // Build messages array
        ArrayNode messages = mapper.createArrayNode();

        ObjectNode systemMessage = mapper.createObjectNode();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        messages.add(systemMessage);

        ObjectNode userMessage = mapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.set("content", userContent);
        messages.add(userMessage);

        // Build request body
        ObjectNode requestJson = mapper.createObjectNode();
        requestJson.put("model", VISION_MODEL);
        requestJson.set("messages", messages);
        requestJson.put("temperature", 0.7);
        requestJson.put("max_tokens", 1024);

        String requestBody = mapper.writeValueAsString(requestJson);

        // Make HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_API_URL))
                .header("Authorization", "Bearer " + groqApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }

    /**
     * Parse the structured JSON response from Groq into an ImageAnalysisResult.
     * Falls back to basic keyword extraction if JSON parsing fails.
     */
    private ImageAnalysisResult parseAnalysisResult(String groqResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(groqResponse);
            JsonNode choices = root.path("choices");

            if (choices.isArray() && choices.size() > 0) {
                String content = choices.get(0).path("message").path("content").asText();

                // Clean up content — remove markdown code fences if present
                content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

                // Try parsing as structured JSON
                try {
                    JsonNode analysisJson = mapper.readTree(content);
                    ImageAnalysisResult result = new ImageAnalysisResult();

                    result.setMood(analysisJson.path("mood").asText("unknown"));
                    result.setGenre(analysisJson.path("genre").asText("unknown"));
                    result.setEnergy(analysisJson.path("energy").asText("medium"));
                    result.setScene(analysisJson.path("scene").asText(""));

                    // Parse artist suggestions
                    List<String> artists = new ArrayList<>();
                    JsonNode artistNode = analysisJson.path("artistSuggestions");
                    if (artistNode.isArray()) {
                        for (JsonNode a : artistNode) {
                            artists.add(a.asText());
                        }
                    }
                    result.setArtistSuggestions(artists);

                    // Parse search queries
                    List<String> queries = new ArrayList<>();
                    JsonNode queryNode = analysisJson.path("searchQueries");
                    if (queryNode.isArray()) {
                        for (JsonNode q : queryNode) {
                            queries.add(q.asText());
                        }
                    }
                    result.setSearchQueries(queries);

                    // Build raw keywords for backward compatibility
                    result.setRawKeywords(result.getMood() + ", " + result.getGenre() + ", " + result.getScene());

                    return result;
                } catch (Exception jsonParseError) {
                    System.out.println("Could not parse structured JSON, falling back to keyword extraction");
                    return buildFallbackResult(content);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return buildFallbackResult("unknown");
    }

    /**
     * Fallback: if the AI doesn't return valid JSON, extract whatever keywords
     * we can and build basic search queries from them.
     */
    private ImageAnalysisResult buildFallbackResult(String rawContent) {
        String cleaned = rawContent.replaceAll("[^a-zA-Z0-9, ]", "").trim();

        ImageAnalysisResult result = new ImageAnalysisResult();
        result.setMood("unknown");
        result.setGenre("unknown");
        result.setEnergy("medium");
        result.setScene("");
        result.setArtistSuggestions(new ArrayList<>());
        result.setRawKeywords(cleaned);

        // Build search queries from raw keywords
        List<String> queries = new ArrayList<>();
        String[] parts = cleaned.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                queries.add(trimmed + " music");
            }
        }
        if (queries.isEmpty()) {
            queries.add("popular music");
        }
        result.setSearchQueries(queries);

        return result;
    }

    /**
     * Save the enriched analysis result to the database for future personalization.
     */
    private void saveSearchKeyword(User user, ImageAnalysisResult result) {
        SearchKeyword searchKeyword = new SearchKeyword();
        searchKeyword.setKeyword(result.getRawKeywords());
        searchKeyword.setMood(result.getMood());
        searchKeyword.setGenre(result.getGenre());
        searchKeyword.setEnergy(result.getEnergy());
        searchKeyword.setScene(result.getScene());
        searchKeyword.setUser(user);
        searchKeywordRepository.save(searchKeyword);
    }
}

