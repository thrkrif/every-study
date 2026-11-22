package com.everystudy.common.audit;

import com.everystudy.common.error.FieldViolation;
import com.everystudy.common.error.InvalidInputException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영자의 감사 로그 조회 (PRD 4.11·8장, API 7장). 최근 것부터 커서로 넘긴다. */
@Service
public class AuditLogQueryService {

    /** 감사 로그 한 줄. */
    public record Item(long id, Long actorId, String action, String targetType, long targetId,
            Map<String, Object> detail, Instant createdAt) {

        static Item of(AuditLog log) {
            return new Item(log.getId(), log.getActorId(), log.getAction(), log.getTargetType(), log.getTargetId(),
                    log.getDetail(), log.getCreatedAt());
        }
    }

    /** @param nextCursor 다음 쪽이 없으면 {@code null} */
    public record Page(List<Item> items, String nextCursor) {
    }

    private final AuditLogRepository logs;

    AuditLogQueryService(AuditLogRepository logs) {
        this.logs = logs;
    }

    /**
     * @param action 알려진 {@link AuditAction} 이름이면 그 행위만. 모르는 이름은 {@code VALIDATION_FAILED}
     * @param targetType 대상 종류(예: {@code STUDY}, {@code USER}). 비우면 전체
     */
    @Transactional(readOnly = true)
    public Page list(String cursor, int size, String action, String targetType) {
        if (action != null && !action.isBlank() && Arrays.stream(AuditAction.values())
                .noneMatch(known -> known.name().equals(action))) {
            throw new InvalidInputException(List.of(new FieldViolation("action", "알 수 없는 행위입니다")));
        }
        List<AuditLog> rows = logs.findPage(decode(cursor), blankToNull(action), blankToNull(targetType),
                Limit.of(size + 1));
        boolean more = rows.size() > size;
        List<AuditLog> page = more ? rows.subList(0, size) : rows;
        String next = more ? encode(page.get(page.size() - 1).getId()) : null;
        return new Page(page.stream().map(Item::of).toList(), next);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String encode(long lastId) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Long.toString(lastId).getBytes(StandardCharsets.UTF_8));
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
        } catch (IllegalArgumentException ex) {
            // 아래에서 같은 오류로 응답한다.
        }
        throw new InvalidInputException(List.of(new FieldViolation("cursor", "올바르지 않은 커서입니다")));
    }
}
