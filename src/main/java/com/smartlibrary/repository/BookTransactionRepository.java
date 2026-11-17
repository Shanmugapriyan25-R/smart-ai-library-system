package com.smartlibrary.repository;

import com.smartlibrary.entity.BookTransaction;
import com.smartlibrary.entity.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookTransactionRepository extends JpaRepository<BookTransaction, Long> {

    List<BookTransaction> findByUserIdOrderByIssueDateDesc(Long userId);

    Page<BookTransaction> findByUserIdOrderByIssueDateDesc(Long userId, Pageable pageable);

    List<BookTransaction> findByUserIdAndStatus(Long userId, TransactionStatus status);

    @Query("SELECT COUNT(t) FROM BookTransaction t WHERE t.user.id = :userId AND t.status = 'ISSUED'")
    long countActiveIssuesByUser(@Param("userId") Long userId);

    List<BookTransaction> findByStatus(TransactionStatus status);

    Page<BookTransaction> findByStatus(TransactionStatus status, Pageable pageable);

    @Query("SELECT t FROM BookTransaction t WHERE t.status = 'ISSUED' AND t.dueDate < :now ORDER BY t.dueDate ASC")
    List<BookTransaction> findOverdueTransactions(@Param("now") LocalDateTime now);

    @Query("SELECT t FROM BookTransaction t WHERE t.status = 'ISSUED' AND t.dueDate < :now ORDER BY t.dueDate ASC")
    Page<BookTransaction> findOverdueTransactions(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT COUNT(t) FROM BookTransaction t WHERE t.status = 'ISSUED' AND t.dueDate < :now")
    long countOverdueTransactions(@Param("now") LocalDateTime now);

    @Query("SELECT t FROM BookTransaction t WHERE t.book.id = :bookId AND t.status = 'ISSUED'")
    List<BookTransaction> findActiveTransactionsForBook(@Param("bookId") Long bookId);

    @Query("SELECT COUNT(t) FROM BookTransaction t WHERE t.status = 'ISSUED'")
    long countCurrentlyIssued();

    @Query("SELECT COUNT(t) FROM BookTransaction t WHERE t.issueDate >= :since")
    long countIssuesSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(t) FROM BookTransaction t WHERE t.returnDate >= :since")
    long countReturnsSince(@Param("since") LocalDateTime since);
}
