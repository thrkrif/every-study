package com.everystudy.attendance.service;

import com.everystudy.common.scheduling.JobRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 이의 기간이 지난 노쇼 후보를 주기적으로 확정한다 (ARCHITECTURE 4.7). 대상이 없으면 아무것도 하지 않는다. */
@Component
class NoShowConfirmationJob {

    private final NoShowConfirmationService service;
    private final JobRunner runner;

    NoShowConfirmationJob(NoShowConfirmationService service, JobRunner runner) {
        this.service = service;
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${everystudy.scheduler.no-show-confirmation-delay:PT10M}")
    void run() {
        runner.run("no-show-confirmation", service::confirmDueNoShows);
    }
}
