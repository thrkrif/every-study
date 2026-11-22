package com.everystudy.attendance.web;

import com.everystudy.attendance.domain.AppealStatus;
import com.everystudy.attendance.service.AppealDecisionService;
import com.everystudy.common.error.FieldViolation;
import com.everystudy.common.error.InvalidInputException;
import com.everystudy.common.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Locale;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영자의 이의 목록과 최종 결정 (API 7장). {@code /api/v1/admin/**} 는 보안 설정이 {@code ADMIN} 만 통과시킨다. */
@RestController
@Validated
@RequestMapping("/api/v1/admin/appeals")
class AdminAppealController {

    private final AppealDecisionService decisions;

    AdminAppealController(AppealDecisionService decisions) {
        this.decisions = decisions;
    }

    /** {@code status} 는 {@code open}(기본)·{@code accepted}·{@code rejected}·{@code all}. */
    @GetMapping
    AppealDecisionService.Page list(@RequestParam(defaultValue = "open") String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return decisions.list(parse(status), cursor, size);
    }

    @PostMapping("/{id}/decision")
    AppealDecisionService.AdminView decide(@AuthenticationPrincipal LoginUser user, @PathVariable long id,
            @Valid @RequestBody AppealDecisionRequest request) {
        return decisions.decide(user.userId(), id, request.accept(), request.note().strip());
    }

    private static AppealStatus parse(String status) {
        String value = status.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "open" -> AppealStatus.OPEN;
            case "accepted" -> AppealStatus.ACCEPTED;
            case "rejected" -> AppealStatus.REJECTED;
            case "all" -> null;
            default -> throw new InvalidInputException(List.of(new FieldViolation("status",
                    "open, accepted, rejected, all 중 하나여야 합니다")));
        };
    }
}
