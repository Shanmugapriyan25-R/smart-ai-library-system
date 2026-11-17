package com.smartlibrary.repository;

import com.smartlibrary.entity.DigitalResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DigitalResourceRepository extends JpaRepository<DigitalResource, Long> {

    List<DigitalResource> findByAccessLevelIn(List<String> accessLevels);

    Page<DigitalResource> findByAccessLevelIn(List<String> accessLevels, Pageable pageable);

    @Query("SELECT d FROM DigitalResource d WHERE " +
           "(d.accessLevel IN :accessLevels) AND " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(d.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(d.author) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(d.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<DigitalResource> searchAccessibleResources(@Param("query") String query,
                                                    @Param("accessLevels") List<String> accessLevels,
                                                    Pageable pageable);
}
