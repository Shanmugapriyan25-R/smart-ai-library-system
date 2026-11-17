package com.smartlibrary.repository;

import com.smartlibrary.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByBookId(Long bookId);

    List<DocumentChunk> findByDigitalResourceId(Long digitalResourceId);

    @Query("SELECT c FROM DocumentChunk c WHERE " +
           "LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.sourceTitle) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<DocumentChunk> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT c FROM DocumentChunk c WHERE c.embeddingVector IS NOT NULL")
    List<DocumentChunk> findAllWithEmbeddings();

    void deleteByBookId(Long bookId);

    void deleteByDigitalResourceId(Long digitalResourceId);
}
