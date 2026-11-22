package com.everystudy.trust.web;

import com.everystudy.common.security.LoginUser;
import com.everystudy.trust.service.SanctionAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영자의 제재 조회와 이용 정지 부여·해제 (API 7장). {@code /api/v1/admin/**} 는 보안 설정이 {@code ADMIN} 만 통과시킨다. */
@RestController
@Validated
class AdminSanctionController {

    /** 이용 정지 부여 본문. 기간은 1~365일. */
    record SuspendRequest(@NotNull @Min(1) @Max(365) Integer days, @NotBlank @Size(max = 200) String note) {
    }

    /** 해제 사유. 필수다. */
    record NoteRequest(@NotBlank @Size(max = 200) String note) {
    }

    private final SanctionAdminService sanctions;

    AdminSanctionController(SanctionAdminService sanctions) {
        this.sanctions = sanctions;
    }

    @GetMapping("/api/v1/admin/sanctions")
    SanctionAdminService.Page list(@RequestParam(defaultValue = "0") long userId,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(defaultValue = "false") boolean reviewRequired,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return sanctions.list(userId, activeOnly, reviewRequired, cursor, size);
    }

    @PostMapping("/api/v1/admin/users/{id}/suspensions")
    SanctionAdminService.View suspend(@AuthenticationPrincipal LoginUser user, @PathVariable long id,
            @Valid @RequestBody SuspendRequest request) {
        return sanctions.suspend(user.userId(), id, request.days(), request.note().strip());
    }

    @DeleteMapping("/api/v1/admin/users/{id}/suspensions/{sanctionId}")
    ResponseEntity<Void> revokeSuspension(@AuthenticationPrincipal LoginUser user, @PathVariable long id,
            @PathVariable long sanctionId, @Valid @RequestBody NoteRequest request) {
        sanctions.revoke(user.userId(), id, sanctionId, request.note().strip());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/admin/sanctions/{sanctionId}")
    ResponseEntity<Void> revoke(@AuthenticationPrincipal LoginUser user, @PathVariable long sanctionId,
            @Valid @RequestBody NoteRequest request) {
        sanctions.revoke(user.userId(), 0, sanctionId, request.note().strip());
        return ResponseEntity.noContent().build();
    }
}
