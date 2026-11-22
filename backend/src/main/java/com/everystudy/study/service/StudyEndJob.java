package com.everystudy.study.service;

import com.everystudy.common.scheduling.JobRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 스터디 종료 처리를 주기적으로 부른다 (ARCHITECTURE 4.7). 대상이 없으면 아무것도 하지 않는다. */
@Component
class StudyEndJob {

    private final StudyEndService service;
    private final JobRunner runner;

    StudyEndJob(StudyEndService service, JobRunner runner) {
        this.service = service;
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${everystudy.scheduler.study-end-delay:PT1M}")
    void run() {
        runner.run("study-end", service::endDueStudies);
    }
}
