package com.everystudy.attendance.service;

import com.everystudy.attendance.domain.Appeal;
import com.everystudy.attendance.repository.AppealRepository;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.service.StudySessions;
import com.everystudy.trust.service.AttendanceLedger;
import com.everystudy.trust.service.AttendanceLedger.Verdict;
import com.everystudy.user.service.PublicUser;
import com.everystudy.user.service.UserDirectory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이의 신청 (PRD 4.9, API 5장, PLAN T-034). 대상은 노쇼 후보와 지각 판정이고 판정당 1회, 본인만 낸다. 기한은 판정 이벤트의
 * 발생 시각부터 {@code everystudy.attendance.appeal-window-hours} 이다. 운영자 최종 결정은 T-056 이다.
 */
@Service
public class AppealService {

    /** 이의 내역 한 건. */
    public record View(long id, long attendanceEventId, long studyId, String studyTitle, String verdictType,
            String reason, String hostOpinion, String status, Instant dueAt, Instant createdAt, Instant decidedAt) {
    }

    /** 주최자가 보는 이의 한 건. 신청자는 닉네임만 보이고(탈퇴했으면 {@code null}) 판정 종류와 사유가 의견의 근거다. */
    public record HostView(long id, String applicantNickname, String verdictType, String reason, String hostOpinion,
            String status, Instant dueAt, Instant createdAt) {
    }

    /** 내 이의 내역 한 페이지. */
    public record Page(List<View> items, String nextCursor) {
    }

    private final AppealRepository appeals;
    private final AttendanceLedger ledger;
    private final StudySessions sessions;
    private final AttendanceProperties properties;
    private final TimeProvider time;
    private final UserDirectory users;

    AppealService(AppealRepository appeals, AttendanceLedger ledger, StudySessions sessions,
            AttendanceProperties properties, TimeProvider time, UserDirectory users) {
        this.users = users;
        this.appeals = appeals;
        this.ledger = ledger;
        this.sessions = sessions;
        this.properties = properties;
        this.time = time;
    }

    /**
     * 최근 {@code appealRejectWindowDays} 일에 기각이 {@code appealRejectLimit} 회 이상이면 마지막 기각 결정부터
     * {@code appealRestrictDays} 일 동안 새 이의를 받지 않는다 (PRD 4.9, PLAN D-11). 제한이 끝나면 다시 신청할 수 있다.
     */
    private void requireNotRestricted(long userId, Instant now) {
        List<Instant> rejected = appeals.findRejectedDecisionTimes(userId,
                now.minus(Duration.ofDays(properties.appealRejectWindowDays())));
        if (rejected.size() < properties.appealRejectLimit()) {
            return;
        }
        Instant until = rejected.get(0).plus(Duration.ofDays(properties.appealRestrictDays()));
        if (until.isAfter(now)) {
            throw new BusinessException(AttendanceErrorCode.APPEAL_RESTRICTED, null, Map.of("until", until));
        }
    }

    /** 이의 기한: 판정 이벤트 발생 시각 + 이의 기간. */
    Instant dueAt(Instant verdictOccurredAt) {
        return verdictOccurredAt.plus(Duration.ofHours(properties.appealWindowHours()));
    }

    /**
     * 이의를 신청한다. 검사 순서: 판정 없음 {@code NOT_FOUND} → 내 판정 아님 {@code FORBIDDEN} → 이의 대상 종류 아님
     * {@code APPEAL_NOT_ALLOWED} → 반복 기각으로 제한 중 {@code APPEAL_RESTRICTED}(응답에 {@code until}) → 이미 신청함 {@code ALREADY_APPEALED} → 기한 지남 {@code APPEAL_CLOSED} → 뒤따르는
     * 판정(노쇼 확정 등)이 있음 {@code APPEAL_NOT_ALLOWED}.
     */
    @Transactional
    public View file(long userId, long eventId, String reason) {
        Verdict verdict = ledger.find(eventId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (verdict.userId() != userId) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (!verdict.appealable()) {
            throw new BusinessException(AttendanceErrorCode.APPEAL_NOT_ALLOWED);
        }
        Instant now = time.now();
        requireNotRestricted(userId, now);
        if (appeals.existsByAttendanceEventId(eventId)) {
            throw new BusinessException(AttendanceErrorCode.ALREADY_APPEALED);
        }
        Instant dueAt = dueAt(verdict.occurredAt());
        if (now.isAfter(dueAt)) {
            throw new BusinessException(AttendanceErrorCode.APPEAL_CLOSED);
        }
        if (!ledger.isCurrent(eventId, verdict.studyId(), userId)) {
            throw new BusinessException(AttendanceErrorCode.APPEAL_NOT_ALLOWED);
        }
        try {
            Appeal appeal = appeals.saveAndFlush(Appeal.open(eventId, verdict.studyId(), userId, reason, dueAt, now));
            return views(List.of(appeal), Map.of(eventId, verdict)).get(0);
        } catch (DataIntegrityViolationException e) {
            // 같은 판정에 동시에 두 번 신청: 유일 제약(uk_appeal_event)이 한 건만 남긴다.
            throw new BusinessException(AttendanceErrorCode.ALREADY_APPEALED);
        }
    }

    /**
     * 주최자 1차 의견. 검사 순서: 이의 없음 {@code NOT_FOUND} → 그 스터디의 현재 주최자 아님 {@code FORBIDDEN} → 이미
     * 결정됨 {@code APPEAL_CLOSED}. 결정 전에는 다시 보내면 덮어쓴다 (멱등).
     */
    @Transactional
    public View writeHostOpinion(long userId, long appealId, String opinion) {
        Appeal appeal = appeals.findById(appealId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        boolean host = sessions.read(appeal.getStudyId(), userId)
                .map(session -> session.hostId() == userId)
                .orElse(false);
        if (!host) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (!appeal.isOpen()) {
            throw new BusinessException(AttendanceErrorCode.APPEAL_CLOSED);
        }
        appeal.writeHostOpinion(opinion);
        Verdict verdict = ledger.find(appeal.getAttendanceEventId()).orElseThrow();
        return views(List.of(appeal), Map.of(verdict.id(), verdict)).get(0);
    }

    /**
     * 이 스터디에 들어온 이의, 최근 신청부터. 주최자가 1차 의견을 쓸 이의를 찾는 곳이다. 검사 순서: 스터디 없음
     * {@code NOT_FOUND} → 그 스터디의 현재 주최자 아님 {@code FORBIDDEN}.
     */
    @Transactional(readOnly = true)
    public List<HostView> forStudy(long userId, long studyId) {
        StudySessions.Session session = sessions.read(studyId, userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (session.hostId() != userId) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        List<Appeal> rows = appeals.findByStudyIdOrderByIdDesc(studyId);
        Map<Long, PublicUser> applicants = users.findPublic(
                rows.stream().map(Appeal::getUserId).collect(Collectors.toSet()));
        return rows.stream().map(a -> {
            Verdict verdict = ledger.find(a.getAttendanceEventId()).orElseThrow();
            PublicUser applicant = applicants.get(a.getUserId());
            return new HostView(a.getId(), applicant == null ? null : applicant.nickname(), verdict.type(),
                    a.getReason(), a.getHostOpinion(), a.getStatus().name(), a.getDueAt(), a.getCreatedAt());
        }).toList();
    }

    /** 내 이의 내역, 최근 것부터 (커서). */
    @Transactional(readOnly = true)
    public Page mine(long userId, String cursor, int size) {
        List<Appeal> rows = appeals.findByUserIdAndIdLessThanOrderByIdDesc(userId, IdCursor.decode(cursor),
                PageRequest.of(0, size + 1));
        boolean hasNext = rows.size() > size;
        List<Appeal> page = hasNext ? rows.subList(0, size) : rows;
        Map<Long, Verdict> verdicts = page.stream()
                .map(a -> ledger.find(a.getAttendanceEventId()).orElseThrow())
                .collect(Collectors.toMap(Verdict::id, v -> v));
        return new Page(views(page, verdicts), hasNext ? IdCursor.encode(page.get(page.size() - 1).getId()) : null);
    }

    private List<View> views(List<Appeal> rows, Map<Long, Verdict> verdicts) {
        Set<Long> studyIds = rows.stream().map(Appeal::getStudyId).collect(Collectors.toSet());
        Map<Long, String> titles = sessions.titles(studyIds);
        return rows.stream()
                .map(a -> new View(a.getId(), a.getAttendanceEventId(), a.getStudyId(),
                        titles.getOrDefault(a.getStudyId(), ""), verdicts.get(a.getAttendanceEventId()).type(),
                        a.getReason(), a.getHostOpinion(), a.getStatus().name(), a.getDueAt(), a.getCreatedAt(),
                        a.getDecidedAt()))
                .toList();
    }
}
