package com.smartlibrary.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlibrary.service.SystemSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class LlmClientService {

    private static final Logger log = LoggerFactory.getLogger(LlmClientService.class);

    @Value("${app.ai.gemini.api-key:}")
    private String configuredApiKey;

    @Value("${app.ai.gemini.model:gemini-1.5-flash}")
    private String defaultModel;

    private final SystemSettingService systemSettingService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LlmClientService(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String getEffectiveApiKey() {
        String dbKey = systemSettingService.getSetting("gemini_api_key", "");
        if (dbKey != null && !dbKey.isBlank()) {
            return dbKey.trim();
        }
        if (configuredApiKey != null && !configuredApiKey.isBlank()) {
            return configuredApiKey.trim();
        }
        String envKey = System.getenv("GEMINI_API_KEY");
        return (envKey != null && !envKey.isBlank()) ? envKey.trim() : "";
    }

    public String getEffectiveModel() {
        String dbModel = systemSettingService.getSetting("gemini_model", "");
        if (dbModel != null && !dbModel.isBlank()) {
            return dbModel.trim();
        }
        return defaultModel;
    }

    public boolean isConfigured() {
        return !getEffectiveApiKey().isBlank();
    }

    public LlmResult generateAnswer(String systemPrompt, String userMessage) {
        String apiKey = getEffectiveApiKey();
        if (apiKey.isBlank()) {
            return new LlmResult(
                generateLocalFallbackAnswer(userMessage, systemPrompt),
                false,
                true,
                "Local Knowledge-Base Search (No GEMINI_API_KEY Configured)"
            );
        }

        try {
            String model = getEffectiveModel();
            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

            Map<String, Object> systemPart = Map.of("text", systemPrompt);
            Map<String, Object> systemInstruction = Map.of("parts", List.of(systemPart));

            Map<String, Object> userPart = Map.of("text", userMessage);
            Map<String, Object> userContent = Map.of("role", "user", "parts", List.of(userPart));

            Map<String, Object> requestPayload = new LinkedHashMap<>();
            requestPayload.put("system_instruction", systemInstruction);
            requestPayload.put("contents", List.of(userContent));
            requestPayload.put("generationConfig", Map.of(
                    "temperature", 0.2,
                    "maxOutputTokens", 800
            ));

            String requestJson = objectMapper.writeValueAsString(requestPayload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && candidates.size() > 0) {
                    JsonNode textNode = candidates.get(0).path("content").path("parts").get(0).path("text");
                    if (!textNode.isMissingNode()) {
                        return new LlmResult(textNode.asText().trim(), true, false, "Gemini (" + model + ") Online");
                    }
                }
            }

            log.warn("Gemini API call non-200 or unexpected response: status={}, body={}", response.statusCode(), response.body());
            return new LlmResult(
                generateLocalFallbackAnswer(userMessage, systemPrompt) + "\n\n*(Note: Cloud LLM response returned HTTP " + response.statusCode() + "; library fallback answer provided)*",
                false,
                true,
                "Fallback Mode (Gemini API returned " + response.statusCode() + ")"
            );
        } catch (Exception e) {
            log.error("Exception calling Gemini LLM: {}", e.getMessage());
            return new LlmResult(
                generateLocalFallbackAnswer(userMessage, systemPrompt) + "\n\n*(Note: Cloud LLM service unreachable; library fallback answer provided)*",
                false,
                true,
                "Fallback Mode (Connection Error: " + e.getMessage() + ")"
            );
        }
    }

    private String generateLocalFallbackAnswer(String query, String systemPrompt) {
        if (systemPrompt != null && systemPrompt.contains("- Book: \"")) {
            StringBuilder sb = new StringBuilder();
            sb.append("Here are verified library catalogue titles matching your inquiry:\n\n");

            String[] lines = systemPrompt.split("\n");
            String currentTitle = null;
            String currentAuthor = null;
            String currentStock = null;
            String currentShelf = null;

            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.startsWith("- Book: \"")) {
                    if (currentTitle != null) {
                        appendBookSummary(sb, currentTitle, currentAuthor, currentStock, currentShelf);
                    }
                    int start = line.indexOf("\"") + 1;
                    int end = line.lastIndexOf("\"");
                    currentTitle = (start > 0 && end > start) ? line.substring(start, end) : line;
                    currentAuthor = null;
                    currentStock = null;
                    currentShelf = null;
                } else if (line.startsWith("Author: ")) {
                    currentAuthor = line.substring(8).trim();
                } else if (line.startsWith("Real-time Stock: ")) {
                    currentStock = line.substring(17).trim();
                } else if (line.startsWith("Shelf Location: ")) {
                    currentShelf = line.substring(16).trim();
                }
            }
            if (currentTitle != null) {
                appendBookSummary(sb, currentTitle, currentAuthor, currentStock, currentShelf);
            }

            sb.append("\nYou can view the full record, check shelf locations, or request a loan directly from your student portal.");
            return sb.toString();
        }

        return "No specific books or documents directly matched your query in the library catalogue.\n\n" +
               "You can browse the complete collection in the Catalogue tab or try searching by author, topic, or ISBN.";
    }

    private void appendBookSummary(StringBuilder sb, String title, String author, String stock, String shelf) {
        sb.append("• **").append(title).append("**");
        if (author != null && !author.isBlank()) {
            sb.append(" by ").append(author);
        }
        if (stock != null && !stock.isBlank()) {
            sb.append(" — ").append(stock);
        }
        if (shelf != null && !shelf.isBlank()) {
            sb.append(" [Location: ").append(shelf).append("]");
        }
        sb.append("\n");
    }

    public static class LlmResult {
        private final String text;
        private final boolean success;
        private final boolean fallback;
        private final String providerStatus;

        public LlmResult(String text, boolean success, boolean fallback, String providerStatus) {
            this.text = text;
            this.success = success;
            this.fallback = fallback;
            this.providerStatus = providerStatus;
        }

        public String getText() { return text; }
        public boolean isSuccess() { return success; }
        public boolean isFallback() { return fallback; }
        public String getProviderStatus() { return providerStatus; }
    }
}
