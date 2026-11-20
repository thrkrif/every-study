package com.everystudy.study.repository;

import com.everystudy.study.domain.Study;
import java.time.Duration;
import java.util.Optional;

/** {@code study} 행을 잠그고 읽는다 (ADR-0004 결정 1). 락 대기 시간이 설정값이라 {@code @Lock} 대신 직접 구현한다. */
public interface StudyLockRepository {

    /**
     * {@code SELECT … FOR UPDATE} 로 읽는다. 같은 스터디의 멤버 구성 변경은 이 락으로 직렬화된다.
     *
     * @param lockTimeout 락 대기 시간. 넘으면 {@link org.springframework.dao.PessimisticLockingFailureException}
     */
    Optional<Study> findByIdForUpdate(long id, Duration lockTimeout);
}
