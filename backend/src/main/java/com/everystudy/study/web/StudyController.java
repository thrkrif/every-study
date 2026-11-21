package com.everystudy.study.web;

import com.everystudy.common.code.JobCategoryCode;
import com.everystudy.common.code.KnownRegionCode;
import com.everystudy.common.code.KnownTopicCode;
import com.everystudy.common.security.LoginUser;
import com.everystudy.study.service.StudyCreationService;
import com.everystudy.study.service.StudyJoinService;
import com.everystudy.study.service.StudyLeaveService;
import com.everystudy.study.service.StudyQueryService;
import com.everystudy.study.service.StudyViews;
import com.everystudy.study.service.StudyWaitingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 스터디 (API 4장). 목록·상세는 공개이고, 로그인했으면 그 회원에게 맞춘 값(myState 등)을 준다. */
@RestController
@RequestMapping("/api/v1/studies")
class StudyController {

    private final StudyCreationService creation;
    private final StudyQueryService queries;
    private final StudyJoinService joining;
    private final StudyWaitingService waiting;
    private final StudyLeaveService leaving;

    StudyController(StudyCreationService creation, StudyQueryService queries, StudyJoinService joining,
            StudyWaitingService waiting, StudyLeaveService leaving) {
        this.leaving = leaving;
        this.waiting = waiting;
        this.creation = creation;
        this.queries = queries;
        this.joining = joining;
    }

    /**
     * 공개 목록. 기본은 모집 중인 스터디만이고, {@code includeClosed=true} 면 시작 전인 마감 스터디도 보여 준다.
     * 이미 시작했거나 끝난 스터디는 보여 주지 않는다.
     */
    @GetMapping
    StudyViews.Page search(
            @AuthenticationPrincipal LoginUser user,
            @RequestParam(required = false) @KnownRegionCode String regionCode,
            @RequestParam(required = false) @KnownTopicCode String topicCode,
            @RequestParam(required = false) @JobCategoryCode String jobCode,
            @RequestParam(defaultValue = "false") boolean myJob,
            @RequestParam(defaultValue = "false") boolean includeClosed,
            @RequestParam(defaultValue = "latest") @Pattern(regexp = "latest|deadline",
                    message = "latest 또는 deadline 이어야 합니다") String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return queries.search(new StudyQueryService.SearchQuery(regionCode, topicCode, jobCode, myJob, includeClosed,
                sort, cursor, size), viewerId(user));
    }

    /** 상세. 비회원도 볼 수 있다. */
    @GetMapping("/{id}")
    StudyViews.Detail detail(@AuthenticationPrincipal LoginUser user, @PathVariable long id) {
        return queries.detail(id, viewerId(user));
    }

    /** 참여자 목록. 그 스터디의 참여자만 볼 수 있다. */
    @GetMapping("/{id}/members")
    List<StudyViews.Member> members(@AuthenticationPrincipal LoginUser user, @PathVariable long id) {
        return queries.members(id, user.userId());
    }

    record CreatedStudy(long id) {
    }

    /** 개설. {@code 201} 과 새 스터디 주소({@code Location}), 본문은 ID 만. */
    @PostMapping
    ResponseEntity<CreatedStudy> create(@AuthenticationPrincipal LoginUser user,
            @Valid @RequestBody CreateStudyRequest request) {
        long id = creation.create(user.userId(), request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/studies/" + id)).body(new CreatedStudy(id));
    }

    /** 참여 신청. 승인 없이 바로 확정되어 {@code 201}, 본문은 없다. 결과는 상세의 {@code myState} 로 본다. */
    @PostMapping("/{id}/members")
    ResponseEntity<Void> join(@AuthenticationPrincipal LoginUser user, @PathVariable long id) {
        joining.join(id, user.userId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** 나가기 = 참여 취소. 사전/직전 취소 판정을 본문({@code cancelType})으로 알려 준다. */
    @DeleteMapping("/{id}/members/me")
    StudyLeaveService.Left leave(@AuthenticationPrincipal LoginUser user, @PathVariable long id) {
        return leaving.leave(id, user.userId());
    }

    /** 나가기 확인용 사전 조회. 아무것도 바꾸지 않는다. */
    @GetMapping("/{id}/leave-preview")
    StudyLeaveService.Preview leavePreview(@AuthenticationPrincipal LoginUser user, @PathVariable long id) {
        return leaving.preview(id, user.userId());
    }

    /** 대기 등록. 정원이 차 있을 때만 가능하다. {@code 201}, 본문은 없다. */
    @PostMapping("/{id}/waiting")
    ResponseEntity<Void> wait(@AuthenticationPrincipal LoginUser user, @PathVariable long id) {
        waiting.register(id, user.userId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** 대기 취소. 대기 중이 아니어도 {@code 204} 다. */
    @DeleteMapping("/{id}/waiting/me")
    ResponseEntity<Void> cancelWaiting(@AuthenticationPrincipal LoginUser user, @PathVariable long id) {
        waiting.cancel(id, user.userId());
        return ResponseEntity.noContent().build();
    }

    private static Long viewerId(LoginUser user) {
        return user == null ? null : user.userId();
    }
}
