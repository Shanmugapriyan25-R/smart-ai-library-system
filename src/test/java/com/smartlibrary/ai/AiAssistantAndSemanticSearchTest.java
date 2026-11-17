package com.smartlibrary.ai;

import com.smartlibrary.dto.AiChatRequestDto;
import com.smartlibrary.dto.AiChatResponseDto;
import com.smartlibrary.dto.SemanticSearchResultDto;
import com.smartlibrary.entity.Book;
import com.smartlibrary.entity.Category;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantAndSemanticSearchTest {

    @Mock
    private RagRetrievalService ragRetrievalService;

    @Mock
    private LlmClientService llmClientService;

    @Mock
    private VectorStoreService vectorStoreService;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private SystemSettingService systemSettingService;

    private AiAssistantService aiAssistantService;
    private EmbeddingService embeddingService;
    private Book sampleBook;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(systemSettingService);
        aiAssistantService = new AiAssistantService(
                ragRetrievalService,
                llmClientService,
                vectorStoreService,
                bookRepository,
                systemSettingService
        );

        Category cat = new Category("Computer Science", "CS", "CS text");
        sampleBook = new Book();
        sampleBook.setId(1L);
        sampleBook.setTitle("Designing Data-Intensive Applications");
        sampleBook.setAuthor("Martin Kleppmann");
        sampleBook.setIsbn("978-1491957660");
        sampleBook.setCategory(cat);
        sampleBook.setTotalCopies(5);
        sampleBook.setAvailableCopies(4);
        sampleBook.setLocationShelf("Shelf CS-04");
        sampleBook.setDescription("Reliable, scalable, and maintainable systems, replication and partitioning.");
    }

    @Test
    @DisplayName("Local deterministic semantic vector generation produces normalized vectors with valid cosine similarity")
    void testLocalSemanticVectorSimilarity() {
        String text1 = "distributed systems replication partitioning fault tolerance consensus";
        String text2 = "distributed replication and partitioning systems";
        String text3 = "renaissance history classic literature poetry";

        float[] v1 = embeddingService.generateLocalSemanticVector(text1);
        float[] v2 = embeddingService.generateLocalSemanticVector(text2);
        float[] v3 = embeddingService.generateLocalSemanticVector(text3);

        double similarityRelated = embeddingService.calculateCosineSimilarity(v1, v2);
        double similarityUnrelated = embeddingService.calculateCosineSimilarity(v1, v3);

        assertThat(similarityRelated).isGreaterThan(0.70);
        assertThat(similarityUnrelated).isLessThan(similarityRelated);
    }

    @Test
    @DisplayName("AI Assistant executes RAG question answering with verified inventory matching")
    void testAiAssistantChatRAG() {
        AiChatRequestDto request = new AiChatRequestDto("How can I design scalable systems?");

        when(systemSettingService.getBooleanSetting("ai_assistant_enabled", true)).thenReturn(true);

        AiChatResponseDto.BookReferenceDto bookRef = new AiChatResponseDto.BookReferenceDto(
                1L, "978-1491957660", "Designing Data-Intensive Applications", "Martin Kleppmann",
                "Computer Science", 4, 5, "Shelf CS-04"
        );

        RagRetrievalService.RagContext context = new RagRetrievalService.RagContext(
                "Book: Designing Data-Intensive Applications, 4 copies available",
                List.of(bookRef),
                List.of("Catalogue: Designing Data-Intensive Applications"),
                true
        );

        when(ragRetrievalService.assembleRagContext(anyString())).thenReturn(context);
        when(llmClientService.generateAnswer(anyString(), anyString())).thenReturn(
                new LlmClientService.LlmResult(
                        "Designing Data-Intensive Applications by Martin Kleppmann is available on Shelf CS-04 with 4 copies in stock.",
                        true, false, "Gemini Online"
                )
        );

        AiChatResponseDto response = aiAssistantService.processChatQuery(request);

        assertThat(response).isNotNull();
        assertThat(response.getAnswer()).contains("Designing Data-Intensive Applications");
        assertThat(response.isRagUsed()).isTrue();
        assertThat(response.getMatchedBooks()).hasSize(1);
        assertThat(response.getMatchedBooks().get(0).getAvailableCopies()).isEqualTo(4);
    }

    @Test
    @DisplayName("AI Assistant returns structured fallback response when LLM key is absent")
    void testAiAssistantFallbackMode() {
        AiChatRequestDto request = new AiChatRequestDto("Where are the algorithms books?");

        when(systemSettingService.getBooleanSetting("ai_assistant_enabled", true)).thenReturn(true);

        RagRetrievalService.RagContext context = new RagRetrievalService.RagContext(
                "Catalogue records found",
                List.of(),
                List.of(),
                false
        );

        when(ragRetrievalService.assembleRagContext(anyString())).thenReturn(context);
        when(llmClientService.generateAnswer(anyString(), anyString())).thenReturn(
                new LlmClientService.LlmResult(
                        "Based on the library database: Check the verified titles below.",
                        false, true, "Local Knowledge-Base Search (No GEMINI_API_KEY Configured)"
                )
        );

        AiChatResponseDto response = aiAssistantService.processChatQuery(request);

        assertThat(response).isNotNull();
        assertThat(response.isFallbackMode()).isTrue();
        assertThat(response.getProviderStatus()).contains("Local Knowledge-Base Search");
    }

    @Test
    @DisplayName("Semantic Search returns scored results and marks vector search mode")
    void testSemanticSearchExecution() {
        when(systemSettingService.getDoubleSetting("semantic_search_threshold", 0.35)).thenReturn(0.35);

        VectorStoreService.ScoredChunk scored = new VectorStoreService.ScoredChunk(10L, 0.88);
        com.smartlibrary.entity.DocumentChunk chunk = new com.smartlibrary.entity.DocumentChunk("Designing Data", 0, 1, "Replication systems");
        chunk.setBook(sampleBook);
        scored.setChunk(chunk);

        when(vectorStoreService.findSimilarChunks(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(scored));

        List<SemanticSearchResultDto> results = aiAssistantService.performSemanticSearch("distributed databases", 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getBook().getTitle()).isEqualTo("Designing Data-Intensive Applications");
        assertThat(results.get(0).getScore()).isEqualTo(0.88);
        assertThat(results.get(0).getSearchMode()).isEqualTo("SEMANTIC_VECTOR");
    }
}
