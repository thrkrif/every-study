package com.everystudy.auth.service;

import com.everystudy.common.scheduling.JobRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 만료된 리프레시 토큰 정리를 주기적으로 부른다 (ARCHITECTURE 4.7). */
@Component
class RefreshTokenCleanupJob {

    private final RefreshTokenCleanupService service;
    private final JobRunner runner;

    RefreshTokenCleanupJob(RefreshTokenCleanupService service, JobRunner runner) {
        this.service = service;
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${everystudy.scheduler.token-cleanup-delay:PT1H}")
    void run() {
        runner.run("refresh-token-cleanup", service::deleteExpired);
    }
}
