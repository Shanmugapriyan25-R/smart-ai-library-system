package com.smartlibrary.repository;

import com.smartlibrary.entity.ReadingProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, Long> {
    Optional<ReadingProgress> findByUserIdAndDigitalResourceId(Long userId, Long digitalResourceId);
    List<ReadingProgress> findByUserIdOrderByLastReadAtDesc(Long userId);
}
