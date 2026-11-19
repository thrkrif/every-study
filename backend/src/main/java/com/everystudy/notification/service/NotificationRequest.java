package com.everystudy.notification.service;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 알림 한 건을 기록해 달라는 요청 ({@link NotificationRecorder}).
 *
 * @param userId      받는 회원
 * @param studyId     연결 대상 스터디. 없으면 {@code null}
 * @param title       중복 발송을 견디는 문장으로 쓴다 (ADR-0005). 최대 {@value #TITLE_MAX}자
 * @param body        최대 {@value #BODY_MAX}자
 * @param dedupeKey   같은 알림을 두 번 만들지 않게 하는 키. 예: {@code CHECKIN_OPEN:{studyId}:{userId}}.
 *                    채널별 아웃박스 키는 여기에 {@code :<채널>} 을 붙인다. 최대 {@value #DEDUPE_KEY_MAX}자
 * @param scheduledAt 발송 예정 시각이자 알림 센터에 보이기 시작하는 시각 (PLAN X-10). 바로 보낼 알림은 현재 시각
 * @param expiresAt   이 시각이 지나면 보내지 않는다 (ARCHITECTURE 4.6). {@code scheduledAt} 이후여야 한다
 * @param channels    보낼 채널. 수신 설정은 발송 시점에 확인한다 (ADR-0005)
 */
public record NotificationRequest(long userId, NotificationType type, Long studyId, String title, String body,
        String dedupeKey, Instant scheduledAt, Instant expiresAt, Set<NotificationChannel> channels) {

    public static final int TITLE_MAX = 100;
    public static final int BODY_MAX = 300;
    /** 아웃박스 키 열 길이(100)에서 가장 긴 채널 접미사({@code :EMAIL})를 뺀 값. */
    public static final int DEDUPE_KEY_MAX = 94;

    public NotificationRequest {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scheduledAt, "scheduledAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        requireText(title, TITLE_MAX, "title");
        requireText(body, BODY_MAX, "body");
        requireText(dedupeKey, DEDUPE_KEY_MAX, "dedupeKey");
        if (expiresAt.isBefore(scheduledAt)) {
            throw new IllegalArgumentException("expiresAt 은 scheduledAt 이후여야 합니다");
        }
        if (channels == null || channels.isEmpty()) {
            throw new IllegalArgumentException("channels 가 비어 있습니다");
        }
        channels = Set.copyOf(channels);
    }

    /** 모든 채널로 보내는 요청. */
    public static NotificationRequest allChannels(long userId, NotificationType type, Long studyId, String title,
            String body, String dedupeKey, Instant scheduledAt, Instant expiresAt) {
        return new NotificationRequest(userId, type, studyId, title, body, dedupeKey, scheduledAt, expiresAt,
                Set.of(NotificationChannel.values()));
    }

    private static void requireText(String value, int max, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 이 비어 있습니다");
        }
        if (value.length() > max) {
            throw new IllegalArgumentException(name + " 은 " + max + "자 이하여야 합니다");
        }
    }
}
