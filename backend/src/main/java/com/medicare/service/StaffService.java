package com.medicare.service;

import java.util.EnumSet;
import java.util.Set;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.staff.CreateStaffRequest;
import com.medicare.dto.staff.StaffResponse;
import com.medicare.entity.Receptionist;
import com.medicare.entity.User;
import com.medicare.enums.AuditOutcome;
import com.medicare.enums.Role;
import com.medicare.exception.BusinessRuleException;
import com.medicare.repository.ReceptionistRepository;
import com.medicare.repository.UserRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin management of non-doctor staff accounts: receptionists and administrators. */
@Service
public class StaffService {

    /** Roles this service manages. Doctors use DoctorService; patients self-register or are registered at reception. */
    public static final Set<Role> STAFF_ROLES = EnumSet.of(Role.RECEPTIONIST, Role.ADMIN);

    private final UserRepository userRepository;
    private final ReceptionistRepository receptionistRepository;
    private final StaffAccountService staffAccountService;
    private final SequenceService sequenceService;
    private final AuthService authService;
    private final AuditService auditService;

    public StaffService(UserRepository userRepository, ReceptionistRepository receptionistRepository,
                        StaffAccountService staffAccountService, SequenceService sequenceService,
                        AuthService authService, AuditService auditService) {
        this.userRepository = userRepository;
        this.receptionistRepository = receptionistRepository;
        this.staffAccountService = staffAccountService;
        this.sequenceService = sequenceService;
        this.authService = authService;
        this.auditService = auditService;
    }

    @Transactional
    public StaffResponse create(CreateStaffRequest req) {
        if (!STAFF_ROLES.contains(req.role())) {
            throw new BusinessRuleException("UNSUPPORTED_ROLE",
                    "Use the doctor form for doctors. Patients register themselves or at reception.");
        }
        User user = staffAccountService.createStaffUser(req.email(), req.fullName(), req.phone(), req.role());
        if (req.role() == Role.RECEPTIONIST) {
            receptionistRepository.save(new Receptionist(user, sequenceService.nextEmployeeCode()));
        }
        authService.sendAccountSetupLink(user);
        auditService.record(AuditService.Actions.STAFF_CREATED, "User", user.getId(), AuditOutcome.SUCCESS,
                "Role: " + req.role());
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<StaffResponse> search(Role role, String search, Boolean active, int page, int size) {
        if (role != null && !STAFF_ROLES.contains(role)) {
            throw new BusinessRuleException("UNSUPPORTED_ROLE", "Role filter must be RECEPTIONIST or ADMIN");
        }
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("fullName"));
        String term = search == null || search.isBlank() ? null : search.trim();
        var users = role != null
                ? userRepository.search(term, role, active, pageable)
                : userRepository.searchInRoles(term, STAFF_ROLES, active, pageable);
        return PageResponse.from(users, this::toResponse);
    }

    private StaffResponse toResponse(User u) {
        String code = u.getRole() == Role.RECEPTIONIST
                ? receptionistRepository.findByUserId(u.getId()).map(Receptionist::getEmployeeCode).orElse(null)
                : null;
        return new StaffResponse(u.getId(), u.getRole(), u.getFullName(), u.getEmail(), u.getPhone(), u.isActive(),
                code, u.getLastLoginAt(), u.getCreatedAt());
    }
}
