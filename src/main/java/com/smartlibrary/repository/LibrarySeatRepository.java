package com.smartlibrary.repository;

import com.smartlibrary.entity.LibrarySeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LibrarySeatRepository extends JpaRepository<LibrarySeat, Long> {

    Optional<LibrarySeat> findBySeatNumber(String seatNumber);

    List<LibrarySeat> findByStatus(String status);

    @Query("SELECT DISTINCT s.floor FROM LibrarySeat s ORDER BY s.floor")
    List<String> findDistinctFloors();

    @Query("SELECT DISTINCT s.section FROM LibrarySeat s ORDER BY s.section")
    List<String> findDistinctSections();

    long countByStatus(String status);
}
