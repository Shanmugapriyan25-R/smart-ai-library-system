package com.smartlibrary.ai;

import com.smartlibrary.entity.Book;
import com.smartlibrary.entity.DigitalResource;
import com.smartlibrary.entity.DocumentChunk;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.DigitalResourceRepository;
import com.smartlibrary.repository.DocumentChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final DocumentChunkRepository chunkRepository;
    private final BookRepository bookRepository;
    private final DigitalResourceRepository digitalResourceRepository;
    private final DocumentChunkingService chunkingService;
    private final EmbeddingService embeddingService;

    // Cache of chunk id -> float[] embedding vector for fast cosine similarity scanning
    private final Map<Long, float[]> vectorCache = new ConcurrentHashMap<>();

    public VectorStoreService(DocumentChunkRepository chunkRepository,
                              BookRepository bookRepository,
                              DigitalResourceRepository digitalResourceRepository,
                              DocumentChunkingService chunkingService,
                              EmbeddingService embeddingService) {
        this.chunkRepository = chunkRepository;
        this.bookRepository = bookRepository;
        this.digitalResourceRepository = digitalResourceRepository;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
    }

    /**
     * Index a single Book: chunks it, generates vector embeddings, and stores in database & cache.
     */
    @Transactional
    public void indexBook(Book book) {
        chunkRepository.deleteByBookId(book.getId());
        List<DocumentChunk> chunks = chunkingService.chunkBook(book);
        for (DocumentChunk chunk : chunks) {
            float[] vector = embeddingService.generateEmbedding(chunk.getContent());
            chunk.setEmbeddingVector(embeddingService.vectorToJson(vector));
            DocumentChunk saved = chunkRepository.save(chunk);
            vectorCache.put(saved.getId(), vector);
        }
        log.info("Indexed {} chunks for book: {}", chunks.size(), book.getTitle());
    }

    /**
     * Index a DigitalResource (PDF): chunks pages, computes vectors, stores in DB & cache.
     */
    @Transactional
    public void indexDigitalResource(DigitalResource resource) {
        chunkRepository.deleteByDigitalResourceId(resource.getId());
        List<DocumentChunk> chunks = chunkingService.chunkDigitalResource(resource);
        for (DocumentChunk chunk : chunks) {
            float[] vector = embeddingService.generateEmbedding(chunk.getContent());
            chunk.setEmbeddingVector(embeddingService.vectorToJson(vector));
            DocumentChunk saved = chunkRepository.save(chunk);
            vectorCache.put(saved.getId(), vector);
        }
        log.info("Indexed {} chunks for digital resource: {}", chunks.size(), resource.getTitle());
    }

    /**
     * Rebuilds all vector indices across all active books and uploaded digital resources.
     */
    @Transactional
    public int rebuildAllIndices() {
        vectorCache.clear();
        chunkRepository.deleteAll();

        int totalIndexed = 0;
        List<Book> books = bookRepository.findByIsArchivedFalse();
        for (Book book : books) {
            indexBook(book);
            totalIndexed++;
        }

        List<DigitalResource> resources = digitalResourceRepository.findAll();
        for (DigitalResource res : resources) {
            indexDigitalResource(res);
            totalIndexed++;
        }

        log.info("Completed rebuilding vector indices for {} items.", totalIndexed);
        return totalIndexed;
    }

    /**
     * Finds top K similar document chunks by cosine similarity.
     */
    @Transactional(readOnly = true)
    public List<ScoredChunk> findSimilarChunks(String query, int topK, double threshold) {
        float[] queryVector = embeddingService.generateEmbedding(query);
        ensureCachePopulated();

        List<ScoredChunk> scoredList = new ArrayList<>();

        for (Map.Entry<Long, float[]> entry : vectorCache.entrySet()) {
            double score = embeddingService.calculateCosineSimilarity(queryVector, entry.getValue());
            if (score >= threshold) {
                scoredList.add(new ScoredChunk(entry.getKey(), score));
            }
        }

        scoredList.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        List<ScoredChunk> results = new ArrayList<>();
        int limit = Math.min(topK, scoredList.size());
        for (int i = 0; i < limit; i++) {
            ScoredChunk sc = scoredList.get(i);
            chunkRepository.findById(sc.getChunkId()).ifPresent(chunk -> {
                sc.setChunk(chunk);
                results.add(sc);
            });
        }

        return results;
    }

    private synchronized void ensureCachePopulated() {
        if (vectorCache.isEmpty()) {
            List<DocumentChunk> chunks = chunkRepository.findAllWithEmbeddings();
            for (DocumentChunk c : chunks) {
                if (c.getEmbeddingVector() != null && !c.getEmbeddingVector().isBlank()) {
                    float[] vec = embeddingService.jsonToVector(c.getEmbeddingVector());
                    if (vec.length > 0) {
                        vectorCache.put(c.getId(), vec);
                    }
                }
            }
        }
    }

    public static class ScoredChunk {
        private final Long chunkId;
        private final double score;
        private DocumentChunk chunk;

        public ScoredChunk(Long chunkId, double score) {
            this.chunkId = chunkId;
            this.score = score;
        }

        public Long getChunkId() { return chunkId; }
        public double getScore() { return score; }
        public DocumentChunk getChunk() { return chunk; }
        public void setChunk(DocumentChunk chunk) { this.chunk = chunk; }
    }
}
