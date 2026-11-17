package com.smartlibrary.ai;

import com.smartlibrary.dto.AiChatRequestDto;
import com.smartlibrary.dto.AiChatResponseDto;
import com.smartlibrary.dto.SemanticSearchResultDto;
import com.smartlibrary.entity.Book;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.service.SystemSettingService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AiAssistantService {

    private final RagRetrievalService ragRetrievalService;
    private final LlmClientService llmClientService;
    private final VectorStoreService vectorStoreService;
    private final BookRepository bookRepository;
    private final SystemSettingService systemSettingService;

    public AiAssistantService(RagRetrievalService ragRetrievalService,
                              LlmClientService llmClientService,
                              VectorStoreService vectorStoreService,
                              BookRepository bookRepository,
                              SystemSettingService systemSettingService) {
        this.ragRetrievalService = ragRetrievalService;
        this.llmClientService = llmClientService;
        this.vectorStoreService = vectorStoreService;
        this.bookRepository = bookRepository;
        this.systemSettingService = systemSettingService;
    }

    /**
     * Executes RAG-powered question answering across library catalogue and digital documents.
     */
    @Transactional(readOnly = true)
    public AiChatResponseDto processChatQuery(AiChatRequestDto request) {
        if (!systemSettingService.getBooleanSetting("ai_assistant_enabled", true)) {
            return new AiChatResponseDto(
                    "The AI Assistant is currently disabled in library system settings.",
                    false,
                    true,
                    "Service Disabled by Admin"
            );
        }

        String query = request.getQuery().trim();

        // 1. Assemble RAG Context from database catalogue and document vector store
        RagRetrievalService.RagContext ragContext = ragRetrievalService.assembleRagContext(query);

        // 2. Prepare strict grounding system prompt
        String systemPrompt =
                "You are the intelligent Library Assistant for the Smart AI Library Management System.\n" +
                "You have access to verified library catalogue database records and document excerpts.\n" +
                "GUIDELINES:\n" +
                "1. Base your answer strictly on the provided library context.\n" +
                "2. State the exact book title, author, shelf location, and number of currently available copies.\n" +
                "3. NEVER fabricate, hallucinate, or assume books that are not in the provided records.\n" +
                "4. If a requested book or topic is not in the library catalogue, clearly explain that it is not currently in the collection.\n" +
                "5. Always remind students that they can submit an issue request through their portal if copies are available.\n\n" +
                "CONTEXT:\n" + ragContext.getContextText();

        // 3. Generate answer from LLM or structured local retrieval fallback
        LlmClientService.LlmResult llmResult = llmClientService.generateAnswer(systemPrompt, query);

        AiChatResponseDto responseDto = new AiChatResponseDto();
        responseDto.setAnswer(llmResult.getText());
        responseDto.setRagUsed(ragContext.isHasMatches());
        responseDto.setFallbackMode(llmResult.isFallback());
        responseDto.setProviderStatus(llmResult.getProviderStatus());
        responseDto.setMatchedBooks(ragContext.getMatchedBooks());
        responseDto.setSourceReferences(ragContext.getSourceReferences());

        return responseDto;
    }

    /**
     * Performs semantic book search with cosine similarity, with clear keyword fallback when needed.
     */
    @Transactional(readOnly = true)
    public List<SemanticSearchResultDto> performSemanticSearch(String query, int topK) {
        double threshold = systemSettingService.getDoubleSetting("semantic_search_threshold", 0.35);
        List<VectorStoreService.ScoredChunk> scoredChunks = vectorStoreService.findSimilarChunks(query, topK, threshold);

        Map<Long, SemanticSearchResultDto> resultMap = new LinkedHashMap<>();

        // Process vector matches
        for (VectorStoreService.ScoredChunk sc : scoredChunks) {
            if (sc.getChunk() != null && sc.getChunk().getBook() != null) {
                Book book = sc.getChunk().getBook();
                if (!Boolean.TRUE.equals(book.getIsArchived())) {
                    resultMap.put(book.getId(), new SemanticSearchResultDto(
                            book,
                            sc.getScore(),
                            sc.getChunk().getContent(),
                            "SEMANTIC_VECTOR"
                    ));
                }
            }
        }

        // Augment with keyword matches if vector results are fewer than desired
        if (resultMap.size() < topK) {
            List<Book> keywordBooks = bookRepository.findTopMatchesByKeyword(query, PageRequest.of(0, topK));
            for (Book b : keywordBooks) {
                if (!resultMap.containsKey(b.getId())) {
                    resultMap.put(b.getId(), new SemanticSearchResultDto(
                            b,
                            0.50,
                            b.getTitle() + " - " + b.getDescription(),
                            "KEYWORD_FALLBACK"
                    ));
                }
            }
        }

        return new ArrayList<>(resultMap.values());
    }
}
