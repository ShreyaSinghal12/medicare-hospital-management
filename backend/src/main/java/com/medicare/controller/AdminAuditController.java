package com.medicare.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.medicare.dto.admin.AuditLogEntry;
import com.medicare.dto.common.PageResponse;
import com.medicare.entity.AuditLog;
import com.medicare.enums.AuditOutcome;
import com.medicare.repository.AuditLogRepository;
import com.medicare.service.DateRange;
import com.medicare.service.HospitalClock;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only audit log viewer (administrators). The log itself cannot be changed through the application. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: audit log")
public class AdminAuditController {

    private final AuditLogRepository repository;
    private final HospitalClock clock;

    public AdminAuditController(AuditLogRepository repository, HospitalClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @GetMapping("/api/admin/audit")
    @Transactional(readOnly = true)
    @Operation(summary = "Search the audit log (user e-mail, action, record type/id, outcome, date range; default last 7 days)")
    public PageResponse<AuditLogEntry> search(@RequestParam(required = false) String actor,
                                              @RequestParam(required = false) String action,
                                              @RequestParam(required = false) String entityType,
                                              @RequestParam(required = false) String entityId,
                                              @RequestParam(required = false) AuditOutcome outcome,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "25") int size) {
        DateRange range = DateRange.of(from, to, clock.today(), 7, clock.zone());
        Instant start = range.start();
        Instant end = range.end();
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            p.add(cb.lessThan(root.get("createdAt"), end));
            if (actor != null && !actor.isBlank()) {
                p.add(cb.like(cb.lower(root.get("actorEmail")), "%" + escape(actor.trim().toLowerCase(Locale.ROOT)) + "%", '\\'));
            }
            if (action != null && !action.isBlank()) {
                p.add(cb.like(root.get("action"), "%" + escape(action.trim().toUpperCase(Locale.ROOT)) + "%", '\\'));
            }
            if (entityType != null && !entityType.isBlank()) {
                p.add(cb.equal(root.get("entityType"), entityType.trim()));
            }
            if (entityId != null && !entityId.isBlank()) {
                p.add(cb.equal(root.get("entityId"), entityId.trim()));
            }
            if (outcome != null) {
                p.add(cb.equal(root.get("outcome"), outcome));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        var result = repository.findAll(spec, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return PageResponse.from(result, a -> new AuditLogEntry(a.getId(), a.getCreatedAt(), a.getActorUserId(), a.getActorEmail(),
                a.getActorRole(), a.getAction(), a.getEntityType(), a.getEntityId(), a.getOutcome(), a.getDetails(), a.getIpAddress()));
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
