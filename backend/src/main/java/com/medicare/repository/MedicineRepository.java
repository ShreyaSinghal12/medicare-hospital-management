package com.medicare.repository;

import java.util.List;

import com.medicare.entity.Medicine;
import com.medicare.enums.MedicineCategory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    @Query("""
            SELECT m FROM Medicine m
            WHERE (:includeInactive = true OR m.active = true)
              AND (:category IS NULL OR m.category = :category)
              AND (:search IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(m.genericName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(m.manufacturer) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Medicine> search(@Param("search") String search, @Param("category") MedicineCategory category,
                          @Param("includeInactive") boolean includeInactive, Pageable pageable);

    List<Medicine> findByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCaseAndStrengthIgnoreCaseAndManufacturerIgnoreCase(String name, String strength, String manufacturer);

    boolean existsByNameIgnoreCaseAndStrengthIgnoreCaseAndManufacturerIgnoreCaseAndIdNot(String name, String strength,
                                                                                        String manufacturer, Long id);
}
