package com.smartlibrary.controller;

import com.smartlibrary.ai.AiAssistantService;
import com.smartlibrary.ai.LlmClientService;
import com.smartlibrary.dto.AiChatRequestDto;
import com.smartlibrary.dto.AiChatResponseDto;
import com.smartlibrary.dto.SemanticSearchResultDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiApiController {

    private final AiAssistantService aiAssistantService;
    private final LlmClientService llmClientService;

    public AiApiController(AiAssistantService aiAssistantService, LlmClientService llmClientService) {
        this.aiAssistantService = aiAssistantService;
        this.llmClientService = llmClientService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponseDto> chat(@Valid @RequestBody AiChatRequestDto request) {
        AiChatResponseDto response = aiAssistantService.processChatQuery(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/semantic-search")
    public ResponseEntity<List<SemanticSearchResultDto>> semanticSearch(
            @RequestParam("query") String query,
            @RequestParam(value = "topK", defaultValue = "8") int topK) {
        List<SemanticSearchResultDto> results = aiAssistantService.performSemanticSearch(query, topK);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAiStatus() {
        Map<String, Object> status = new HashMap<>();
        boolean isConfigured = llmClientService.isConfigured();
        status.put("configured", isConfigured);
        status.put("model", llmClientService.getEffectiveModel());
        status.put("provider", isConfigured ? "Google Gemini API" : "Local Vector/Catalogue Search");
        status.put("vectorSearchReady", true);
        return ResponseEntity.ok(status);
    }
}
