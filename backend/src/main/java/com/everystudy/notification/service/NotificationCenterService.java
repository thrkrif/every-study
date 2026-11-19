package com.everystudy.notification.service;

import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.notification.domain.Notification;
import com.everystudy.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 센터 (API 3장, 화면 12). 보이는 시각({@code visibleAt})이 지난 내 알림만 다룬다 (PLAN X-10).
 * 아직 보이지 않는 예약 알림은 없는 것과 같다(읽음 처리도 {@code NOT_FOUND}).
 */
@Service
public class NotificationCenterService {

    private final NotificationRepository notifications;
    private final TimeProvider time;

    NotificationCenterService(NotificationRepository notifications, TimeProvider time) {
        this.notifications = notifications;
        this.time = time;
    }

    public record Item(long id, NotificationType type, Long studyId, String title, String body, boolean read,
            Instant notifiedAt) {
    }

    public record Page(List<Item> items, String nextCursor) {
    }

    @Transactional(readOnly = true)
    public Page list(long userId, String cursor, int size) {
        Instant now = time.now();
        NotificationCursor.Position after = NotificationCursor.decode(cursor);
        Limit limit = Limit.of(size + 1);
        List<Notification> rows = after == null
                ? notifications.findVisible(userId, now, limit)
                : notifications.findVisibleAfter(userId, now, after.visibleAt(), after.id(), limit);

        boolean hasNext = rows.size() > size;
        List<Notification> page = hasNext ? rows.subList(0, size) : rows;
        String nextCursor = null;
        if (hasNext) {
            Notification last = page.getLast();
            nextCursor = NotificationCursor.encode(last.getVisibleAt(), last.getId());
        }
        return new Page(page.stream().map(NotificationCenterService::item).toList(), nextCursor);
    }

    /**
     * 한 건을 읽음으로. 이미 읽었어도 성공이다(멱등).
     *
     * @throws BusinessException 없거나, 내 알림이 아니거나, 아직 보이지 않으면 {@code NOT_FOUND}
     */
    @Transactional
    public void markRead(long userId, long notificationId) {
        Instant now = time.now();
        Notification notification = notifications.findById(notificationId)
                .filter(n -> n.getUserId() == userId && n.isVisibleAt(now))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        notification.markRead(now);
    }

    /** 보이는 알림을 모두 읽음으로. */
    @Transactional
    public void markAllRead(long userId) {
        notifications.markAllVisibleRead(userId, time.now());
    }

    private static Item item(Notification n) {
        return new Item(n.getId(), NotificationType.valueOf(n.getType()), n.getStudyId(), n.getTitle(), n.getBody(),
                n.getReadAt() != null, n.getVisibleAt());
    }
}
