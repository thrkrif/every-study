package com.everystudy.common.audit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영자 감사 로그 조회 (API 7장). {@code /api/v1/admin/**} 는 보안 설정이 {@code ADMIN} 만 통과시킨다. */
@RestController
@Validated
@RequestMapping("/api/v1/admin/audit-logs")
class AuditLogController {

    private final AuditLogQueryService service;

    AuditLogController(AuditLogQueryService service) {
        this.service = service;
    }

    @GetMapping
    AuditLogQueryService.Page list(@RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String action, @RequestParam(required = false) String targetType) {
        return service.list(cursor, size, action, targetType);
    }
}
