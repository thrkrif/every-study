package com.everystudy.chat.service;

import com.everystudy.common.scheduling.JobRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 보관 기간이 지난 채팅 메시지 삭제를 주기적으로 부른다 (ARCHITECTURE 4.7). 대상이 없으면 아무것도 하지 않는다. */
@Component
class ChatPurgeJob {

    private final ChatPurgeService service;
    private final JobRunner runner;

    ChatPurgeJob(ChatPurgeService service, JobRunner runner) {
        this.service = service;
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${everystudy.scheduler.chat-purge-delay:PT10M}")
    void run() {
        runner.run("chat-purge", service::purgeDueRooms);
    }
}
