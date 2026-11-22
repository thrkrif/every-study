package com.everystudy.study.service;

import com.everystudy.common.scheduling.SchedulerProperties;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.domain.Study;
import com.everystudy.study.domain.StudyStatus;
import com.everystudy.study.event.StudyEnded;
import com.everystudy.study.repository.StudyRepository;
import com.everystudy.study.repository.StudyWaitingRepository;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 스터디 종료 처리 (PRD 4.14, ARCHITECTURE 4.7, PLAN T-030). 세션 날짜(KST) 다음날 00:00 이 지난 {@code ACTIVE} 스터디를
 * {@code ENDED} 로 바꾸고, 남은 대기 행을 지우고(PLAN X-16), {@link StudyEnded} 를 발행한다.
 *
 * <p>스터디마다 별도 트랜잭션에서 {@code study} 행 락을 잡고 조건을 다시 확인한다(ADR-0004 결정 1). 그래서 재실행·동시 실행에
 * 안전하고, 한 스터디의 실패가 다른 스터디를 막지 않는다.
 */
@Service
public class StudyEndService {

    private static final Logger log = LoggerFactory.getLogger(StudyEndService.class);

    private final StudyRepository studies;
    private final StudyWaitingRepository waiting;
    private final StudyLockProperties lockProperties;
    private final SchedulerProperties scheduler;
    private final TimeProvider time;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    StudyEndService(StudyRepository studies, StudyWaitingRepository waiting, StudyLockProperties lockProperties,
            SchedulerProperties scheduler, TimeProvider time, ApplicationEventPublisher events,
            TransactionTemplate tx) {
        this.studies = studies;
        this.waiting = waiting;
        this.lockProperties = lockProperties;
        this.scheduler = scheduler;
        this.time = time;
        this.events = events;
        this.tx = tx;
    }

    /** @return 이번에 종료한 스터디 수. 한 번에 {@code batchLimit} 건까지만 처리한다 */
    public int endDueStudies() {
        LocalDate today = time.todayInKorea();
        List<Long> due = studies.findIdsByStatusAndSessionDateBefore(StudyStatus.ACTIVE, today,
                PageRequest.of(0, scheduler.batchLimit()));
        int ended = 0;
        for (long id : due) {
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> endOne(id, today)))) {
                    ended++;
                }
            } catch (RuntimeException e) {
                log.error("스터디 {} 종료 처리 실패. 다음 스터디를 계속 처리한다", id, e);
            }
        }
        return ended;
    }

    private boolean endOne(long id, LocalDate today) {
        Study study = studies.findByIdForUpdate(id, lockProperties.timeout()).orElse(null);
        // 락을 기다리는 사이 다른 처리(파투 등)가 끝냈을 수 있어 조건을 다시 확인한다.
        if (study == null || study.getStatus() != StudyStatus.ACTIVE || !study.getSessionDate().isBefore(today)) {
            return false;
        }
        study.end(time.now());
        waiting.deleteByStudyId(id);
        events.publishEvent(new StudyEnded(id));
        return true;
    }
}
