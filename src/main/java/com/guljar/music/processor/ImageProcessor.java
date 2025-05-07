package com.guljar.music.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guljar.music.model.SearchKeyword;
import com.guljar.music.model.User;
import com.guljar.music.repo.SearchKeywordRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class ImageProcessor {

    @Autowired
    private SearchKeywordRepository searchKeywordRepository;

    // 🔐 Replace with your actual API key or inject it via @Value if stored in properties
    @Value("${groq.api.key}")
    private String groqApiKey;



    public String process(MultipartFile image, User user) throws IOException, InterruptedException {
        // ✅ Convert image to base64
        byte[] imageBytes = IOUtils.toByteArray(image.getInputStream());
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        // ✅ Construct request body as JSON
        ObjectMapper mapper = new ObjectMapper();

        ArrayNode messages = mapper.createArrayNode();
        messages.add(mapper.createObjectNode()
                .put("role", "system")
                .put("content", "You are an image analysis assistant that gives relevant music keywords."));
        messages.add(mapper.createObjectNode()
                .put("role", "user")
                .put("content", "Given the base64 image, generate 3 relevant music keywords (mood/situation). Comma-separated only.(return only keyword) Here is the base64 image: " + base64Image));

        ObjectNode requestJson = mapper.createObjectNode();
        requestJson.put("model", "meta-llama/llama-4-scout-17b-16e-instruct");
        requestJson.set("messages", messages);

        String requestBody = mapper.writeValueAsString(requestJson);

        // ✅ Make HTTP POST request to Groq
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                .header("Authorization", "Bearer " + groqApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Groq Response:\n" + response.body());

        // ✅ Extract keyword
        String keyword = extractKeywordFromJson(response.body());
        System.out.println("Keyword: " + keyword);

        // ✅ Save to DB
        SearchKeyword searchKeyword = new SearchKeyword();
        searchKeyword.setKeyword(keyword);
        searchKeyword.setUser(user);
        searchKeywordRepository.save(searchKeyword);

        return keyword;
    }

    private String extractKeywordFromJson(String json) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(json);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode contentNode = choices.get(0).path("message").path("content");
                if (contentNode != null && !contentNode.asText().isBlank()) {
                    return contentNode.asText().replaceAll("[^a-zA-Z0-9, ]", "").trim();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "unknown";
    }
}
