package com.everystudy.notification.service;

import com.everystudy.common.scheduling.JobRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 알림 발송 워커를 주기적으로 부른다 (ARCHITECTURE 4.7). 보낼 건이 없으면 아무것도 하지 않는다. */
@Component
class NotificationDispatchJob {

    private final NotificationDispatchService service;
    private final JobRunner runner;

    NotificationDispatchJob(NotificationDispatchService service, JobRunner runner) {
        this.service = service;
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${everystudy.scheduler.notification-dispatch-delay:PT10S}")
    void run() {
        runner.run("notification-dispatch", service::dispatchDue);
    }
}
