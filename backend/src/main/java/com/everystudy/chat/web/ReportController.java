package com.everystudy.chat.web;

import com.everystudy.chat.domain.ReportStatus;
import com.everystudy.chat.service.ReportService;
import com.everystudy.common.error.FieldViolation;
import com.everystudy.common.error.InvalidInputException;
import com.everystudy.common.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 신고 접수와 운영자 신고 큐 (API 7장). 운영자 경로는 보안 설정이 {@code ADMIN} 만 통과시킨다. */
@RestController
@Validated
class ReportController {

    private final ReportService reports;

    ReportController(ReportService reports) {
        this.reports = reports;
    }

    @PostMapping("/api/v1/reports")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> file(@AuthenticationPrincipal LoginUser user, @Valid @RequestBody ReportRequest request) {
        long id = reports.file(user.userId(), request.targetType(), request.targetId(), request.reason(),
                request.detail());
        return Map.of("id", id);
    }

    /** {@code status} 는 {@code open}(기본)·{@code dismissed}·{@code resolved}·{@code all}. */
    @GetMapping("/api/v1/admin/reports")
    ReportService.Page queue(@RequestParam(defaultValue = "open") String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return reports.list(parse(status), cursor, size);
    }

    @PostMapping("/api/v1/admin/reports/{id}/dismiss")
    ReportService.AdminView dismiss(@AuthenticationPrincipal LoginUser user, @PathVariable long id,
            @Valid @RequestBody ReportNoteRequest request) {
        return reports.handle(user.userId(), id, false, request.note().strip());
    }

    @PostMapping("/api/v1/admin/reports/{id}/resolve")
    ReportService.AdminView resolve(@AuthenticationPrincipal LoginUser user, @PathVariable long id,
            @Valid @RequestBody ReportNoteRequest request) {
        return reports.handle(user.userId(), id, true, request.note().strip());
    }

    private static ReportStatus parse(String status) {
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "open" -> ReportStatus.OPEN;
            case "dismissed" -> ReportStatus.DISMISSED;
            case "resolved" -> ReportStatus.RESOLVED;
            case "all" -> null;
            default -> throw new InvalidInputException(List.of(new FieldViolation("status",
                    "open, dismissed, resolved, all 중 하나여야 합니다")));
        };
    }
}
