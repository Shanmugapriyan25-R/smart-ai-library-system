package com.smartlibrary.repository;

import com.smartlibrary.entity.IssueRequest;
import com.smartlibrary.entity.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IssueRequestRepository extends JpaRepository<IssueRequest, Long> {

    List<IssueRequest> findByUserIdOrderByRequestDateDesc(Long userId);

    Page<IssueRequest> findByUserIdOrderByRequestDateDesc(Long userId, Pageable pageable);

    List<IssueRequest> findByStatusOrderByRequestDateAsc(RequestStatus status);

    Page<IssueRequest> findByStatusOrderByRequestDateAsc(RequestStatus status, Pageable pageable);

    @Query("SELECT r FROM IssueRequest r WHERE r.user.id = :userId AND r.book.id = :bookId AND r.status = 'PENDING'")
    Optional<IssueRequest> findPendingRequestByUserAndBook(@Param("userId") Long userId, @Param("bookId") Long bookId);

    long countByStatus(RequestStatus status);

    long countByUserIdAndStatus(Long userId, RequestStatus status);
}
