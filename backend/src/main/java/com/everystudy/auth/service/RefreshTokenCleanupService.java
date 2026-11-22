package com.everystudy.auth.service;

import com.everystudy.auth.repository.RefreshTokenRepository;
import com.everystudy.common.scheduling.SchedulerProperties;
import com.everystudy.common.time.TimeProvider;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 만료된 리프레시 토큰을 지운다 (DATA-MODEL 10장, PLAN T-030). 만료 시각이 되면 이미 쓸 수 없으므로 지워도 동작은 같다. */
@Service
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository tokens;
    private final SchedulerProperties scheduler;
    private final TimeProvider time;

    RefreshTokenCleanupService(RefreshTokenRepository tokens, SchedulerProperties scheduler, TimeProvider time) {
        this.tokens = tokens;
        this.scheduler = scheduler;
        this.time = time;
    }

    /** @return 지운 개수. 한 번에 {@code batchLimit} 건까지만 지운다 */
    @Transactional
    public int deleteExpired() {
        List<Long> ids = tokens.findIdsByExpiresAtLessThanEqual(time.now(), PageRequest.of(0, scheduler.batchLimit()));
        if (ids.isEmpty()) {
            return 0;
        }
        tokens.deleteAllByIdInBatch(ids);
        return ids.size();
    }
}
