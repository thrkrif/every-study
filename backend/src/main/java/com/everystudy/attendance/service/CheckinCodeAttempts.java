package com.everystudy.attendance.service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 코드 입력 실패 횟수 (PLAN D-7·X-28). 서버 1대 가정으로 메모리에서 센다. 서버를 늘리면 DB·외부 저장소로 옮긴다
 * (DATA-MODEL 5장, ADR-0005). 트랜잭션이 롤백돼도 횟수가 남아야 하므로 DB 가 아니라 메모리다.
 *
 * <p>(스터디, 사용자) 단위로 센다. 기록은 코드가 만료되는 시각(체크인 마감)이 지나면 쓸모가 없어서 새 실패를 기록할 때
 * 함께 걷어 낸다.
 */
@Component
class CheckinCodeAttempts {

    private record Key(long studyId, long userId) {
    }

    private static final class Counter {
        private int failures;
        private Instant discardAfter;
    }

    private final Map<Key, Counter> counters = new ConcurrentHashMap<>();

    /** 이미 {@code maxFailures} 번 틀려서 더는 입력을 받지 않는가. */
    boolean isLocked(long studyId, long userId, int maxFailures) {
        Counter counter = counters.get(new Key(studyId, userId));
        return counter != null && counter.failures >= maxFailures;
    }

    /** 실패를 한 번 더한다. {@code discardAfter} 가 지나면 이 기록은 버려도 된다. */
    void recordFailure(long studyId, long userId, Instant now, Instant discardAfter) {
        counters.entrySet().removeIf(entry -> now.isAfter(entry.getValue().discardAfter));
        counters.compute(new Key(studyId, userId), (key, counter) -> {
            Counter next = counter == null ? new Counter() : counter;
            next.failures++;
            next.discardAfter = discardAfter;
            return next;
        });
    }

    /** 모든 기록을 지운다. 테스트가 스터디 ID 가 재사용되는 DB 초기화 사이에 쓴다. */
    void clear() {
        counters.clear();
    }
}
