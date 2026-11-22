package com.everystudy.common.scheduling;

import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 스케줄 작업을 실행하는 한 곳. 작업 하나가 예외를 던져도 다른 작업과 다음 실행에 영향이 없도록 여기서 삼키고 기록한다
 * (ARCHITECTURE 4.7 "작업별 예외 격리"). 작업 본문은 처리한 건수를 돌려준다.
 */
@Component
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    /** @return 처리한 건수. 작업이 실패하면 0 */
    public int run(String name, IntSupplier job) {
        try {
            int processed = job.getAsInt();
            if (processed > 0) {
                log.info("스케줄 작업 {}: {}건 처리", name, processed);
            }
            return processed;
        } catch (RuntimeException e) {
            log.error("스케줄 작업 {} 실패", name, e);
            return 0;
        }
    }
}
