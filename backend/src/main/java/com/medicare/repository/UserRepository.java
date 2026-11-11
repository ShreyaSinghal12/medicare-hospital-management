package com.medicare.repository;

import java.util.Collection;
import java.util.Optional;

import com.medicare.entity.User;
import com.medicare.enums.Role;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRoleAndActiveTrue(Role role);

    java.util.List<User> findByRoleAndActiveTrue(Role role);

    /** Admin search: optional free text (name/email/phone), optional role, optional active flag. */
    @Query("""
            SELECT u FROM User u
            WHERE (:search IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR u.phone LIKE CONCAT('%', :search, '%'))
              AND (:role IS NULL OR u.role = :role)
              AND (:active IS NULL OR u.active = :active)
            """)
    Page<User> search(@Param("search") String search, @Param("role") Role role,
                      @Param("active") Boolean active, Pageable pageable);

    @Query("""
            SELECT u FROM User u
            WHERE u.role IN :roles
              AND (:search IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR u.phone LIKE CONCAT('%', :search, '%'))
              AND (:active IS NULL OR u.active = :active)
            """)
    Page<User> searchInRoles(@Param("search") String search, @Param("roles") Collection<Role> roles,
                             @Param("active") Boolean active, Pageable pageable);
}
