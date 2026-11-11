package com.medicare.repository;

import java.util.List;

import com.medicare.entity.LabTest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabTestRepository extends JpaRepository<LabTest, Long> {

    @Query("""
            SELECT t FROM LabTest t
            WHERE (:activeOnly = false OR t.active = true)
              AND (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(t.code) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY t.name ASC
            """)
    List<LabTest> search(@Param("search") String search, @Param("activeOnly") boolean activeOnly);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
