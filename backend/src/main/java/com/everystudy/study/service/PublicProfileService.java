package com.everystudy.study.service;

import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.user.service.PublicProfile;
import com.everystudy.user.service.UserDirectory;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 프로필 (PRD 4.1, API 3장 {@code GET /users/{id}}). 회원 정보({@code user})에 출석 지표({@code trust})와 주최
 * 지표(주최 횟수는 {@code study})를 더한다. {@code study} 가 {@code user}·{@code trust} 를 이미 참조하므로, 세 모듈을
 * 모두 아는 이곳에 둔다.
 */
@Service
public class PublicProfileService {

    private final UserDirectory users;
    private final PersonMetrics metrics;

    PublicProfileService(UserDirectory users, PersonMetrics metrics) {
        this.users = users;
        this.metrics = metrics;
    }

    /**
     * 공개 프로필 응답.
     *
     * @param nickname     탈퇴한 회원이면 {@code null}. 화면은 "탈퇴한 사용자"로 표시한다
     * @param jobCode      탈퇴한 회원이면 {@code null}
     * @param districtCode 탈퇴한 회원이면 {@code null}
     */
    public record View(long id, String nickname, String jobCode, String districtCode, Instant joinedAt,
            StudyViews.Trust attendance, StudyViews.HostStats hostStats) {
    }

    /**
     * @throws BusinessException 없는 회원이거나 온보딩을 마치지 않은 회원이면 {@code NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public View find(long userId) {
        PublicProfile profile = users.findProfile(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return new View(profile.userId(), profile.nickname(), profile.jobCode(), profile.districtCode(),
                profile.joinedAt(), metrics.trustOf(List.of(userId)).get(userId),
                metrics.hostStatsOf(List.of(userId)).get(userId));
    }
}
