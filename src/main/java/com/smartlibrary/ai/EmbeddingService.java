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
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int LOCAL_VECTOR_DIM = 64;

    @Value("${app.ai.gemini.api-key:}")
    private String configuredApiKey;

    @Value("${app.ai.embedding.model:text-embedding-004}")
    private String embeddingModel;

    private final SystemSettingService systemSettingService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public EmbeddingService(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String getEffectiveApiKey() {
        // First check database setting, fallback to application.properties / ENV
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

    public boolean isApiAvailable() {
        return !getEffectiveApiKey().isBlank();
    }

    /**
     * Generates embedding vector for a given text.
     * Uses Gemini Embedding API if API key is present; otherwise falls back to deterministic local semantic hash vector.
     */
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[LOCAL_VECTOR_DIM];
        }

        String apiKey = getEffectiveApiKey();
        if (!apiKey.isBlank()) {
            try {
                return fetchGeminiEmbedding(text, apiKey);
            } catch (Exception e) {
                log.warn("Gemini embedding API call failed: {}. Falling back to local semantic vectorizer.", e.getMessage());
            }
        }

        return generateLocalSemanticVector(text);
    }

    private float[] fetchGeminiEmbedding(String text, String apiKey) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + embeddingModel + ":embedContent?key=" + apiKey;

        Map<String, Object> part = Map.of("text", text);
        Map<String, Object> content = Map.of("parts", List.of(part));
        Map<String, Object> requestBody = Map.of("content", content);

        String json = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode values = root.path("embedding").path("values");
            if (values.isArray() && values.size() > 0) {
                float[] vector = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    vector[i] = (float) values.get(i).asDouble();
                }
                return normalizeVector(vector);
            }
        }
        throw new RuntimeException("Embedding API returned HTTP " + response.statusCode() + ": " + response.body());
    }

    /**
     * Deterministic local term-frequency hashing vectorizer for offline or non-API setups.
     * Ensures consistent cosine similarity calculations without any external runtime or GPU.
     */
    public float[] generateLocalSemanticVector(String text) {
        float[] vector = new float[LOCAL_VECTOR_DIM];
        String[] tokens = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");

        for (String token : tokens) {
            if (token.isBlank() || isStopWord(token)) continue;
            int hash = Math.abs(token.hashCode());
            int idx = hash % LOCAL_VECTOR_DIM;
            vector[idx] += 1.0f;
            // Also assign a 2-gram / prefix feature to neighboring cell
            if (token.length() > 3) {
                int prefixHash = Math.abs(token.substring(0, 3).hashCode());
                vector[prefixHash % LOCAL_VECTOR_DIM] += 0.5f;
            }
        }

        return normalizeVector(vector);
    }

    public double calculateCosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length == 0 || v2.length == 0) return 0.0;
        int length = Math.min(v1.length, v2.length);
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private float[] normalizeVector(float[] vector) {
        double norm = 0.0;
        for (float val : vector) {
            norm += val * val;
        }
        if (norm > 0) {
            float len = (float) Math.sqrt(norm);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= len;
            }
        }
        return vector;
    }

    public String vectorToJson(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception e) {
            return "[]";
        }
    }

    public float[] jsonToVector(String json) {
        if (json == null || json.isBlank()) return new float[0];
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (Exception e) {
            return new float[0];
        }
    }

    private boolean isStopWord(String word) {
        return STOP_WORDS.contains(word);
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't",
            "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by",
            "can", "could", "did", "do", "does", "doing", "down", "during", "each", "few", "for", "from",
            "further", "had", "has", "have", "having", "he", "her", "here", "hers", "herself", "him", "himself",
            "his", "how", "i", "if", "in", "into", "is", "it", "its", "itself", "just", "me", "more", "most",
            "my", "myself", "no", "nor", "not", "now", "of", "off", "on", "once", "only", "or", "other", "our",
            "ours", "ourselves", "out", "over", "own", "same", "she", "should", "so", "some", "such", "than",
            "that", "the", "their", "theirs", "them", "themselves", "then", "there", "these", "they", "this",
            "those", "through", "to", "too", "under", "until", "up", "very", "was", "we", "were", "what", "when",
            "where", "which", "while", "who", "whom", "why", "with", "would", "you", "your", "yours"
    );
}
