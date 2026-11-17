package com.smartlibrary.repository;

import com.smartlibrary.entity.StudySession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    List<StudySession> findByUserIdOrderByCompletedAtDesc(Long userId);

    Page<StudySession> findByUserIdOrderByCompletedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT COUNT(s) FROM StudySession s WHERE s.user.id = :userId")
    long countCompletedSessionsByUser(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(s.durationMinutes), 0) FROM StudySession s WHERE s.user.id = :userId")
    long sumStudyMinutesByUser(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(s.durationMinutes), 0) FROM StudySession s WHERE s.user.id = :userId AND s.completedAt >= :since")
    long sumStudyMinutesByUserSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
