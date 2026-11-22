package com.everystudy.chat.service;

import com.everystudy.chat.domain.ChatMessage;
import com.everystudy.chat.domain.ChatMessageKind;
import com.everystudy.chat.domain.Report;
import com.everystudy.chat.domain.ReportReason;
import com.everystudy.chat.domain.ReportStatus;
import com.everystudy.chat.domain.ReportTargetType;
import com.everystudy.chat.repository.ChatMessageRepository;
import com.everystudy.chat.repository.ChatRoomRepository;
import com.everystudy.chat.repository.ReportRepository;
import com.everystudy.common.audit.AuditAction;
import com.everystudy.common.audit.AuditLogger;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.error.FieldViolation;
import com.everystudy.common.error.InvalidInputException;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.service.StudySessions;
import com.everystudy.user.service.PublicUser;
import com.everystudy.user.service.UserDirectory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 접수와 운영자 신고 큐 (PRD 4.11, API 7장, PLAN T-053·D-4). 스터디·사용자·채팅 메시지 신고가 한 큐에 쌓이고, 운영자는
 * 사유(note)를 적어 반려하거나 처리 완료로 표시한다. 처리 완료는 표시일 뿐이고 실제 조치(강제 종료·메시지 삭제·이용 정지)는
 * 각각의 운영자 API 로 따로 한다. 모든 처리는 감사 로그에 남는다.
 */
@Service
public class ReportService {

    /** 운영자 큐의 신고 한 건. {@code targetSummary} 는 스터디 제목·닉네임·메시지 내용 앞부분이고 대상이 없으면 {@code null}. */
    public record AdminView(long id, long reporterId, String reporterNickname, String targetType, long targetId,
            String targetSummary, String reason, String detail, String status, Long handledBy, String handleNote,
            Instant createdAt, Instant handledAt) {
    }

    /** @param nextCursor 다음 쪽이 없으면 {@code null} */
    public record Page(List<AdminView> items, String nextCursor) {
    }

    private static final int SUMMARY_MAX = 100;

    private final ReportRepository reports;
    private final ChatMessageRepository messages;
    private final ChatRoomRepository rooms;
    private final StudySessions sessions;
    private final UserDirectory users;
    private final AuditLogger audit;
    private final TimeProvider time;

    ReportService(ReportRepository reports, ChatMessageRepository messages, ChatRoomRepository rooms,
            StudySessions sessions, UserDirectory users, AuditLogger audit, TimeProvider time) {
        this.reports = reports;
        this.messages = messages;
        this.rooms = rooms;
        this.sessions = sessions;
        this.users = users;
        this.audit = audit;
        this.time = time;
    }

    /**
     * 신고를 접수한다. 검사 순서: 입력(사유 {@code VALIDATION_FAILED}, {@code OTHER} 는 상세 필수, 자기 자신 신고 불가) →
     * 대상 없음 {@code NOT_FOUND}(메시지는 시스템 메시지·지워진 메시지도 없는 것으로 본다) → 메시지는 그 방의 참여자만
     * {@code FORBIDDEN} → 같은 대상에 열린 신고가 이미 있음 {@code ALREADY_REPORTED}.
     *
     * @return 새 신고 ID
     */
    @Transactional
    public long file(long reporterId, ReportTargetType targetType, long targetId, String reasonCode, String detail) {
        ReportReason reason = parseReason(reasonCode);
        String text = detail == null || detail.isBlank() ? null : detail.strip();
        if (reason == ReportReason.OTHER && text == null) {
            throw new InvalidInputException(List.of(new FieldViolation("detail", "기타 사유는 상세 내용이 필요합니다")));
        }
        if (text != null && text.length() > 500) {
            throw new InvalidInputException(List.of(new FieldViolation("detail", "상세 내용은 500자 이하여야 합니다")));
        }
        requireReportable(reporterId, targetType, targetId);
        if (reports.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(reporterId, targetType, targetId,
                ReportStatus.OPEN)) {
            throw new BusinessException(ChatErrorCode.ALREADY_REPORTED);
        }
        return reports.save(Report.open(reporterId, targetType, targetId, reason, text, time.now())).getId();
    }

    /**
     * 운영자 신고 큐, 최근 신고부터 (커서).
     *
     * @param status {@code null} 이면 모든 상태
     */
    @Transactional(readOnly = true)
    public Page list(ReportStatus status, String cursor, int size) {
        long before = decode(cursor);
        PageRequest limit = PageRequest.of(0, size + 1);
        List<Report> rows = status == null ? reports.findByIdLessThanOrderByIdDesc(before, limit)
                : reports.findByStatusAndIdLessThanOrderByIdDesc(status, before, limit);
        boolean hasNext = rows.size() > size;
        List<Report> page = hasNext ? rows.subList(0, size) : rows;
        return new Page(views(page), hasNext ? encode(page.get(page.size() - 1).getId()) : null);
    }

    /**
     * 열린 신고를 반려({@code resolved=false}) 또는 처리 완료한다. 신고 행을 잠그고 한 번만 처리한다.
     *
     * @param note 처리 사유. 호출한 쪽이 비어 있지 않고 300자 이하임을 보장한다
     * @throws BusinessException 없으면 {@code NOT_FOUND}, 이미 처리됐으면 {@code REPORT_ALREADY_HANDLED}
     */
    @Transactional
    public AdminView handle(long operatorId, long reportId, boolean resolved, String note) {
        Report report = reports.findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!report.isOpen()) {
            throw new BusinessException(ChatErrorCode.REPORT_ALREADY_HANDLED);
        }
        report.handle(resolved, operatorId, note, time.now());
        audit.record(operatorId, resolved ? AuditAction.REPORT_RESOLVE : AuditAction.REPORT_DISMISS, "REPORT",
                reportId, Map.of("targetType", report.getTargetType().name(), "targetId", report.getTargetId(),
                        "reason", report.getReason().name(), "note", note));
        return views(List.of(report)).get(0);
    }

    private void requireReportable(long reporterId, ReportTargetType type, long targetId) {
        switch (type) {
            case STUDY -> {
                if (sessions.titles(Set.of(targetId)).isEmpty()) {
                    throw new BusinessException(CommonErrorCode.NOT_FOUND);
                }
            }
            case USER -> {
                if (targetId == reporterId) {
                    throw new InvalidInputException(List.of(new FieldViolation("targetId", "자기 자신은 신고할 수 없습니다")));
                }
                if (!users.findPublic(Set.of(targetId)).containsKey(targetId)) {
                    throw new BusinessException(CommonErrorCode.NOT_FOUND);
                }
            }
            case MESSAGE -> requireReportableMessage(reporterId, targetId);
        }
    }

    private void requireReportableMessage(long reporterId, long messageId) {
        ChatMessage message = messages.findById(messageId)
                .filter(m -> m.getKind() == ChatMessageKind.TEXT && !m.isDeleted())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (message.getSenderId() != null && message.getSenderId() == reporterId) {
            throw new InvalidInputException(List.of(new FieldViolation("targetId", "내 메시지는 신고할 수 없습니다")));
        }
        long studyId = rooms.findById(message.getRoomId()).orElseThrow().getStudyId();
        boolean member = sessions.read(studyId, reporterId).map(StudySessions.Session::activeMember).orElse(false);
        if (!member) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    private List<AdminView> views(List<Report> rows) {
        Set<Long> userIds = new HashSet<>();
        Set<Long> studyIds = new HashSet<>();
        Map<Long, ChatMessage> messageById = Map.of();
        Set<Long> messageIds = new HashSet<>();
        for (Report r : rows) {
            userIds.add(r.getReporterId());
            switch (r.getTargetType()) {
                case USER -> userIds.add(r.getTargetId());
                case STUDY -> studyIds.add(r.getTargetId());
                case MESSAGE -> messageIds.add(r.getTargetId());
            }
        }
        if (!messageIds.isEmpty()) {
            messageById = messages.findAllById(messageIds).stream()
                    .collect(Collectors.toMap(ChatMessage::getId, m -> m));
        }
        Map<Long, PublicUser> publicUsers = users.findPublic(userIds);
        Map<Long, String> titles = studyIds.isEmpty() ? Map.of() : sessions.titles(studyIds);
        Map<Long, ChatMessage> finalMessages = messageById;
        return rows.stream().map(r -> {
            PublicUser reporter = publicUsers.get(r.getReporterId());
            String summary = switch (r.getTargetType()) {
                case STUDY -> titles.get(r.getTargetId());
                case USER -> publicUsers.get(r.getTargetId()) == null ? null
                        : publicUsers.get(r.getTargetId()).nickname();
                case MESSAGE -> {
                    ChatMessage m = finalMessages.get(r.getTargetId());
                    yield m == null || m.isDeleted() ? null : abbreviate(m.getContent());
                }
            };
            return new AdminView(r.getId(), r.getReporterId(), reporter == null ? null : reporter.nickname(),
                    r.getTargetType().name(), r.getTargetId(), summary, r.getReason().name(), r.getDetail(),
                    r.getStatus().name(), r.getHandledBy(), r.getHandleNote(), r.getCreatedAt(), r.getHandledAt());
        }).toList();
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= SUMMARY_MAX ? text : text.substring(0, SUMMARY_MAX - 1) + "…";
    }

    private static ReportReason parseReason(String code) {
        try {
            return ReportReason.valueOf(code == null ? "" : code);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException(List.of(new FieldViolation("reason", "올바르지 않은 신고 사유입니다")));
        }
    }

    private static String encode(long lastId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Long.toString(lastId).getBytes(StandardCharsets.UTF_8));
    }

    private static long decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Long.MAX_VALUE;
        }
        try {
            long id = Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
            if (id > 0) {
                return id;
            }
        } catch (IllegalArgumentException e) {
            // 아래에서 같은 오류로 응답한다.
        }
        throw new InvalidInputException(List.of(new FieldViolation("cursor", "올바르지 않은 커서입니다")));
    }
}
