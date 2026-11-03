package com.medicare.repository;

import java.util.List;
import java.util.Optional;

import com.medicare.entity.Department;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByActiveTrueOrderByNameAsc();

    Optional<Department> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    long countByActiveTrue();
}
