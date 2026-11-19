package com.everystudy.trust.service;

import com.everystudy.trust.domain.SanctionKind;
import com.everystudy.trust.repository.UserSanctionRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 모듈이 제재 여부를 확인하는 곳 (PRD 4.8, DATA-MODEL 6장). */
@Service
public class SanctionService {

    /** 참여 신청을 막는 제재: 참여 신청 제한과 이용 정지. 개설 제한은 신청을 막지 않는다. */
    private static final Set<SanctionKind> BLOCKS_JOIN = EnumSet.of(SanctionKind.JOIN_RESTRICT, SanctionKind.SUSPEND);

    private final UserSanctionRepository sanctions;

    SanctionService(UserSanctionRepository sanctions) {
        this.sanctions = sanctions;
    }

    /**
     * 지금 참여 신청이 막혀 있으면 풀리는 시각. 막는 제재가 여럿이면 가장 늦게 끝나는 것이다.
     *
     * @return 막혀 있지 않으면 빈 값
     */
    @Transactional(readOnly = true)
    public Optional<Instant> joinRestrictedUntil(long userId, Instant now) {
        return sanctions.findLatestActiveEnd(userId, BLOCKS_JOIN, now);
    }
}
