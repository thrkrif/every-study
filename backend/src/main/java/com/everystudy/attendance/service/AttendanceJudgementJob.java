package com.everystudy.attendance.service;

import com.everystudy.common.scheduling.JobRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 체크인 마감이 지난 스터디의 출석 판정을 주기적으로 부른다 (ARCHITECTURE 4.7). 대상이 없으면 아무것도 하지 않는다. */
@Component
class AttendanceJudgementJob {

    private final AttendanceJudgementService service;
    private final JobRunner runner;

    AttendanceJudgementJob(AttendanceJudgementService service, JobRunner runner) {
        this.service = service;
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${everystudy.scheduler.attendance-judgement-delay:PT1M}")
    void run() {
        runner.run("attendance-judgement", service::judgeDueSessions);
    }
}
