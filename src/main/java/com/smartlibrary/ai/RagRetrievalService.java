package com.smartlibrary.ai;

import com.smartlibrary.dto.AiChatResponseDto;
import com.smartlibrary.dto.AiChatResponseDto.BookReferenceDto;
import com.smartlibrary.entity.Book;
import com.smartlibrary.entity.DocumentChunk;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.service.SystemSettingService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RagRetrievalService {

    private final BookRepository bookRepository;
    private final VectorStoreService vectorStoreService;
    private final SystemSettingService systemSettingService;

    public RagRetrievalService(BookRepository bookRepository,
                               VectorStoreService vectorStoreService,
                               SystemSettingService systemSettingService) {
        this.bookRepository = bookRepository;
        this.vectorStoreService = vectorStoreService;
        this.systemSettingService = systemSettingService;
    }

    @Transactional(readOnly = true)
    public RagContext assembleRagContext(String query) {
        double settingThreshold = systemSettingService.getDoubleSetting("semantic_search_threshold", 0.50);
        double threshold = Math.min(settingThreshold, 0.35);

        // 1. Vector semantic search for document chunks
        List<VectorStoreService.ScoredChunk> scoredChunks = vectorStoreService.findSimilarChunks(query, 5, threshold);
        if (scoredChunks.isEmpty()) {
            scoredChunks = vectorStoreService.findSimilarChunks(query, 5, 0.20);
        }

        // 2. Keyword search on book catalogue for direct title/author/description matches
        List<Book> keywordBooks = findBooksByExtractedKeywords(query);

        // 3. Assemble distinct matched books with actual DB availability
        Map<Long, Book> bookMap = new LinkedHashMap<>();
        for (Book b : keywordBooks) {
            bookMap.put(b.getId(), b);
        }
        for (VectorStoreService.ScoredChunk sc : scoredChunks) {
            if (sc.getChunk() != null && sc.getChunk().getBook() != null) {
                Book b = sc.getChunk().getBook();
                bookMap.put(b.getId(), b);
            }
        }

        // Build prompt context
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("=== VERIFIED LIBRARY CATALOGUE RECORDS (FROM DATABASE) ===\n");

        if (bookMap.isEmpty() && scoredChunks.isEmpty()) {
            contextBuilder.append("No books or documents directly matched the search query in the library database.\n");
        } else {
            for (Book b : bookMap.values()) {
                contextBuilder.append("- Book: \"").append(b.getTitle()).append("\"\n")
                        .append("  Author: ").append(b.getAuthor()).append("\n")
                        .append("  ISBN: ").append(b.getIsbn()).append("\n")
                        .append("  Category: ").append(b.getCategory().getName()).append("\n")
                        .append("  Shelf Location: ").append(b.getLocationShelf() != null ? b.getLocationShelf() : "General Stacks").append("\n")
                        .append("  Real-time Stock: ").append(b.getAvailableCopies()).append(" available copies (Total: ").append(b.getTotalCopies()).append(")\n")
                        .append("  Description: ").append(b.getDescription() != null ? b.getDescription() : "None").append("\n\n");
            }
        }

        if (!scoredChunks.isEmpty()) {
            contextBuilder.append("=== RETRIEVED DOCUMENT AND CONTENT EXCERPTS ===\n");
            for (VectorStoreService.ScoredChunk sc : scoredChunks) {
                DocumentChunk c = sc.getChunk();
                if (c != null) {
                    contextBuilder.append("[Source: ").append(c.getSourceTitle());
                    if (c.getPageNumber() != null) {
                        contextBuilder.append(" (Page ").append(c.getPageNumber()).append(")");
                    }
                    contextBuilder.append(" - Relevance Score: ").append(String.format("%.2f", sc.getScore())).append("]\n")
                            .append(c.getContent()).append("\n\n");
                }
            }
        }

        // Prepare structured DTO results
        List<BookReferenceDto> matchedBookDtos = new ArrayList<>();
        for (Book b : bookMap.values()) {
            matchedBookDtos.add(new BookReferenceDto(
                    b.getId(),
                    b.getIsbn(),
                    b.getTitle(),
                    b.getAuthor(),
                    b.getCategory().getName(),
                    b.getAvailableCopies(),
                    b.getTotalCopies(),
                    b.getLocationShelf()
            ));
        }

        List<String> sourceReferences = new ArrayList<>();
        for (Book b : bookMap.values()) {
            sourceReferences.add("Catalogue: " + b.getTitle() + " by " + b.getAuthor() + " (ISBN: " + b.getIsbn() + ")");
        }
        for (VectorStoreService.ScoredChunk sc : scoredChunks) {
            if (sc.getChunk() != null && sc.getChunk().getDigitalResource() != null) {
                sourceReferences.add("Digital Document: " + sc.getChunk().getSourceTitle() + " (Page " + sc.getChunk().getPageNumber() + ")");
            }
        }

        return new RagContext(contextBuilder.toString(), matchedBookDtos, sourceReferences, !scoredChunks.isEmpty() || !bookMap.isEmpty());
    }

    private List<Book> findBooksByExtractedKeywords(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        Set<Long> foundIds = new HashSet<>();
        List<Book> result = new ArrayList<>();

        // Try exact match first
        List<Book> directMatches = bookRepository.findTopMatchesByKeyword(query.trim(), PageRequest.of(0, 5));
        for (Book b : directMatches) {
            if (foundIds.add(b.getId())) {
                result.add(b);
            }
        }
        if (result.size() >= 5) {
            return result;
        }

        // Common conversational stop words to ignore when extracting core book search terms
        Set<String> stopWords = Set.of(
            "what", "where", "which", "when", "who", "whom", "whose", "why", "how",
            "book", "books", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "can", "could", "will", "would",
            "shall", "should", "may", "might", "must", "the", "a", "an", "and", "or",
            "but", "if", "in", "on", "at", "to", "for", "with", "about", "against",
            "between", "into", "through", "during", "before", "after", "above", "below",
            "from", "up", "down", "of", "off", "over", "under", "again", "further",
            "then", "once", "here", "there", "all", "any", "both", "each", "few", "more",
            "most", "other", "some", "such", "no", "nor", "not", "only", "own", "same",
            "so", "than", "too", "very", "just", "now", "tell", "show", "find",
            "available", "library", "shelf", "stock", "please", "recommend", "suggest",
            "give", "read", "reading", "study", "studying", "need", "want"
        );

        String cleaned = query.replaceAll("[^a-zA-Z0-9\\s]", " ").toLowerCase();
        String[] tokens = cleaned.split("\\s+");
        for (String token : tokens) {
            if (token.length() >= 3 && !stopWords.contains(token)) {
                List<Book> matches = bookRepository.findTopMatchesByKeyword(token, PageRequest.of(0, 5));
                for (Book b : matches) {
                    if (foundIds.add(b.getId())) {
                        result.add(b);
                        if (result.size() >= 5) break;
                    }
                }
            }
            if (result.size() >= 5) break;
        }

        return result;
    }

    public static class RagContext {
        private final String contextText;
        private final List<BookReferenceDto> matchedBooks;
        private final List<String> sourceReferences;
        private final boolean hasMatches;

        public RagContext(String contextText, List<BookReferenceDto> matchedBooks, List<String> sourceReferences, boolean hasMatches) {
            this.contextText = contextText;
            this.matchedBooks = matchedBooks;
            this.sourceReferences = sourceReferences;
            this.hasMatches = hasMatches;
        }

        public String getContextText() { return contextText; }
        public List<BookReferenceDto> getMatchedBooks() { return matchedBooks; }
        public List<String> getSourceReferences() { return sourceReferences; }
        public boolean isHasMatches() { return hasMatches; }
    }
}
