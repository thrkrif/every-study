package com.everystudy.attendance.service;

import com.everystudy.attendance.domain.Appeal;
import com.everystudy.attendance.repository.AppealRepository;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.service.StudySessions;
import com.everystudy.trust.service.AttendanceLedger;
import com.everystudy.trust.service.AttendanceLedger.Verdict;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 지난 출석 기록 (API 3장, PLAN T-034). 스터디마다 현재 판정 하나씩, 최근 것부터 커서로 준다. 이의 가능 여부는 이의 대상
 * 판정이고, 아직 신청하지 않았고, 기한 안일 때다.
 */
@Service
public class MyAttendanceService {

    /**
     * 출석 기록 한 건.
     *
     * @param eventId   판정 이벤트 id. 이의 신청 경로의 {@code eventId} 다
     * @param type      현재 판정 ({@code ATTENDED}, {@code LATE}, {@code NO_SHOW_CANDIDATE} 등)
     * @param appealable 지금 이의를 낼 수 있는가
     * @param appealDueAt 이의 대상 판정이면 기한, 아니면 {@code null}
     * @param appealId  이미 낸 이의가 있으면 그 id
     */
    public record Entry(long eventId, long studyId, String studyTitle, String type, Instant occurredAt,
            boolean appealable, Instant appealDueAt, Long appealId) {
    }

    public record Page(List<Entry> items, String nextCursor) {
    }

    private final AttendanceLedger ledger;
    private final AppealRepository appeals;
    private final AppealService appealService;
    private final StudySessions sessions;
    private final TimeProvider time;

    MyAttendanceService(AttendanceLedger ledger, AppealRepository appeals, AppealService appealService,
            StudySessions sessions, TimeProvider time) {
        this.ledger = ledger;
        this.appeals = appeals;
        this.appealService = appealService;
        this.sessions = sessions;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public Page mine(long userId, String cursor, int size) {
        List<Verdict> rows = ledger.currentVerdicts(userId, IdCursor.decode(cursor), size + 1);
        boolean hasNext = rows.size() > size;
        List<Verdict> page = hasNext ? rows.subList(0, size) : rows;
        Map<Long, Appeal> appealByEvent = appeals
                .findByAttendanceEventIdIn(page.stream().map(Verdict::id).toList()).stream()
                .collect(Collectors.toMap(Appeal::getAttendanceEventId, a -> a));
        Set<Long> studyIds = page.stream().map(Verdict::studyId).collect(Collectors.toSet());
        Map<Long, String> titles = sessions.titles(studyIds);
        Instant now = time.now();
        List<Entry> items = page.stream().map(v -> {
            Appeal appeal = appealByEvent.get(v.id());
            Instant dueAt = v.appealable() ? appealService.dueAt(v.occurredAt()) : null;
            boolean appealable = v.appealable() && appeal == null && !now.isAfter(dueAt);
            return new Entry(v.id(), v.studyId(), titles.getOrDefault(v.studyId(), ""), v.type(), v.occurredAt(),
                    appealable, dueAt, appeal == null ? null : appeal.getId());
        }).toList();
        return new Page(items, hasNext ? IdCursor.encode(page.get(page.size() - 1).id()) : null);
    }
}
