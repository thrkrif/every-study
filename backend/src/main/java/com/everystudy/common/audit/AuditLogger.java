package com.everystudy.common.audit;

import com.everystudy.common.time.TimeProvider;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그를 기록하는 공개 서비스 (ARCHITECTURE 3장·4.5, PLAN T-019). 여러 모듈이 부르므로 {@code common} 에 둔다 (X-11).
 *
 * <p>변경과 같은 트랜잭션 안에서 기록한다. 그래서 점수·제재 변경이 롤백되면 기록도 남지 않는다. 트랜잭션 밖에서
 * 부르면 실패한다({@link Propagation#MANDATORY}).
 */
@Service
public class AuditLogger {

    private final AuditLogRepository logs;
    private final TimeProvider time;

    AuditLogger(AuditLogRepository logs, TimeProvider time) {
        this.logs = logs;
        this.time = time;
    }

    /**
     * 감사 로그 한 줄을 기록한다.
     *
     * @param actorId 행위자 사용자 id. 시스템이 한 행위(자동 감점·판정 등)면 {@code null}
     * @param targetType 대상 종류(예: {@code STUDY}, {@code USER})
     * @param detail 변경 전후 값. 없으면 {@code null}. 개인정보·토큰은 넣지 않는다
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorId, AuditAction action, String targetType, long targetId,
            Map<String, Object> detail) {
        logs.save(AuditLog.of(actorId, action, targetType, targetId, detail, time.now()));
    }
}
