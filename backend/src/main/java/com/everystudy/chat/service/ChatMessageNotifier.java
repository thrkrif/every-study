package com.everystudy.chat.service;

import com.everystudy.chat.repository.ChatReadRepository;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.notification.service.NotificationChannel;
import com.everystudy.notification.service.NotificationRecorder;
import com.everystudy.notification.service.NotificationRequest;
import com.everystudy.notification.service.NotificationType;
import com.everystudy.study.service.StudySessions;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 새 메시지 알림 (PRD 4.12, PLAN D-12·T-050). 받는 사람은 방의 {@code ACTIVE} 참여자 중 <b>보낸 사람 본인, 방을 열어 둔
 * 사람, 보낸 사람을 차단한 사람</b>을 뺀 나머지다. 채널은 푸시뿐이고(이메일 없음), 방·사람마다 {@code notifyWindow}
 * 시간 창에 한 건만 보낸다(키에 시간 창 번호를 넣어 같은 창의 메시지는 한 건으로 묶인다). 수신 설정은 발송 시점에 워커가 본다.
 *
 * <p>구독 레지스트리는 웹소켓 설정(인터셉터 → 메시지 서비스)과 순환하므로 지연 주입한다.
 *
 * <p>메시지 내용은 알림에 싣지 않는다(잠금 화면 노출 방지). 메시지 저장과 같은 트랜잭션에서 기록한다.
 */
@Component
class ChatMessageNotifier {

    private final StudySessions sessions;
    private final ChatReadRepository reads;
    private final ChatSubscriptions subscriptions;
    private final NotificationRecorder notifications;
    private final ChatProperties properties;
    private final TimeProvider time;

    ChatMessageNotifier(StudySessions sessions, ChatReadRepository reads,
            @Lazy ChatSubscriptions subscriptions,
            NotificationRecorder notifications, ChatProperties properties, TimeProvider time) {
        this.sessions = sessions;
        this.reads = reads;
        this.subscriptions = subscriptions;
        this.notifications = notifications;
        this.properties = properties;
        this.time = time;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void notifyNewMessage(long studyId, long senderId) {
        Set<Long> recipients = new HashSet<>(sessions.activeMemberIds(studyId));
        recipients.remove(senderId);
        recipients.removeAll(subscriptions.viewersOf(studyId));
        recipients.removeAll(reads.blockersAmong(senderId, recipients));
        if (recipients.isEmpty()) {
            return;
        }
        Instant now = time.now();
        long window = now.toEpochMilli() / Math.max(1, properties.notifyWindow().toMillis());
        String title = sessions.titles(List.of(studyId)).getOrDefault(studyId, "스터디");
        String shortTitle = title.length() <= 40 ? title : title.substring(0, 39) + "…";
        for (long userId : recipients) {
            notifications.record(new NotificationRequest(userId, NotificationType.CHAT_MESSAGE, studyId,
                    "새 메시지가 도착했어요", "'" + shortTitle + "' 채팅방에 새 메시지가 있어요.",
                    "CHAT_MESSAGE:" + studyId + ":" + userId + ":" + window, now, now.plus(properties.notifyValid()),
                    Set.of(NotificationChannel.PUSH)));
        }
    }
}
