package com.medicare.controller;

import jakarta.validation.Valid;

import com.medicare.dto.admin.UpdateUserStatusRequest;
import com.medicare.dto.admin.UserSummaryResponse;
import com.medicare.dto.common.PageResponse;
import com.medicare.enums.Role;
import com.medicare.security.CurrentUser;
import com.medicare.service.UserAdminService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: users", description = "Account search, activation/deactivation and unlocking")
public class AdminUserController {

    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    @Operation(summary = "Search user accounts")
    public PageResponse<UserSummaryResponse> search(@RequestParam(required = false) String search,
                                                    @RequestParam(required = false) Role role,
                                                    @RequestParam(required = false) Boolean active,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return userAdminService.search(search, role, active, page, size);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate an account")
    public UserSummaryResponse setStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return userAdminService.setActive(CurrentUser.id(), id, request);
    }

    @PatchMapping("/{id}/unlock")
    @Operation(summary = "Clear a temporary login lock")
    public UserSummaryResponse unlock(@PathVariable Long id) {
        return userAdminService.unlock(id);
    }
}
