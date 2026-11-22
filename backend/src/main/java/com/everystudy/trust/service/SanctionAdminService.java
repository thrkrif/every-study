package com.everystudy.trust.service;

import com.everystudy.common.audit.AuditAction;
import com.everystudy.common.audit.AuditLogger;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.common.web.IdCursor;
import com.everystudy.notification.service.NotificationRecorder;
import com.everystudy.notification.service.NotificationRequest;
import com.everystudy.notification.service.NotificationType;
import com.everystudy.trust.domain.SanctionKind;
import com.everystudy.trust.domain.UserSanction;
import com.everystudy.trust.repository.UserSanctionRepository;
import com.everystudy.user.service.UserDirectory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자의 제재 조회·이용 정지 부여·해제 (PRD 4.11 조치 4·4.13C, API 7장, PLAN T-055). 자동 제재와 수동 정지를 같은 곳에서
 * 본다. 모든 조치는 감사 로그에 남는다. 해제하면 그 제재는 바로 효력이 없어져 신청·개설을 다시 할 수 있다.
 */
@Service
public class SanctionAdminService {

    /** 제재 한 건. */
    public record View(long id, long userId, String kind, String source, String reason, Instant startsAt,
            Instant endsAt, Instant revokedAt, Long createdBy, boolean reviewRequired, boolean active) {

        static View of(UserSanction s, Instant now) {
            return new View(s.getId(), s.getUserId(), s.getKind().name(), s.getSource().name(), s.getReason(),
                    s.getStartsAt(), s.getEndsAt(), s.getRevokedAt(), s.getCreatedBy(), s.isReviewRequired(),
                    !s.isRevoked() && s.getEndsAt().isAfter(now));
        }
    }

    /** @param nextCursor 다음 쪽이 없으면 {@code null} */
    public record Page(List<View> items, String nextCursor) {
    }

    private final UserSanctionRepository sanctions;
    private final UserDirectory users;
    private final NotificationRecorder notifications;
    private final AuditLogger audit;
    private final TrustProperties properties;
    private final TimeProvider time;

    SanctionAdminService(UserSanctionRepository sanctions, UserDirectory users, NotificationRecorder notifications,
            AuditLogger audit, TrustProperties properties, TimeProvider time) {
        this.sanctions = sanctions;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
        this.properties = properties;
        this.time = time;
    }

    /**
     * 제재 목록, 최근 것부터 (커서).
     *
     * @param userId 0 보다 크면 그 회원의 제재만
     * @param reviewOnly 운영자 검토 대상(주최자 합계가 검토 단계)만
     */
    @Transactional(readOnly = true)
    public Page list(long userId, boolean activeOnly, boolean reviewOnly, String cursor, int size) {
        Instant now = time.now();
        List<UserSanction> rows = sanctions.findForAdmin(IdCursor.decode(cursor), Math.max(userId, 0), activeOnly,
                reviewOnly, now, Limit.of(size + 1));
        boolean hasNext = rows.size() > size;
        List<UserSanction> page = hasNext ? rows.subList(0, size) : rows;
        return new Page(page.stream().map(s -> View.of(s, now)).toList(),
                hasNext ? IdCursor.encode(page.get(page.size() - 1).getId()) : null);
    }

    /**
     * 이용 정지를 건다 (수동). 신청·개설이 {@code days} 일 동안 막힌다. 본인에게 알린다.
     *
     * @param note 사유. 호출한 쪽이 비어 있지 않고 200자 이하임을 보장한다
     * @throws BusinessException 없는 회원은 {@code NOT_FOUND}
     */
    @Transactional
    public View suspend(long operatorId, long userId, int days, String note) {
        if (!users.findPublic(Set.of(userId)).containsKey(userId)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        Instant now = time.now();
        Instant endsAt = now.plus(Duration.ofDays(days));
        UserSanction sanction = sanctions.saveAndFlush(
                UserSanction.operatorSuspend(userId, note, now, endsAt, operatorId));
        audit.record(operatorId, AuditAction.SANCTION_GRANT, "USER", userId,
                Map.of("kind", SanctionKind.SUSPEND.name(), "source", "OPERATOR", "days", days, "note", note,
                        "endsAt", endsAt.toString(), "sanctionId", sanction.getId()));
        notifications.record(NotificationRequest.allChannels(userId, NotificationType.SANCTION_APPLIED, null,
                "이용이 정지됐어요", days + "일간 이용이 정지돼요. 정지가 끝나면 다시 이용할 수 있어요.",
                "SANCTION_APPLIED:MANUAL:" + sanction.getId(), now,
                now.plus(Duration.ofDays(properties.noticeValidDays()))));
        return View.of(sanction, now);
    }

    /**
     * 제재를 해제한다. 이미 해제됐으면 아무것도 하지 않는다(멱등).
     *
     * @param userId 0 보다 크면 그 회원의 제재인지 확인한다(아니면 {@code NOT_FOUND})
     * @param note 해제 사유. 호출한 쪽이 비어 있지 않고 200자 이하임을 보장한다
     */
    @Transactional
    public void revoke(long operatorId, long userId, long sanctionId, String note) {
        UserSanction sanction = sanctions.findById(sanctionId)
                .filter(s -> userId <= 0 || s.getUserId() == userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (sanction.isRevoked()) {
            return;
        }
        sanction.revoke(time.now());
        audit.record(operatorId, AuditAction.SANCTION_REVOKE, "USER", sanction.getUserId(),
                Map.of("kind", sanction.getKind().name(), "source", sanction.getSource().name(),
                        "sanctionId", sanctionId, "note", note));
    }
}
